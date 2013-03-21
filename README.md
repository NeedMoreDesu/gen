# gen

This is an attempt to make an actor model in clojure. Utilizes
clojure's refs and promises.

The basic unit is process object, that have message queue inside.
The idea of the process is that it knows how to start and stop itself,
return values and recieve messages. Processes are pretty-printed in repl.

Loop, server, linker and supervisor modules aids to create a process
you want, accepting functions as arguments.

## Usage

Simple loop example

```clojure
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

## License

Copyright © 2013 NeedMoreDesu

Distributed under Do What The Fuck You Want To Public License.
