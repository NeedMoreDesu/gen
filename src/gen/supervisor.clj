;;; Copyright © 2013 NeedMoreDesu desu@horishniy.org.ua
;;
;;; This program is free software. It comes without any warranty, to
;;; the extent permitted by applicable law. You can redistribute it
;;; and/or modify it under the terms of the Do What The Fuck You Want
;;; To Public License, Version 2, as published by Sam Hocevar. See
;;; http://www.wtfpl.net/ for more details.

(ns gen.supervisor
 (:require clj-time.core)
 (:require [gen process linker-storage loop internals]))

(def ^:dynamic *print-start-reports* true)
(def ^:dynamic *print-stop-reports* true)
(def ^:dynamic *print-crash-reports* true)
(def ^:dynamic *print-fail-reports* true)
(def ^:dynamic *print-restart-reports* true)

(defn rule-create [& {:keys [important? restarts-everyone?
                             max-restarts in-milliseconds
                             args]
                      :or {important? false
                           restarts-everyone? false
                           max-restarts 0}}]
 {:important? important?
  :restarts-everyone? restarts-everyone?
  :max-restarts max-restarts
  :in-milliseconds in-milliseconds
  :args args})

(defn processes [supervisor]
 (:processes (second (gen.process/state-of supervisor))))

(defn process-add [supervisor process & [rule]]
 (gen.process/message
  supervisor
  [:add {:process process
         :rule rule}]))

(defn process-remove [supervisor process]
 (gen.process/message
  supervisor
  [:remove process]))

(letfn [(rule? [arg]
         (and
          arg
          (map? arg)
          (some #(= (:important? arg) %) [true false])
          (some #(= (:restarts-everyone? arg) %) [true false])
          (or
           (not (:max-restarts arg))
           (integer? (:max-restarts arg)))
          (or
           (not (:in-milliseconds arg))
           (integer? (:in-milliseconds arg)))))

        (queue-create []
         (clojure.lang.PersistentQueue/EMPTY))

        (queue-add-timestamp [queue]
         (conj queue (clj-time.core/now)))

        (queue-pop-old-timestamps [queue miliseconds]
         (if miliseconds
          (let [now (clj-time.core/now)]
           (loop [queue queue]
            (if (and
                 (not (empty? queue))
                 (<
                  miliseconds
                  (clj-time.core/in-msecs
                   (clj-time.core/interval
                    (first queue)
                    now))))
             (recur (pop queue))
             queue)))
          queue))

        (queue-check [queue max-restarts in-milliseconds]
         (let [queue (queue-pop-old-timestamps (queue-add-timestamp queue) in-milliseconds)]
          (if (<= (count queue) max-restarts)
           [:ok queue]
           [:fail queue])))

        (analyze-dead-process [process rule queue]
         (if (:max-restarts rule)
          (let [[response queue] (queue-check
                                  queue
                                  (:max-restarts rule)
                                  (:in-milliseconds rule))]
           (if (= response :ok)
            [:restart process rule queue]
            [:fail process rule queue]))
          [:restart process rule queue]))

        (update-state [state restarted failed process doom]
         (update-failed (update-restarted state restarted process doom) failed process))

        (update-failed [state failed self]
         (if (and
              (seq failed)
              *print-fail-reports*)
          (println "== In supervisor" self "=="))
         (loop [processes (:processes state)
                rules (:rules state)
                timers (:timers state)
                failed failed]
          (if (seq failed)
           (let [[_ process rule queue] (first failed)]
            (if *print-fail-reports*
             (do
              (if (:important? rule)
               (println "== IMPORTANT PROCESS DEATH =="))
              (println "|= FAIL:" process "=|")
              (println "|- with thread" (gen.process/get-thread process) "-|")
              (println
               "|-" "max number of restarts" (:max-restarts rule) "reached" "in"
               (clj-time.core/in-msecs
                (clj-time.core/interval
                 (first queue)
                 (clj-time.core/now)))
               "milliseconds" "-|")))
            (recur
             (disj processes process)
             (dissoc rules process)
             (dissoc timers process)
             (rest failed)))
           {:processes processes
            :rules rules
            :timers timers})))

        (update-restarted [state restarted self doom]
         (if (and
              (seq restarted)
              *print-restart-reports*)
          (do
           (println "== In supervisor" self "==")
           (if doom
            (do
             (println "-- terminated processes are not restarting --")
             (println "-- because supervisor is terminating --")))))
         (loop [processes (:processes state)
                rules (:rules state)
                timers (:timers state)
                restarted restarted]
          (if (seq restarted)
           (let [[_ process rule queue] (first restarted)]
            (if (or
                 *print-restart-reports*
                 (and *print-fail-reports* doom))
             (do
              (if doom
               (println "|= RIP:" process "=|")
               (println "|= restart:" process "=|"))
              (println "|- with thread" (gen.process/get-thread process) "-|")
              (if (not doom)
               (if (:max-restarts rule)
                (println
                 "|-" (count queue) "restarts"
                 (str "(max " (:max-restarts rule) ")")
                 "in"
                 (clj-time.core/in-msecs
                  (clj-time.core/interval
                   (first queue)
                   (clj-time.core/now)))
                 "milliseconds" "-|")
                (println "|- process restarts infinitely -|")))))
            (if (not doom)
             @(gen.process/restart-link process :supervisor-restart (:args rule)))
            (if doom
             (recur
              (disj processes process)
              (dissoc rules process)
              (dissoc timers process)
              (rest restarted))
             (recur
              processes
              rules
              (assoc timers process queue)
              (rest restarted))))
           {:processes processes
            :rules rules
            :timers timers})))]
 (let [action-handlers
       {:add
        (bound-fn [{:keys [process rule]} processes rules timers self]
         (if rule (assert (rule? rule)))
         (if (gen.process/alive? process)
          (if *print-start-reports*
           (do
            (println "== In supervisor" self "==")
            (println "|=" "ADDED" process "=|")))
          (do
           @(gen.process/start-link process (:args rule))
           (if *print-start-reports*
            (do
             (println "== In supervisor" self "==")
             (println "|=" "STARTED" process "=|")))))
         {:processes (conj processes process)
          :rules (if rule (assoc rules process rule))
          :timers timers})
        :remove
        (bound-fn [process processes rules timers self]
         (if (not (get processes process))
          {:processes processes :rules rules :timers timers}
          (do
           @(gen.process/stop process :supervisor-stop)
           (if *print-stop-reports*
            (do
             (println "== In supervisor" self "==")
             (println "|=" "STOPPED" process "=|")))
           {:processes (disj processes process)
            :rules (dissoc rules process)
            :timers (dissoc timers process)})))}]
  (defn create [& {:keys [processes rules linker default-rule name]
                   :or {processes #{}
                        rules {}
                        default-rule (rule-create)
                        linker gen.linker-storage/*linker*}
                   :as args}]
   (assert (set? processes))
   (assert (map? rules))
   (doall (map (bound-fn [[_ rule#]] (assert (rule? rule#))) rules))
   (gen.loop/create
    :name name
    :type :supervisor
    :init
    (bound-fn [process args]
     (if *print-start-reports*
      (println "==" "STARTED supervisor" process "=="))
     (doall (map (bound-fn [process]
                  @(gen.process/start-link process (:args (get rules process)))
                  (if *print-start-reports*
                   (println "|=" "STARTED" process "=|")))
             processes))
     [:run {:processes (set processes)
            :rules rules
            :timers {}}])
    :body
    (bound-fn [{:keys [processes rules timers]
          :as state}
         process]
     (Thread/sleep gen.internals/*sleep-interval*)
     (if (gen.process/queue-empty? process)
      (let [dead-processes
            (filter #(do
                      (Thread/sleep gen.internals/*sleep-interval*)
                      (not (gen.process/alive? %1))) processes)
            analized
            (map
             #(analyze-dead-process
               %1
               (or (get rules %1) default-rule)
               (or (get timers %1) (queue-create)))
             dead-processes)
            restart-everyone?
            (some #(:restarts-everyone? (nth % 2)) analized)
            analized
            (if restart-everyone?
             (map
              #(analyze-dead-process
                %1
                (or (get rules %1) default-rule)
                (or (get timers %1) (queue-create)))
              processes)
             analized)
            those-cannot-be-restarted
            (filter #(= :fail (first %)) analized)
            those-can-be-restarted
            (filter #(= :restart (first %)) analized)
            no-hope?
            (some #(:important? (nth % 2)) those-cannot-be-restarted)]
       (if (and
            restart-everyone?
            (or
             *print-restart-reports*
             (and
              *print-fail-reports*
              (seq those-cannot-be-restarted))
             (and
              *print-crash-reports*
              no-hope?)))
        (println "==" "restart all processes" "=="))
       (let [state
             (update-state
              state
              those-can-be-restarted
              those-cannot-be-restarted
              process
              no-hope?)]
        (if no-hope?
         [:self-term state]
         [:run state])))
      (gen.process/receive [[action data] process]
       [:run ((get action-handlers action) data processes rules timers process)])))
    :terminate
    (bound-fn [reason {:keys [processes] :as state} process]
     (if *print-crash-reports*
      (do
       (println "== supervisor" process "==")
       (println "-- with thread" (gen.process/get-thread process) "--")
       (println "--" "CRASHED" "--")))
     (doall (map
             (bound-fn [process]
              (do
               @(gen.process/stop process :supervisor-terminate)
               (if *print-fail-reports*
                (do
                 (println "|- terminated" process "-|")
                 (println "|- with thread" (gen.process/get-thread process) "-|")))))
             processes))
     [:terminated reason])
    :timeout nil
    :linker linker))))
