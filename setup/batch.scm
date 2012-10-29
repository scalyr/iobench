(define-structure batch (export critical batched-fn)
        (open scheme-with-scsh
                  ;big-scheme
                  threads
                  thread-fluids
                  locks
                  placeholders
                  srfi-1
                  srfi-13
                  srfi-27
                  my-util-def)
        (begin

;procedure: ec2-describe-instances
;args: a b c d e
;invocation = procedure + args
;group: (equal? a b c d e)
;	or group-rule is a provided function that returns a group key
;wait-upto: 30 seconds
;group-invocation:
;	(lambda (invocation-group)
;		(append (car invocation-group) (list "-n" (length invocation-group))))
;distribute-results:
;	(lambda (invocation-group batched-result)
;		(map list invocation-group (split batched-result "\n")))


;(define (make-task command+args)
;	(list command+args (group-rule command+args)))
;(define (main command+args)
;	(let ((t (make-task command+args)))
;		(add-task t)
;		(let ((result (await-completion t)))
;			;;display (stderr result)
;			;;display (stdout result)
;			;;return  (exit-status result)

;----
;function mapping many invocations onto a single batched one. or n batches.
;function distributing multi-result to those original invocations.

;(define (ec2-run-instances ...) ...)
;(batch ec2-run-instances
;	   500              ;milliseconds
;	   (lambda () ...)  ;batcher
;	   (lambda () ...)) ;result distributor
;;;;
;in general, many functions may want to be batched into one.
;also you might want to limit the batch size.

(define (critical lck fn)
  (lambda args
    (obtain-lock lck)
    (let ((result (apply fn args)))
      (release-lock lck) ;need finally
      result)))
;;; returns a terminator
(define (bg-repeat thunk interval-ms)
  (let ((terminated #f))
    (define (loop)
      (if terminated
        '()
        (begin (thunk)
               (sleep interval-ms)
               (loop))))
    (fork-thread loop)
    (lambda () (set! terminated #t))))

;; usage
(define x 1)
(define x-lock (make-lock))
(define (test1)
  (let ((t1 (bg-repeat (critical x-lock
                                 (lambda () (display x)))
                       1000))
        (t2 (bg-repeat (critical x-lock
                                 (lambda () (set! x (+ x 1))))
                       1500)))
    (sleep 5000)
    (t1)
    (t2)))

;; batcher maps n invocations to n results
(define (batched-fn batcher interval-ms)
  ;;;a batch is a list of (placeholder invocation-args)
  (define batch (list))
  (define batch-lock (make-lock))
  (define (process-batch b)
    (or (null? b)
        (map placeholder-set!
             (map car b)
             (batcher (map cadr b)))))
  (define take-batch (critical batch-lock (lambda ()
                                            (let ((b batch))
                                              (set! batch (list))
                                              b))))
  ;;;the first invocation in a batch triggers this
  (define (schedule-processing)
    (fork-thread (lambda ()
                   (sleep interval-ms)
                   (process-batch (reverse (take-batch))))))
  (lambda args
    (let ((p (make-placeholder)))
      (obtain-lock batch-lock)
      (if (zero? (length batch)) (schedule-processing))
      (set! batch (cons (list p args) batch))
      (release-lock batch-lock)
      (placeholder-value p))))

;original
;(define (ls-l filename) (car (run/strings (ls -l ,filename))))
;replacement
(define ls-l
  (batched-fn (lambda (invocations)
                ;not quite right. ls -l b a returns (a b) instead of (b a)
                (run/strings (ls -l ,@(apply append invocations))))
              1000))
;usage
(define l1 '())
(define l2 '())
(define (test2)
    (fork-thread (lambda () (set! l1 (ls-l "2.txt"))))
    (fork-thread (lambda () (set! l2 (ls-l "pl.ini")))))

))
