(define-structure my-util-def (export displayln
									  flatten-all
									  add-char
									  alist-replace
									  aif
									  inv
									  flatten
									  make-collector
									  consecutive-pairs
									  separate
									  replace
									  split
									  format-table display-table
									  transpose
									  vector-sum1 vector-sum
                                      pad
                                      make-latch latch-countdown latch-await
                                      finally)
(open scheme-with-scsh srfi-1 threads thread-fluids handle locks)
(files "my-util.scm")
(begin))

