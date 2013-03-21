(ns linker-test
 (:require [gen
            [process :as pr]
            [loop :as lo]
            [linker-storage :as ls]
            [linker :as li]
            [server :as se]
            [supervisor :as sup]
            [core :as core]]))
(def t1 (lo/create
         :init (fn [process] [:run 0])
         :body (fn [state process] [:run (inc state)])))
(pr/self)
(def t2 (li/create))
(def t3_1 (se/create
           :init (fn [process args]
                  (se/message process args [:inc 0])
                  [:run nil])
           :handler (fn [[[type message] from] state process]
                     (case type
                      :inc (se/message process from [:inc (inc message)]))
                     [:run state])))
(def t3_2 (se/create
           :handler (fn [[[type message] from] state process]
                     (case type
                      :inc (se/message process from [:inc (inc message)]))
                     [:run state])))
(core/silent-all-messages
 (def t4 (sup/create
          :processes #{t3_1 t3_2 t2}
          :rules {t2 (sup/rule-create
                      :important? true
                      :max-restarts nil)
                  t3_1 (sup/rule-create
                        :important? true
                        :args t3_2)
                  t3_2 (sup/rule-create
                        :important? true)})))
(binding [sup/*print-crash-reports* true]
 @(pr/start t4 nil)
 @(pr/stop t4 nil))

(sup/processes t4)
(li/processes t2)
(sup/supervisor-processes t4)
(sup/process-add t4 t1 (sup/rule-create :max-restarts 1))
(sup/process-remove t4 t3_1)
@(pr/start-link t3_1)
@(pr/restart-link t2 :t nil)
(pr/state-of t3_1)
(se/message nil t3_1 [:init t3_2])
(binding [sup/*print-crash-reports* true]
 @(pr/stop t4 :t))
@(pr/stop t2 :t)
@(pr/stop t3_1 :t)
(count (set (keys (li/processes t2))))
(seq (ls/storage-get-processes ls/*linker*))
(pr/have-dead-links t3_1)
(pr/get-links t3_1)
(count
 (filter (fn[[t _]] (= :terminated t))
  (map (fn[_] (Thread/sleep 5) @(pr/stop t3_1 :nya)) (range 1000))))

@(pr/stop t3_2 :kill)
@(pr/stop t4 :kill)
(pr/start-link t1)
(pr/result-of t4)
(pr/state-of t4)
(pr/have-dead-links t2)
(pr/state-of t3_1)

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
