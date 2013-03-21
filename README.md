# gen

This is an attempt to make an actor model in clojure. Utilizes
clojure's refs and promises.

The basic unit is process object, that have message queue inside.
The idea of the process is that it knows how to start and stop itself,
return values and recieve messages. Processes are pretty-printed in repl.

Loop, server, linker and supervisor modules aids to create a process
you want, accepting functions as arguments.

Pre-Alpha Status
----
Function definitions may change, project is generally untested. Be wary.

Releases and dependency information
----

[Leiningen](http://github.com/technomancy/leiningen/) dependency information:
```
[actors/gen "0.1.0-SNAPSHOT"]
```

## Usage

Simple loop example

```clojure
(require '[gen process loop])
(def loop1 (gen.loop/create
            :init (fn [process arg]
                   (println "I've initialized with" arg)
                   [:run arg])
            :body (fn [state process]
                   (println "Im running with" state)
                   (Thread/sleep 2000)
                   [:run (inc state)])))
;; => #'test/loop1

@(gen.process/start loop1 8999) ; start and stop are futures, deref to see result
;; => [:started #<Thread Thread[Thread-64,5,main]>]
;; At this point you should see things in your repl
loop1
;; => #<GenProcess Loop status: alive-linked, state: 9030>
@(gen.process/stop loop1 :nya-death)
;; => [:terminated :ok]
(gen.process/result-of loop1)
;; => [:result [:terminated :nya-death]]
loop1
;; => #<GenProcess Loop status: dead, state: 9064, [:result [:terminated :nya-death]]>
(gen.process/state-of loop1)
;; => [:result 9064]
```

Some supervisor and linker examples

```clojure
(require '[gen process loop linker supervisor])
(def linker (gen.linker/create))
(def supervisor (gen.supervisor/create
                 :processes #{linker}
                 :rules {linker (gen.supervisor/rule-create
                                 :important? true
                                 :max-restarts nil)}))

@(gen.process/start supervisor nil)     ; supervisor and linker ignore args
;; => [:started #<Thread Thread[Thread-70,5,main]>]

supervisor
;; => #<GenProcess Supervisor status: alive-linked, processes: [#<GenProcess Linker status:
;; alive-linked, count: 2>], count 1>
;; and in repl:
;; == STARTED supervisor #<GenProcess Supervisor status: alive-linked, processes: [], count 0> ==
;; |= STARTED #<GenProcess Linker status: alive-linked> =|


(gen.supervisor/process-add supervisor loop1)
;; => true
;; whoops. Something is going on in repl:
;; == In supervisor #<GenProcess Supervisor status: alive-linked, processes: [#<GenProcess Linker
;; status: alive-linked, count: 3>], count 1> ==
;; |= STARTED #<GenProcess Loop status: alive-linked, state: 9064> =|
;; I've initialized with nil
;; Im running with nil
;; === In process #<GenProcess Loop status: alive-linked, state: nil> ===
;; === In thread #<Thread Thread[Thread-72,5,main]> ===
;; java.lang.NullPointerException: null
;;  at clojure.lang.Numbers.ops (Numbers.java:942)
;;     clojure.lang.Numbers.inc (Numbers.java:110)
;;     test/fn (NO_SOURCE_FILE:8)
;;     gen.loop$create$start_fn__1197$fn__1198.invoke (loop.clj:27)
;;     clojure.lang.AFn.applyToHelper (AFn.java:159)
;;     clojure.lang.AFn.applyTo (AFn.java:151)
;;     clojure.core$apply.invoke (core.clj:601)
;;     clojure.core$with_bindings_STAR_.doInvoke (core.clj:1771)
;;     clojure.lang.RestFn.invoke (RestFn.java:425)
;;     clojure.lang.AFn.applyToHelper (AFn.java:163)
;;     clojure.lang.RestFn.applyTo (RestFn.java:132)
;;     clojure.core$apply.invoke (core.clj:605)
;;     clojure.core$bound_fn_STAR_$fn__3984.doInvoke (core.clj:1793)
;;     clojure.lang.RestFn.invoke (RestFn.java:397)
;;     gen.process$eval1066$start__1079$fn__1080$fn__1081$fn__1082.invoke (process.clj:108)
;;     gen.process$eval1066$start__1079$fn__1080$fn__1081.invoke (process.clj:107)
;;     clojure.lang.AFn.applyToHelper (AFn.java:159)
;;     clojure.lang.AFn.applyTo (AFn.java:151)
;; 
;;     clojure.core$apply.invoke (core.clj:601)
;;     clojure.core$with_bindings_STAR_.doInvoke (core.clj:1771)
;;     clojure.lang.RestFn.invoke (RestFn.java:425)
;;     clojure.lang.AFn.applyToHelper (AFn.java:163)
;;     clojure.lang.RestFn.applyTo (RestFn.java:132)
;;     clojure.core$apply.invoke (core.clj:605)
;;     clojure.core$bound_fn_STAR_$fn__3984.doInvoke (core.clj:1793)
;;     clojure.lang.RestFn.invoke (RestFn.java:397)
;;     clojure.lang.AFn.run (AFn.java:24)
;;     java.lang.Thread.run (Thread.java:722)
;; == In supervisor #<GenProcess Supervisor status: alive-linked, processes: [#<GenProcess Linker
;; status: alive-linked, count: 2> #<GenProcess Loop status: dead, state: nil, [:fail
;; #<NullPointerException java.lang.NullPointerException>]>], count 2> ==
;; |= FAIL: #<GenProcess Loop status: dead, state: nil, [:fail #<NullPointerException
;; java.lang.NullPointerException>]> =|
;; |- with thread #<Thread Thread[Thread-72,5,]> -|
;; |- max number of restarts 0 reached in 4 milliseconds -|

(gen.supervisor/process-add supervisor loop1
 (gen.supervisor/rule-create :args 1234)) ; Ah, right. Forgot init arg.
;; => true
;; in repl:
;; == In supervisor #<GenProcess Supervisor status: alive-linked, processes: [#<GenProcess Linker
;; status: alive-linked, count: 3>], count 1> ==
;; |= STARTED #<GenProcess Loop status: alive-linked, state: nil> =|
;; I've initialized with 1234
;; Im running with 1234
;; Im running with 1235

@(gen.process/stop supervisor
  :some-nice-looking-reason-to-kill-main-supervisor)
;; => [:terminated :ok]
;; in repl:
;; == supervisor #<GenProcess Supervisor status: alive-linked, processes: [#<GenProcess Linker
;; status: alive-linked, count: 3> #<GenProcess Loop status: alive-linked, state: 1708>], count 2>
;; ==
;; -- with thread #<Thread Thread[Thread-70,5,main]> --
;; -- CRASHED --
;; |- terminated #<GenProcess Linker status: dead, count: 2, [:result [:terminate :supervisor-terminate]]> -|
;; |- with thread #<Thread Thread[Thread-71,5,]> -|
;; |- terminated #<GenProcess Loop status: dead, state: 1709, [:result [:terminated :supervisor-terminate]]> -|
;; |- with thread #<Thread Thread[Thread-74,5,]> -|

@(gen.process/start supervisor nil)
;; => [:started #<Thread Thread[Thread-78,5,main]>]
;; in repl:
;; == STARTED supervisor #<GenProcess Supervisor status: alive-linked, processes: [#<GenProcess
;; Linker status: dead, count: 3, [:result [:terminate :supervisor-terminate]]> #<GenProcess Loop
;; status: dead, state: 1709, [:result [:terminated :supervisor-terminate]]>], count 2> ==
;; |= STARTED #<GenProcess Linker status: alive-linked, count: 4> =|

supervisor
;; => #<GenProcess Supervisor status: alive-linked, processes: [#<GenProcess Linker status:
;; alive-linked, count: 2>], count 1>

@(gen.process/stop linker :just-die)
;; => [:terminated :ok]
;; in repl:
;; == In supervisor #<GenProcess Supervisor status: alive-linked, processes: [#<GenProcess Linker
;; status: dead, count: 1, [:result [:terminate :just-die]]>], count 1> ==
;; |= restart: #<GenProcess Linker status: dead, count: 1, [:result [:terminate :just-die]]> =|
;; |- with thread #<Thread Thread[Thread-79,5,]> -|
;; |- process restarts infinitely -|

@(gen.process/stop linker :die-die-die!!)
;; => [:terminated :ok]
@(gen.process/stop linker :Y-DONT-U-DIE??!!)
;; => [:terminated :ok]
;; you got the idea. :)

(gen.supervisor/process-add supervisor loop1
 (gen.supervisor/rule-create
  :important? loop1
  :args 1234))
@(gen.process/stop loop1 :oops)
;; == In supervisor #<GenProcess Supervisor status: alive-linked, processes: [#<GenProcess Linker
;; status: alive-linked, count: 3> #<GenProcess Loop status: dead, state: 1424, [:result
;; [:terminated :oops]]>], count 2> ==
;; == IMPORTANT PROCESS DEATH ==
;; |= FAIL: #<GenProcess Loop status: dead, state: 1424, [:result [:terminated :oops]]> =|
;; |- with thread #<Thread Thread[Thread-88,5,]> -|
;; |- max number of restarts 0 reached in 6 milliseconds -|
;; == supervisor #<GenProcess Supervisor status: alive-linked, processes: [#<GenProcess Linker
;; status: alive-linked, count: 3>], count 1> ==
;; -- with thread #<Thread Thread[Thread-78,5,main]> --
;; -- CRASHED --
;; |- terminated #<GenProcess Linker status: dead, count: 1, [:result [:terminate :supervisor-terminate]]> -|
;; |- with thread #<Thread Thread[Thread-87,5,]> -|

@(gen.process/start supervisor nil)
;; => [:started #<Thread Thread[Thread-95,5,main]>]

(gen.supervisor/process-add supervisor loop1
 (gen.supervisor/rule-create
  :args 1234))
;; => true

;; let's kill supervisor so it won't have a chance to stop all processes
(gen.process/kill supervisor)           ; brutal kill
;; => true
;; in repl:
;; == in linker #<GenProcess Linker status: alive-linked, count: 2> ==
;; -- terminated #<GenProcess Loop status: dead, state: 1247, [:result [:terminated :link]]> --
;; -- with thread #<Thread Thread[Thread-97,5,]> --
;; == in linker #<GenProcess Linker status: alive, count: 1> ==
;; -- terminated #<GenProcess Linker status: alive, count: 1> --
;; -- with thread #<Thread Thread[Thread-96,5,main]> --

loop1
;; => #<GenProcess Loop status: dead, state: 1247, [:result [:terminated :link]]>
linker
;; => #<GenProcess Linker status: dead, count: 0, [:result [:terminate :link]]>
;; Those processes was linked to supervisor, because they were started
;; with gen.process/start-link. If supervisor die, those process will be
;; stopped with a linker process. Links are one-sided, so supervisor won't
;; die, if one of it's processes is terminated.

;; There should be only one linker. It works with
;; gen.linker-storage/*linker*, but you can manually pass linker
;; variable to each process you create. Or bind *linker* with binding.
```

Server example

```clojure
(require '[gen process server linker supervisor])

(def linker (gen.linker/create))
(def server1 (gen.server/create
              :init (fn [process args]
                     (gen.server/message process args [:inc 0])
                     [:run nil])
              ;; gen.server/message sends [message process-from] messages
              ;; lets divide it to [[type message] from]
              :handler (fn [[[type message] from] state process]
                        (case type
                         :inc (gen.server/message
                               process
                               from
                               [:inc (inc message)]))
                        [:run state])))
(def server2 (gen.server/create
              :handler (fn [[[type message] from] state process]
                        (case type
                         :inc (gen.server/message
                               process
                               from
                               [:inc (inc message)]))
                        (if (< 10000 message)
                         [:stop state]
                         [:run state]))))
(def supervisor (sup/create
                 :processes #{linker server1 server2}
                 :rules {linker (sup/rule-create
                                 :important? true
                                 :max-restarts nil)
                         server1 (sup/rule-create
                                  :important? true
                                  ;; passing other server as init, so
                                  ;; they can start communicating
                                  :args server2)
                         server2 (sup/rule-create
                                  :important? true)}))
@(gen.process/start supervisor nil)
;; => [:started #<Thread Thread[Thread-127,5,main]>]
;; in repl:
;; == STARTED supervisor #<GenProcess Supervisor status: alive-linked, processes: [], count 0> ==
;; |= STARTED #<GenProcess Linker status: alive-linked, count: 4> =|
;; |= STARTED #<GenProcess Server status: alive-linked, state: nil, last-message: nil> =|
;; |= STARTED #<GenProcess Server status: alive-linked, state: nil, last-message: [:inc 503]> =|

;; after a while
;; == In supervisor #<GenProcess Supervisor status: alive-linked, processes: [#<GenProcess Linker
;; status: alive-linked, count: 4> #<GenProcess Server status: dead, messages: 1, state: nil,
;; last-message: [:inc 10002], [:result [:stopped {:state nil, :last-message [[:inc 10002]
;; #<GenProcess Server status: alive-linked, state: nil, last-message: [:inc 10003]>]}]]>
;; #<GenProcess Server status: alive-linked, state: nil, last-message: [:inc 10003]>], count 3> ==
;; == IMPORTANT PROCESS DEATH ==
;; |= FAIL: #<GenProcess Server status: dead, messages: 1, state: nil, last-message: [:inc 10002],
;; [:result [:stopped {:state nil, :last-message [[:inc 10002] #<GenProcess Server status:
;; alive-linked, state: nil, last-message: [:inc 10003]>]}]]> =|
;; |- with thread #<Thread Thread[Thread-129,5,]> -|
;; |- max number of restarts 0 reached in 4 milliseconds -|
;; == supervisor #<GenProcess Supervisor status: alive-linked, processes: [#<GenProcess Linker
;; status: alive-linked, count: 4> #<GenProcess Server status: alive-linked, state: nil,
;; last-message: [:inc 10003]>], count 2> ==
;; -- with thread #<Thread Thread[Thread-127,5,main]> --
;; -- CRASHED --
;; |- terminated #<GenProcess Linker status: dead, count: 2, [:result [:terminate :supervisor-terminate]]> -|
;; |- with thread #<Thread Thread[Thread-128,5,]> -|
;; |- terminated #<GenProcess Server status: dead, state: nil, last-message: [:inc 10003], [:result :killed]> -|
;; |- with thread #<Thread Thread[Thread-130,5,]> -|

;; So, it reached 10000 and server2 stopped. All the system collapsed,
;; because important process is dead and cannot be restarted.
```

## License

Copyright © 2013 NeedMoreDesu desu@horishniy.org.ua

This program is free software. It comes without any warranty, to
the extent permitted by applicable law. You can redistribute it
and/or modify it under the terms of the Do What The Fuck You Want
To Public License, Version 2, as published by Sam Hocevar. See
COPYING for more details.
