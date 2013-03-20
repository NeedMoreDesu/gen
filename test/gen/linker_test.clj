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
(Thread. (fn[] (/ 0 0))) -- can I print stacktrace, given a thread? 
(pr/state-of t4)(filter #(not (gen.process/alive? %1)) #{t3})
(def handler (proxy [Thread$UncaughtExceptionHandler] [] 
  (uncaughtException [thread exception]
    (println thread exception))))
(def thread (Thread. (bound-fn[] (/ 0 0))))
(.setUncaughtExceptionHandler thread handler)
(.start thread)
pprint  10) @(:thread (.data t4))
(pr/message t3_2 'hi)
(pr/message t1 "Eto ti?")
(pr/state-of t1)
(pr/state-of t2)
(pr/state-of t3)
(defn qqq [& {:keys [x y z] :or {x 10 y 20} :as args}] (assoc args :x x :y y))
(pr/result-of t1)
(pr/result-of t2)
(def t2p (second t2))
@(pr/stop t1 :kill)
@(pr/stop t2 :kill)
@(pr/stop t3_1 :kill)
@(pr/stop t3_2 :kill)
@(pr/stop t4 :kill)
(pr/alive? t4)
(pr/result-of t2)
(.toString ls/*linker*)
(def q ls/storage-create)
(.toString q)
(ls/storage-add *linker* @(:thread @t1) t1)
(:links @(second t2))
(:return-promise (def t3 (pr/start (second t2))))
(count (ls/storage-get-processes *linker*))
(deftype her [h])
(her. 2)
(defmethod print-method her
 [o w]
 (print-simple
 (str "#<" "Lalala: "    (.h o) " >")
  w))


(defn thread-exception-removal
  "Exceptions thrown in the graphics rendering thread generally cause
  the entire REPL to crash! It is good to suppress them while trying
  things out to shorten the debug loop."
  []
  (.setUncaughtExceptionHandler
   (Thread/currentThread)
   (proxy [Thread$UncaughtExceptionHandler] []
     (uncaughtException
       [thread thrown]
       (println "uncaught-exception thrown in " thread)
       (println (.getMessage thrown))))))
(thread-exception-removal)
(/ 12 0)

(Thread/setDefaultUncaughtExceptionHandler handler)
(Thread/setDefaultUncaughtExceptionHandler
 (reify Thread$UncaughtExceptionHandler
   (uncaughtException [this thread throwable]
     (println throwable)
     )))

(def handler (proxy [Thread$UncaughtExceptionHandler] [] 
  (uncaughtException [thread exception]
    (println thread exception))))

(Thread/setDefaultUncaughtExceptionHandler handler)

(/ 12 0)

    (def handler (proxy [Thread$UncaughtExceptionHandler] []
      (uncaughtException [thread exception]
        (println thread exception))))
    (def thread (Thread. (bound-fn[] (/ 0 0))))
    (.setUncaughtExceptionHandler thread handler)
    (.start thread)

(.start
 (Thread.
  (bound-fn []
   (try ((bound-fn [] (/ 0 0)))
    (catch Exception e (clojure.stacktrace/print-stack-trace (clojure.stacktrace/root-cause e) 2))))))

(instance? Exception (try (/ 0 0) (catch Exception e e)))
(defn foo [x y] (+ x y))
(defn bar [x y] (* x y))
(defn c [x] (comp #(foo x %) #(bar x %)))
(defn c [x] (comp (partial foo x) (partial bar x)))

(throw (Exception. "В сортах говна не разбираюсь"))
