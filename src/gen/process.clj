(ns gen.process
 (:import java.util.concurrent.TimeoutException)
 (:require [gen linker-storage internals])
 (:require [clojure.string :as str])
 (:use [gen.internals :only [with-timeout]]))

(def ^:dynamic *stacktraces* true)
(def ^:dynamic *stacktrace-max-length* nil)

(declare to-string)

(deftype GenProcess [data]
 Object
 (toString [self]
  (to-string self)))

(defn create
 [& {:keys [thread start stop-timeout queue linker links return-promise stop-promise state-getter type]
     :or {type :default
          queue (clojure.lang.PersistentQueue/EMPTY)
          links #{}
          stop-timeout 500
          start (fn [process stop-promise] nil)
          stop (fn [reason process] nil)}}]
 (let [process (GenProcess. {:type type
                             :thread (ref thread)
                             :return-promise (ref return-promise)
                             :stop-promise (ref stop-promise)
                             :state-getter state-getter
                             :start start
                             :stop-timeout stop-timeout
                             :queue (ref queue)
                             :linker linker
                             :links (ref links)})]
  process))

(defn process? [arg]
 (= (type arg) gen.process.GenProcess))

(defn alive? [process]
 (let [thread @(:thread (.data process))]
  (if (= (type thread) java.lang.Thread)
   (.isAlive thread)
   false)))

(defn get-thread [process]
 @(:thread (.data process)))

(defn add-to-linker
 ([process]
  (if (:linker (.data process))
   (gen.linker-storage/storage-add (:linker (.data process)) @(:thread (.data process)) process)))
 ([process linker]
  (gen.linker-storage/storage-add linker @(:thread (.data process)) process)))

(defn result-of [process]
 (let [promise @(:return-promise (.data process))]
  (if promise
   (if (realized? promise)
    (if (instance? Exception @promise)
     [:fail @promise]
     [:result @promise])
    (if (alive? process)
     [:fail :still-running]
     [:fail :!not-running-not-realized]))
   [:fail :no-result-promise])))

(defn state-of [process]
 (let [getter (:state-getter (.data process))]
  (if getter
   [:result (getter)]
   [:fail :no-state-getter])))

(defn stacktrace-of [process & [max-length]]
 (let [[test exception] (result-of process)]
  (if (and
       (= test :fail)
       (instance? Exception exception))
   (clojure.stacktrace/print-stack-trace
    exception
    max-length))))

(letfn [(change-thread [process thread]
         (dosync (ref-set (:thread (.data process)) thread))
         (add-to-linker process)
         true)
        (change-return-promise [process promise]
         (dosync (ref-set (:return-promise (.data process)) promise))
         true)
        (change-stop-promise [process promise]
         (dosync (ref-set (:stop-promise (.data process)) promise))
         true)]
 (defn start [process args]
  (future
   (if (alive? process)
    [:fail :already-alive]
    (do
     (change-stop-promise process nil)
     (change-return-promise process nil)
     (let [stop-promise (promise)
           starter (((.data process) :start) process args stop-promise)]
      (if (fn? starter)
       (let [return-promise (promise)
             thread (Thread.
                     (bound-fn []
                      (deliver return-promise
                       (try
                        (starter)
                        (catch Exception e
                         (if *stacktraces*
                          (do
                           (println "=== In process" process "===")
                           (println "=== In thread" @(:thread (.data process)) "===")
                           (clojure.stacktrace/print-stack-trace
                            e
                            *stacktrace-max-length*)
                           (flush)))
                         e)))))]
        (if
         (dosync
          (and
           (not @(:stop-promise (.data process)))
           (not @(:return-promise (.data process)))
           (change-stop-promise process stop-promise)
           (change-return-promise process return-promise)
           true))
         (do
          (.start thread)
          (change-thread process thread)
          [:started thread])
         [:fail :concurrency-race]))
       [:fail :bad-starter])))))))

(defn get-links [process]
 @((.data process) :links))

(defn add-links [process links]
 (doall (map #(assert (or
                       (= (type %1) Thread)
                       (process? %1))) links))
 (dosync (alter (:links (.data process)) #(apply conj (set %1) (set %2)) links)))

(defn set-links [process links]
 (doall (map #(assert (or
                       (= (type %1) Thread)
                       (process? %1))) links))
 (dosync (ref-set (:links (.data process)) (set links))))

(defn have-dead-links [process]
 (if
  (some
   (fn [arg]
    (cond
     (= (type arg) java.lang.Thread)
     (not (.isAlive arg))
     (process? arg)
     (alive? arg)))
   (get-links process))
  true
  false))

(defn kill [process]
 (let [thread @(:thread (.data process))]
  (cond
   (= (type thread) java.lang.Thread)
   (do
    (.stop thread)
    (deliver (:return-promise (.data process)) :killed)
    true)
   true
   false)))

(defn stop [process reason]
 (future
  (try
   (if (not (alive? process))
    [:fail :not-alive]
    (let [st (:stop-timeout (.data process))
          thread (get-thread process)
          stop-promise @(:stop-promise (.data process))]
     (if (integer? st)
      (do
       (deliver stop-promise reason)
       (with-timeout st
        (while (.isAlive thread)
         (Thread/sleep gen.internals/*sleep-interval*))))
      (do
       (deliver stop-promise reason)
       (while (.isAlive thread)
        (Thread/sleep gen.internals/*sleep-interval*))))
     [:terminated :ok]))
   (catch TimeoutException e
    (if (kill process)
     [:killed :timeout]
     [:fail :!kill-failure]))
   (catch Exception e
    (if (not (alive? process))
     [:terminated :exception]
     (if (kill process)
      [:killed :exception]
      [:fail :!kill-failure]))))))

(defn start-link [process args]
 (set-links process [(Thread/currentThread)])
 (start process args))

(defn restart [process reason args]
 (future
  @(stop process reason)
  @(start process args)))

(defn restart-link [process reason args]
 (future
  @(stop process reason)
  @(start-link process args)))

(defn message [process message]
 (dosync (alter (:queue (.data process)) conj message))
 (if (alive? process)
  true
  false))

(defn queue-empty? [process]
 (empty? @(:queue (.data process))))

(defn queue-top [process]
 (first @(get (.data process) :queue)))

(defn queue-pop [process]
 (let [top (queue-top process)]
  (dosync (alter (:queue (.data process)) pop))
  top))

(defn queue-pop-blocking [process]
 (while (queue-empty? process)
  (Thread/sleep gen.internals/*sleep-interval*))
 (queue-pop process))

(defn queue-size [process]
 (count @(:queue (.data process))))

(defn queue-flush [process]
 (dosync (alter (:queue (.data process)) #(remove (fn [& args] true) %1)))
 nil)

(defn self [& {:keys [storage]
               :or {storage gen.linker-storage/*linker*}}]
 (gen.linker-storage/storage-get-process-by-thread storage (Thread/currentThread)))

(defmacro receive
 "ARG is either
* variable.
* [variable process].
When queue is empty, blocking."
 [[variable process] & BODY]
 `(if (not (queue-empty? ~process))
   (let [~variable (queue-pop ~process)]
    ~@BODY)))

(letfn [(p [arg] (print-str arg))
        (status-string [process]
         (str
          " status: "
          (if (alive? process)
           (str "alive"
            (let [linker (:linker (.data process))
                  thread @(:thread (.data process))]
             (and
              linker thread
              (gen.linker-storage/storage-get-process-by-thread linker thread)
              "-linked")))
           "dead")))
        (queue-string [process]
         (if (not (queue-empty? process))
          (str ", messages: " (queue-size process))))
        (result-string [process]
         (let [response (result-of process)]
          (if (not (= [:fail :still-running] response))
           (str ", " response))))]
 (defn to-string [process]
  (case (:type (.data process))
   :linker
   (str "Linker"
    (status-string process)
    (queue-string process)
    (let [[r linker-storage] (state-of process)]
     (if (and (= r :result) linker-storage)
      (str ", " linker-storage)))
    (result-string process))
   :loop
   (str "Loop"
    (status-string process)
    (queue-string process)
    (let [[r state] (state-of process)]
     (if (= r :result)
      (str ", state: " (p state))))
    (result-string process))
   :server
   (str "Server"
    (status-string process)
    (queue-string process)
    (let [[r {:keys [state last-message]}] (state-of process)]
     (if (= r :result)
      (str ", state: " (p state) ", last-message: " (p (first last-message)))))
    (result-string process))
   :supervisor
   (str "Supervisor"
    (status-string process)
    (queue-string process)
    (let [[r {processes :processes}] (state-of process)]
     (if (= r :result)
      (str
       ", processes: ["
       (str/join " "
        (map
         (fn[process]
          (p process))
         processes))
       "], count " (count processes))))
    (result-string process))
   (str
    (:type (.data process))
    (status-string process)
    (if (not (queue-empty? process)) (str ", messages: " (queue-size process)))))))
