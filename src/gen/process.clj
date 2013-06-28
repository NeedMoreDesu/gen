;;; Copyright © 2013 NeedMoreDesu desu@horishniy.org.ua
;;
;;; This program is free software. It comes without any warranty, to
;;; the extent permitted by applicable law. You can redistribute it
;;; and/or modify it under the terms of the Do What The Fuck You Want
;;; To Public License, Version 2, as published by Sam Hocevar. See
;;; http://www.wtfpl.net/ for more details.

(ns gen.process
 (:import java.util.concurrent.TimeoutException)
 (:require [gen linker-storage internals])
 (:require [clojure stacktrace [string :as str]])
 (:use [gen.internals :only [with-timeout]])
 (:use [slingshot.slingshot]))

(def ^:dynamic *stacktraces* true)
(def ^:dynamic *stacktrace-max-length* nil)

(defn create
 [& {:keys [name thread start stop-timeout message-queue linker links return-promise stop-promise state-getter receive-promise-queue type]
     :or {type :default
          message-queue (clojure.lang.PersistentQueue/EMPTY)
          receive-promise-queue (clojure.lang.PersistentQueue/EMPTY)
          links #{}
          stop-timeout 500
          start (fn [process args stop-promise] nil)}}]
 (let [process (with-meta {:name name
                           :type type
                           :thread (ref thread)
                           :return-promise (ref return-promise)
                           :stop-promise (ref stop-promise)
                           :state-getter state-getter
                           :start start
                           :stop-timeout stop-timeout
                           :message-queue (ref message-queue)
                           :receive-promise-queue (ref receive-promise-queue)
                           :linker linker
                           :links (ref links)}
                {:type ::process})]
  process))

(defn process? [arg]
 (= (type arg) ::process))

(defn alive? [process]
 (let [thread @(:thread process)]
  (if (= (type thread) java.lang.Thread)
   (.isAlive thread)
   false)))

(defn get-thread [process]
 @(:thread process))

(defn add-to-linker
 ([process]
  (if (:linker process)
   (gen.linker-storage/storage-add (:linker process) @(:thread process) process)))
 ([process linker]
  (gen.linker-storage/storage-add linker @(:thread process) process)))

(defn result-of [process]
 (let [promise @(:return-promise process)]
  (if promise
   (if (realized? promise)
    (if (instance? Exception @promise)
     [:fail @promise]
     [:result @promise])
    (if (alive? process)
     [:fail :still-running]
     [:fail :error!not-running-not-realized]))
   [:fail :no-result-promise])))

(defn state-of [process]
 (let [getter (:state-getter process)]
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
         (dosync (ref-set (:thread process) thread))
         (add-to-linker process)
         true)
        (change-return-promise [process promise]
         (dosync (ref-set (:return-promise process) promise))
         true)
        (change-stop-promise [process promise]
         (dosync (ref-set (:stop-promise process) promise))
         true)]
 (defn start [process args]
  (future
   (if (alive? process)
    [:fail :already-alive]
    (do
     (change-stop-promise process nil)
     (change-return-promise process nil)
     (let [stop-promise (promise)
           starter ((process :start) process args stop-promise)]
      (if (fn? starter)
       (let [return-promise (promise)
             thread (Thread.
                     (bound-fn []
                      (deliver return-promise
                       (try+
                        (starter)
                        (catch Object e
                         (if *stacktraces*
                          (do
                           (println "=== In process" process "===")
                           (println "=== In thread" @(:thread process) "===")
                           (clojure.stacktrace/print-stack-trace
                            e
                            *stacktrace-max-length*)
                           (flush)))
                         e)))))]
        (if
         (dosync
          (and
           (not @(:stop-promise process))
           (not @(:return-promise process))
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
 @(process :links))

(defn add-links [process links]
 (doall (map #(assert (or
                       (= (type %1) Thread)
                       (process? %1))) links))
 (dosync (alter (:links process) #(apply conj (set %1) (set %2)) links)))

(defn set-links [process links]
 (doall (map #(assert (or
                       (= (type %1) Thread)
                       (process? %1))) links))
 (dosync (ref-set (:links process) (set links))))

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
 (let [thread @(:thread process)]
  (cond
   (= (type thread) java.lang.Thread)
   (do
    (.stop thread)
    (deliver (:return-promise process) :killed)
    true)
   true
   false)))

(defn stop [process reason]
 (future
  (try+
   (if (not (alive? process))
    [:fail :not-alive]
    (let [st (:stop-timeout process)
          thread (get-thread process)
          stop-promise @(:stop-promise process)]
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
   (catch Object e
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

(defn queue-empty? [process]
 (empty? @(:message-queue process)))

(defn queue-top [process]
 (first @(get process :message-queue)))

(defn queue-pop [process]
 (let [p (promise)]
  (if (queue-empty? process)
   (do
    (dosync (alter (:receive-promise-queue process) conj p))
    p)
   (let [top (queue-top process)]
    (dosync (alter (:message-queue process) pop))
    (deliver p top)
    p))))

(defn queue-size [process]
 (count @(:message-queue process)))

(defn queue-flush [process]
 (dosync (alter (:message-queue process) #(remove (fn [& args] true) %1)))
 (dosync (alter (:receive-promise-queue process) #(remove (fn [& args] true) %1)))
 nil)

(defn self [& {:keys [storage]
               :or {storage gen.linker-storage/*linker*}}]
 (gen.linker-storage/storage-get-process-by-thread storage (Thread/currentThread)))

(defn message-internal [process message]
 (if (empty? @(:receive-promise-queue process))
  (do
   (dosync (alter (:message-queue process) conj message))
   (alive? process))
  (do
   (let [p (first @(:receive-promise-queue process))]
    (deliver p message)
    (dosync (alter (:receive-promise-queue process) pop))
    (alive? process)))))

(defn message
 "If no FROM message, attempt to lookup your process in
default linker-storage by your thread. If it's not there,
from is nil."
 ([to message#]
  (let [from (self)]
   (message from to message#)))
 ([from to message]
  (message-internal to [message from])))

(defmacro receive
 "VARIABLE is variable, which will hold message(or promise, see below).
If FALSE-BODY is given, execute TRUE-BODY only if message
was received now, otherwise execute FALSE-BODY.
If FALSE-BODY is not given, variable will hold promise, that
 will hold message, when it will be received. Use deref."
 ([[variable process] TRUE-BODY FALSE-BODY]
  `(if (not (queue-empty? ~process))
    (let [~variable @(queue-pop ~process)]
     ~TRUE-BODY)
    ~FALSE-BODY))
 ([[variable process] BODY]
  `(let [~variable (queue-pop ~process)]
    ~BODY)))

(def ^:dynamic *print-state* false)
(letfn [(p [arg] (print-str arg))
        (status-string [process]
         (str
          " status: "
          (if (alive? process)
           (str "alive"
            (let [linker (:linker process)
                  thread @(:thread process)]
             (and
              linker thread
              (gen.linker-storage/storage-get-process-by-thread linker thread)
              "-linked")))
           "dead")))
        (queue-string [process]
         (str
          (if (not (queue-empty? process))
           (str ", messages: " (queue-size process)))
          (let [rpq (count @(:receive-promise-queue process))]
           (if (< 0 rpq)
            (str ", message-waiting: " rpq)))))
        (result-string [process]
         (let [response (result-of process)]
          (if (not (= [:fail :still-running] response))
           (str ", " response))))]
 (let [looked-up-processes (atom #{})]
  (defn to-string [process]
   (if (get @looked-up-processes process)
    "#<~recursion~>"
    (str "#<"
     (do
      (reset! looked-up-processes (conj @looked-up-processes process))
      ((fn[a b] a)
       (case (:type process)
        :linker
        (str
         (:name process) " "
         "Linker"
         (status-string process)
         (queue-string process)
         (let [[r linker-storage] (state-of process)]
          (if (and (= r :result) linker-storage)
           (if *print-state* (str ", " linker-storage))))
         (result-string process))
        :loop
        (str
         (:name process) " "
         "Loop"
         (status-string process)
         (queue-string process)
         (let [[r state] (state-of process)]
          (if (= r :result)
           (if *print-state* (str ", state: " (p state)))))
         (result-string process))
        :server
        (str
         (:name process) " "
         "Server"
         (status-string process)
         (queue-string process)
         (let [[r {:keys [state last-message]}] (state-of process)]
          (if (= r :result)
           (str
            (if *print-state* (str ", state: " (p state)) "")
            ", last-message: " (p (first last-message)))))
         (result-string process))
        :supervisor
        (str
         (:name process) " "
         "Supervisor"
         (status-string process)
         (queue-string process)
         (let [[r {processes :processes}] (state-of process)]
          (if (= r :result)
           (str
            (if *print-state*
             (str
              ", processes: ["
              (str/join " "
               (map
                (fn[process]
                 (p process))
                processes)))
             "")
            "], count " (count processes))))
         (result-string process))
        (str
         (:name process) " "
         (:type process)
         (status-string process)
         (if (not (queue-empty? process)) (str ", messages: " (queue-size process)))))
       (reset! looked-up-processes (disj @looked-up-processes process))))
     ">")))))
(defmethod print-method ::process
 [o w]
 (print-simple
  (to-string o)
  w))
