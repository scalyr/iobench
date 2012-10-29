#!/usr/local/bin/scsh \
-lm my-util-def.scm -lm batch.scm -dm -m remote-run -e main -s
!#
;;;;prerequisites:
;;;;1. scsh
;;;;2. screen
;;;;3. ec2-api-tools
;;;;   http://aws.amazon.com/developertools/351
;;;;   export shell variables EC2_HOME,EC2_PRIVATE_KEY,EC2_CERT,EC2_URL
;;;;4. iobench.jar in this directory

;;;;to run:
;;;;  edit config.scm
;;;;  ./remote-run.scm -h

;;;;files:
;;;;  "instances" contains ids of amazon instances that are left running
;;;;  "instance_id" where you may specify a launched but uninitialized instance
;;;;  to initialize
;;;;  *.out *.err log files

(define-structure remote-run (export)
	(open scheme-with-scsh
		  big-scheme
		  threads
          thread-fluids
		  define-record-types
		  srfi-1
		  srfi-13
		  srfi-27
		  my-util-def
          batch)
	(files "config.scm")
	(begin

(define *RESOURCEDIR* (cwd))

;;;;utilities
(define (poll-random fn attempts interval-ms fractional-range)
	;;interval-ms=10, fractional-range=0.2 => something between (8,12)
	(define (random-interval)
		(* (+ 1
			  (- fractional-range)
			  (* 2 fractional-range (random-real)))
		   interval-ms))
	(define (poll2 attempts2)
		(or (begin (format #t ".")
				   (fn))
			(if (equal? attempts2 1)
				#f
				(begin (sleep (random-interval))
					   (poll2 (- attempts2 1))))))
	(poll2 attempts)
	(format #t "\n"))
;;; attempts > 0
(define (poll fn attempts interval-ms)
	(poll-random fn attempts interval-ms 0))
(define (poll-forever fn interval-ms)
	(poll fn 1000000 interval-ms))
(define (ssh/strings opts command)
  (run/strings (ssh -q ,@opts ,command)))
(define (ssh opts command)
	(run (ssh -q ,@opts ,command) stdports))
;;;ssh -t to allocate tty because /etc/sudoers disallows sudo without one.
;;;screen because otherwise -t seems to not work when this entire script is
;;;in the background. maybe -t -t would have forced it to work.
;;;StrictHostKeyChecking must be "no" with this.
(define (screen-ssh-sudo connection command)
	(run (screen -D -m
				 ssh -q -t ,@connection
					 ,(format #f "sudo sh -c \"~a\"" command))
         stdports))
(define (fix-sudo-conf connection)
	(screen-ssh-sudo connection
					 "sed -e /requiretty/d -e /visiblepw/d \\
						/etc/sudoers >/etc/sudoers.new &&
					  mv /etc/sudoers.new /etc/sudoers &&
					  chmod 440 /etc/sudoers"))
(define (ssh-sudo connection command)
	(run (ssh -q ,@connection ,(format #f "sudo sh -c \"~a\"" command))
         stdports))
;;;connection is a list, whose last element must be the hostname,
;;;e.g. '("-oIdentityFile=key.pem" "user@localhost")
(define (scp-to connection dir files)
	(run (scp -q
			  ,@(drop-right connection 1)
			  ,@files
			  ,(format #f "~a:~a/" (car (take-right connection 1)) dir))
         stdports))
(define (scp-from connection dir file)
	(run (scp -q
			  ,@(drop-right connection 1)
			  ,(format #f "~a:~a/~a" (car (take-right connection 1)) dir file)
			  ".")
         stdports))
(define (ensure-ssh-access connection)
	(displayln "waiting for ssh access")
	(poll (lambda () (zero? (ssh (cons "-oConnectTimeout=10" connection)
								 "/bin/true")))
		  40
		  20000))
;;;;end utilities

;;;;a server is an abstract object that understands:
;;;;  'connection
;;;;  'teardown
;;;;  'java
(define (connection server) (server 'connection))
(define (teardown server)
	(displayln "tearing down server")
	(server 'teardown))
(define (java server) (server 'java))

;;;create-spec : empty string to say don't create a new data file
(define (make-test create-spec run-spec teardown? server-maker)
	(list create-spec run-spec teardown? server-maker))
(define create-spec car)
(define run-spec cadr)
(define teardown? caddr)
(define server-maker cadddr)

;;;; running remote tests

;;;template has placeholders for java, create args, run args
(define (run-test-script server test template)
  (car (ssh/strings (connection server)
                    (format #f
                            template
                            (java server)
                            (create-spec test)
                            (run-spec test)))))

(define-record-type job :job
	(make-job server test dir)
	job?
	(server job-server)
	(test job-test)
	(dir job-dir))
(define (record-job job)
	(format #t "spawned job on server ~a ~a\n"
			(connection (job-server job))
			(job-dir job))
	job)
(define script-detached
	". /tmp/stats.sh
	function iobench2() {
		proc=$1; shift
		~a -classpath /tmp/iobench.jar Main $proc \"$@\" \\
			1>$proc.out 2>$proc.err 
	}
	function iobench() {
		stats_around iobench2 \"$@\"
	}
	dir=`mktemp -d`
	cd \"$dir\" &&
	echo \"$dir\"
	( iobench create ~a &&
	  iobench run ~a
	  tar -czf results.tar.gz \\
		`find . \\( -name \"*.out\" -o -name \"*.err\" \\) -a -size +0c`
	  touch finished ) 1>detached.out 2>detached.err &")
;;;returns a job
(define (spawn-test server test)
	(displayln "starting job")
	(record-job (make-job server
						  test
						  (run-test-script server
                                           test
                                           script-detached))))
(define (await-file connection file interval-seconds)
	(zero? (ssh connection
				(format #f
						"while ! [ -f ~a ]; do sleep ~a; done"
						file
						interval-seconds))))
(define (job-ended? connection dir)
	(await-file connection (string-append dir "/finished") 10))
(define (fetch-results job)
	;;remote directory is not deleted
	(let ((connection (connection (job-server job)))
		  (dir        (job-dir job)))
      (scp-from connection dir "results.tar.gz")
      (run (tar -xzvvf results.tar.gz) stdports)
      (delete-file "results.tar.gz")))
(define (resources lst)
  (map (lambda (resource)
         (format #f "~a/~a" *RESOURCEDIR* resource))
       lst))
(define (deploy server)
	(displayln "deploying iobench.jar")
	(scp-to (connection server) "/tmp" (resources '("iobench.jar" "stats.sh"))))
(define (await-completion job)
	(displayln "polling for test completion")
	(poll-forever (lambda () (job-ended? (connection (job-server job))
										 (job-dir job)))
				  60000))
(define (run-test test)
	(let ((server ((server-maker test))))
		(deploy server)
		(let ((job (spawn-test server test)))
			(await-completion job)
			(fetch-results job))
		(if (teardown? test) (teardown server))))

;;;;simple servers

(define simple-servers
	'((localhost "localhost" "java")
	  (lfs       "localhost" "/usr/local/java/jdk1.6.0_07/bin/java")
	  (debian64  "192.168.56.101" "java")))
(define (simple-server-maker id)
	(let ((s (cdr (assoc id simple-servers))))
		(let ((host (car  s))
			  (java (cadr s)))
			(lambda () (lambda (message) (case message
					((connection) (list host))
					((teardown)   #f)
					((java)       java)))))))

;;;;EC2 servers

;;;;Queries
;(define (describe id) (run/string (ec2-describe-instances ,id)))
(define describe
  (batched-fn (lambda (invocations)
                (let ((lines (run/strings
                               (ec2-describe-instances
                                 ,@(apply append invocations)))))
                  (map (lambda (id)
                         (aif (find (lambda (line) (string-contains line id))
                                    lines)
                              it
                              ""))
                       (map car invocations))))
              5000))
(define (field description n)
	(run/string (| (echo ,description)
				   (awk ,(format #f "/^INSTANCE/ {printf(\"%s\",$~a)}" n)))))
(define (ip description) (field description (if (equal? ip-type 'public) 4 5)))
(define (state description) (field description 6))
(define (running? description) (equal? (state description) "running"))
;;;;end

;;;;an instance contains the details of a running EC2 server instance
(define (make-instance id keyfile user host) (list id keyfile user host))
(define (make-instance2 id user host) (make-instance id keyfile user host))
(define (make-instance3 user instance-info)
	(make-instance2 (car instance-info) user (cadr instance-info)))
(define instance-id car)
(define instance-key cadr)
(define instance-user caddr)
(define instance-host cadddr)
(define (instance-connection instance)
	(list "-oStrictHostKeyChecking=no"
		  (string-append "-oIdentityFile=" (instance-key instance))
		  (string-append (instance-user instance) "@"
						 (instance-host instance))))
;(define (instance-teardown instance)
;	(run (ec2-terminate-instances ,(instance-id instance))))
(define instance-teardown
  (batched-fn (lambda (invocations)
                (make-list
                  (length invocations)
                  (run (ec2-terminate-instances
                         ,@(map (lambda (invocation)
                                  (instance-id (car invocation)))
                                invocations))
                       stdports)))
              5000))

;;;;a server object that is an EC2 instance
(define (make-instance-server instance)
	(lambda (message) (case message
		((connection) (instance-connection instance))
		((teardown)   (instance-teardown instance))
		((java)       "java")
		((id)         (instance-id instance)))))
(define (make-instance-server2 user instance-info)
	(make-instance-server (make-instance3 user instance-info)))
(define (make-instance-server3 user id)
	(make-instance-server (make-instance2 id user (ip (describe id)))))

;;;;start an EC2 instance

(define (basic-run-args ami ec2-type block-device-mappings)
	(append (list ami "-k" keyname "-g" securitygroup "-t" ec2-type)
			(flatten-all (map (lambda (x) (cons "-b" x))
							  block-device-mappings))))
;;;block-device-mappings: list of "-b" arguments to ec2-run-instances,
;;;                       without the "-b".
;;;returns the instance-id
;(define (run-on-demand-instance ami ec2-type block-device-mappings)
;	(run/string (| (ec2-run-instances
;						,@(basic-run-args ami
;										  ec2-type
;										  block-device-mappings))
;				   (awk "/^INSTANCE/ {printf(\"%s\",$2)}"))))
(define run-on-demand-instance
  (batched-fn
    (lambda (invocations)
      ;;assume invocations is non-empty and that all are identical
      (let ((ami (car (car invocations)))
            (ec2-type (cadr (car invocations)))
            (block-device-mappings (caddr (car invocations))))
        (pad (run/strings (| (ec2-run-instances
                               ,@(basic-run-args ami
                                                 ec2-type
                                                 block-device-mappings)
                               -n ,(length invocations))
                             (awk "/^INSTANCE/ {print $2}")))
             (length invocations)
             "")))
    5000))

;;;; spot instances
;;;; goal: produce a function interchangeable with run-on-demand-instance

;;;returns (spot-request-id,state(open,active,cancelled,closed),instance-id)
(define (parse-spot-response-line line)
	(run/strings (| (begin (display line))
					(awk "-F\\t" "{printf(\"%s\\n%s\\n%s\",$2,$6,$12)}"))))
;(define (describe-spot-instance-request request-id)
;  (car (run/strings (ec2-describe-spot-instance-requests ,request-id))))
(define describe-spot-instance-request
  (batched-fn
    (lambda (invocations)
      (let ((lines (run/strings (ec2-describe-spot-instance-requests
                                  ,@(apply append invocations)))))
        (map (lambda (request-id)
               (aif (find (lambda (line) (string-contains line request-id))
                          lines)
                    it
                    ""))
             (map car invocations))))
    5000))
;;;loops forever until the spot request is made active or cancelled
;;;returns an instance-id
;;;fragile because will fail if any poll fails to return a spot response
(define (wait-while-open spot-response-line)
	(let ((spot-response (parse-spot-response-line spot-response-line)))
		(format #t "spot response: ~a\n" spot-response)
		(case (string->symbol (cadr spot-response))
			((active) (caddr spot-response))
			((open)
			 (sleep 60000)
			 (wait-while-open
               (describe-spot-instance-request (car spot-response))))
			((cancelled closed)
			 (error (format #f
							"spot request ~a has terminated"
							(car spot-response))))
			(else
			 (error (format #f
			 				"unrecognized state ~a"
							(cadr spot-response)))))))
;(define (request-spot-instance ami ec2-type block-device-mappings price)
;  (car (run/strings (ec2-request-spot-instances
;                      ,@(basic-run-args ami
;                                        ec2-type
;                                        block-device-mappings)
;                      "-r" "one-time"
;                      "-p" ,(number->string price)))))
(define request-spot-instance
  (batched-fn
    (lambda (invocations)
      ;;;as in run-on-demand-instance-batched,
      ;;;assume non-empty, identical invocations
      (let ((ami (car (car invocations)))
            (ec2-type (cadr (car invocations)))
            (block-device-mappings (caddr (car invocations)))
            (price (cadddr (car invocations))))
        (pad (run/strings (ec2-request-spot-instances
                            ,@(basic-run-args ami
                                              ec2-type
                                              block-device-mappings)
                            -n ,(length invocations)
                            "-r" "one-time"
                            "-p" ,(number->string price)))
             (length invocations)
             "")))
    5000))
(define (make-spot-instance-runner price)
	;;polymorphic with run-on-demand-instance
	;;returns an instance-id
	(lambda (ami ec2-type block-device-mappings)
		(wait-while-open (request-spot-instance
                           ami
                           ec2-type
                           block-device-mappings
                           price))))

;;;;end spot instances

;;;https://aws.amazon.com/amis/amazon-linux-ami-instance-store-64-bit
;;;https://aws.amazon.com/amis/amazon-linux-ami-ebs-backed-64-bit
;;;region,instance-store-ami,ebs-ami
(define amis '((us-east                     "ami-41814f28" "ami-1b814f72")
			   (asia-pacific-tokyo          "ami-f45beff5" "ami-0a44f00b")
			   (south-america               "ami-063be41b" "ami-3c3be421")
			   (us-west-northern-california "ami-09d68a4c" "ami-1bd68a5e")
			   (us-west-oregon              "ami-caff72fa" "ami-30fe7300")
			   (asia-pacific-singapore      "ami-80b0cad2" "ami-beb0caec")
			   (eu-west                     "ami-a33b06d7" "ami-953b06e1")))
(define (ami storage-type)
	((if (equal? storage-type 'ephemeral) cadr caddr) (assoc region amis)))

;;;returns an instance-info=(id,ip), which is used to construct an instance.
(define (wait-until-running id)
	(define (iter n)
		(sleep 30000)
		(let ((description (describe id)))
			(cond ((running? description) (ip description))
				  ((<= n 1)               '())
				  (else                   (iter (- n 1))))))
	(let ((ip (iter 60)))
		(if (null? ip)
			(error (format #f "instance ~a failed to start" id))
			(begin (format #t "instance is running with IP ~a\n" ip)
				   (list id ip)))))

(define (block-devices n c fmt . fns)
	(map (lambda (i) (apply format #f fmt (add-char c i)
							(map (inv i) fns)))
		 (iota n)))
(define (block-devices2 storage volumes volume-size)
	(if (equal? storage 'ebs)
		(block-devices volumes #\f (format #f "/dev/sd~~a=:~a" volume-size))
		(block-devices volumes #\b "/dev/sd~a=ephemeral~a" identity)))
(define (block-device-name block-device-mapping)
	(substring block-device-mapping 0 (string-length "/dev/sd_")))

(define (use-mount path)
	(format
		#f
		"mount -o remount,noatime ~a
		chmod og+rw ~a
		rm -f /iobenchdata
		ln -s ~a /iobenchdata"
		path path path))
(define (setup-raid level device-names)
	(format
		#f
		"yes | stats_around mdadm /dev/md0 --create --auto yes -l ~a -n ~a ~a
		 stats_around mke2fs -j /dev/md0
		 mount /dev/md0 /mnt &&
		 ~a"
		level
		(length device-names)
		(string-join device-names " ")
		(use-mount "/mnt")))
(define (prep-fs connection storage vols raid block-device-names)
	(displayln "preparing filesystem")
	(ssh-sudo
		connection
		(string-append
			". /tmp/stats.sh; "
			(if (= vols 1)
				(if (equal? storage 'ephemeral)
					(use-mount "/media/ephemeral0")
					(string-append "stats_around mke2fs -j /dev/sdf; "
								   "mount /dev/sdf /mnt; "
								   (use-mount "/mnt")))
				(string-append
					(if (equal? storage 'ephemeral)
						"umount /media/ephemeral0; "
						"")
					(setup-raid raid block-device-names))))))
(define (init-instance-server connection storage vols
							  raid block-device-mappings)
	(ensure-ssh-access connection)
	(fix-sudo-conf connection)
	(scp-to connection "/tmp" (resources '("stats.sh"))) ;duplicate scp
	(prep-fs connection storage vols raid (map block-device-name
											   block-device-mappings)))
;;;raid: raid level, if volumes > 1
;;;runner: returns an instance id
;;;returns a newly launched instance-server with a directory /iobenchdata
;;;for data files
(define (start-instance-server runner ec2-type storage vols vol-size raid)
	(let ((block-device-mappings (block-devices2 storage vols vol-size)))
		(let ((server 
				(make-instance-server2
					"ec2-user"
					(wait-until-running (runner (ami storage)
												ec2-type
												block-device-mappings)))))
			(init-instance-server
				(connection server)
				storage
				vols
				raid
				block-device-mappings)
			server)))

;;;key,type,storage,volumes,volume-size-GB,raid_level.
;;;the cache file maps these keys to instance-ids,
;;;so add or remove definitions instead of editing them.
;;;raid_level is of the form "raid10" or 10. man mdadm, --level .
;;;raid is only actually set up when volumes > 1.
(define instance-defs
			'((1 "m1.small"  ephemeral 1 #f 0)
			  (2 "m1.small"  ebs       1 24 0)
			  (3 "m1.small"  ebs       4  6 0)
			  (4 "m1.medium" ephemeral 1 #f 0)
			  (5 "m1.large"  ephemeral 2 #f 0)
			  (6 "m1.large"  ebs       1 80 0)
			  (7 "m1.large"  ebs       4 20 0)
			  (8 "m1.xlarge" ephemeral 4 #f 0)))
(define (instance-defs-at n) (cdr (assoc n instance-defs)))
(define (start-instance-server-def instance-runner n)
	(apply start-instance-server instance-runner (instance-defs-at n)))
(define (run-uninitialized-instance . ignored)
  (car (run/strings (cat "instance_id"))))
;(define (init-instance-def n id)
;	(start-instance-server-def (make-running-instance-runner id) n))

;;; stolen back from cost-table.scm
(define instance-type-costs
        '((us-east ("m1.small"  0.080)
				   ("m1.medium" 0.160)
				   ("m1.large"  0.320)
				   ("m1.xlarge" 0.640))))
(define (instance-type n) (car (instance-defs-at n)))
(define (instance-def-cost n)
	 (cadr (assoc (instance-type n)
	 			  (cdr (assoc region instance-type-costs)))))
;;;historically, the spot price has never been higher than 1.2 x on-demand
(define (spot-bid n) (* 1.2 (instance-def-cost n)))

;;;returns (RAM,effective_storage) in GB
(define (document instance-def)
	;;http://aws.amazon.com/ec2/instance-types/
	;;this is what ec2 provides for each instance type
	;;type,memory_gb,max_instance_storage_gb,volumes
	(define specs '(("m1.small"   1.7   160 1)
					("m1.medium"  3.75  410 1)
					("m1.large"   7.5   850 2)
					("m1.xlarge" 15    1690 4)))
	(define (spec type) (cdr (assoc type specs)))
	(define (volume-size type)
		(let ((s (spec type)))
			(/ (cadr s) (caddr s))))
	(define (effective-storage vol-count vol-size raid-level)
		(case raid-level
			((0) (* vol-count vol-size)) ;stripe
			((1) vol-size)))             ;mirror
			;;add other raid levels
	(list (car (spec (car instance-def)))
		  (effective-storage (caddr instance-def)
							 (if (equal? (cadr instance-def) 'ephemeral)
								 (volume-size (car instance-def))
								 (cadddr instance-def))
							 (list-ref instance-def 4))))
(define documented-instance-defs
	(map (lambda (x) (append x (document (cdr x))))
		 instance-defs))

;;;;instance server cache
(define cache-file "instances")
(define (load-cache filename)
	(if (file-exists? filename)
		(with-input-from-file filename read)
		'()))
(define (persist transformation)
	(let ((ids (load-cache cache-file)))
		(with-output-to-file cache-file
			(lambda () (write (transformation ids))))))
(define (cache n instance-server)
	(persist (lambda (ids) (alist-replace n (instance-server 'id) ids)))
	instance-server)
(define (lookup-cache n)
	(aif (assoc n (load-cache cache-file)) (cdr it) #f))
(define (remove-from-cache n)
	(persist (lambda (ids) (alist-delete n ids))))
(define (caching-instance-server-maker n instance-runner)
	(lambda ()
		(let ((id (lookup-cache n)))
			(let ((server (if (and id (running? (describe id)))
							  ;;fix duplicate username
							  (make-instance-server3 "ec2-user" id)
							  (cache n (start-instance-server-def
							  				instance-runner
											n)))))
				(lambda (message) ;decorate server
					(if (equal? message 'teardown) (remove-from-cache n))
					(server message))))))
;;;;end cache

(define (cli-usage)
	(display #<<EOF
Usage: ./remote-run.scm <dir> <n>
       { -s <simple-server-id> | -i <instance-def-number> <teardown> [-d | -r] }
       <create-args> <run-args>

dir : base directory for n parallel runs. will be created if necessary.
n   : number of parallel runs

create-args : arguments to the java program's create command
run-args    : arguments to the java program's run command

-s : test an ordinary ssh server, identified by a key in the table,

EOF
)
(map (lambda (x) (format #t "    ~a\n" x)) simple-servers)
(display #<<EOF

-i : test an amazon instance, chosen from the instance definitions below.
     In such a test, the RAID/EBS/Ephemeral disk will always be mounted on
     /iobenchdata,  Specify this directory name in create-args and run-args.
teardown : t | f
The default is to use spot instances.  Otherwise,
-d : runs an on-demand instance instead.
-r : initializes and uses a running instance, whose id has been specified in
     <dir>/<i>/instance_id .  This only works if the cache file, instances,
     doesn't describe a running instance.

Instance Definitions:
(number type storage vols vol-size(GB) raid-level RAM(GB) effective-storage(GB))

EOF
)
(map displayln documented-instance-defs)
(display #<<EOF

Examples:
./remote-run.scm trial 5 -i 1 f "/iobenchdata/file1 1M" \
                                "/iobenchdata/file1 60 read,1,1K,1K"
use an existing file:
./remote-run.scm trial 5 -i 1 f "/dev/null 0" \
                                "/iobenchdata/file1 60 read,1,1K,1K"
only create a file:
./remote-run.scm trial 5 -i 1 f "/iobenchdata/file1 1M" "/dev/null 0"

EOF
))
(define (show-error)
	(displayln "invoke with -h for help")
	(exit 1))
(define (show-help)
	(with-current-output-port* (current-error-port) cli-usage)
	(exit 1))
(define (args->test args)
  (apply (lambda (teardown? server-maker)
           (let ((p (take-right args 2)))
             (make-test (car  p) (cadr p) teardown? server-maker)))
         (cond ((equal? "-s" (car args))
                (list #f
                      (simple-server-maker (string->symbol (cadr args)))))
               ((equal? "-i" (car args))
                (list (equal? "t" (caddr args))
                      (let ((n (string->number (cadr args))))
                        (caching-instance-server-maker
                          n
                          (case (string->symbol (list-ref args 3))
                            ((-d) run-on-demand-instance)
                            ((-r) run-uninitialized-instance)
                            (else (make-spot-instance-runner
                                    (spot-bid n)))))))))))
(define (run-args args)
	(run-test (args->test args)))

;;;; run-n

(define (clear-dir dir)
  (for-each (lambda (name)
              (for-each (lambda (ext)
                          (let ((file (format #f "~a/~a.~a"
                                              dir name ext)))
                            (if (file-exists? file)
                              (delete-file file))))
                        '("out" "err")))
            '("create" "run" "detached" "remote-run")))
;;; sets cwd and io for this thread
(define (run-env basedir i args)
  (let ((dir (format #f "~a/~a" basedir (number->string i))))
    (if (file-exists? dir)
      (clear-dir dir)
      (create-directory dir))
    (with-cwd dir
      (close-after (open-output-file "remote-run.err")
        (lambda (err)
          (close-after (open-output-file "remote-run.out")
            (lambda (out)
              (with-current-error-port err
                (with-current-output-port out
                  (run-args args))))))))))
;;; forks and waits
(define (run-n basedir n args)
  (if (not (file-exists? basedir))
    (create-directory basedir))
  (let ((latch (make-latch n)))
    (for-each (lambda (i)
                (fork-thread
                  (lambda ()
                    (finally
                      (lambda () (latch-countdown latch))
                      (lambda () (run-env basedir i args))))))
              (iota n 1 1))
    (latch-await latch)))
(define (main prog+args)
  (let ((args (cdr prog+args)))
    (cond ((zero? (length args))    (show-error))
          ((equal? "-h" (car args)) (show-help))
          (else
            (run-n (car args)
                   (string->number (cadr args))
                   (cddr args))))))

))
; vim: ts=4
