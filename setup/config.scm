;where instances will be created.
;a key in the amis table in remote-run.scm.
;EC2_URL environment variable must also match.
(define region 'us-east)

(define keyname "iobench") ;to launch instances with
(define keyfile "iobench.pem")
(define securitygroup "iobench") ;allows ssh

;set to private if your control computer is an EC2 instance in the same region
(define ip-type 'private) ;public or private
