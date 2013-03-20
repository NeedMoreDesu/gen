(ns gen.linker
 (:require [gen loop internals])
 (:use gen.linker-storage))

(def ^:dynamic *print-dead-link-kills* true)

(defn processes [linker]
 (gen.linker-storage/storage-get-processes
  (second (gen.process/state-of linker))))

(letfn
 [(check-processes
   ([linker self]
    (Thread/sleep gen.internals/*sleep-interval*)
    (check-processes linker (seq (storage-get-processes linker)) self))
   ([linker list self]
    (if (seq list)
     (let [[thread process] (first list)]
      (if (not (.isAlive thread))
       (storage-remove linker thread)
       (if (gen.process/have-dead-links process)
        (do
         (if (= process self)
          (gen.process/stop process :link)
          @(gen.process/stop process :link))
         (storage-remove linker thread)
         (if *print-dead-link-kills*
          (do
           (println "==" "in linker" self "==")
           (println "--" "terminated" process "--")
           (println "--" "with thread" (gen.process/get-thread process) "--"))))))
      (Thread/sleep gen.internals/*sleep-interval*)
      (recur linker (rest list) self))
    false)))]
 (defn create
  [& {:keys [linker]
      :or {linker *linker*}
      :as args}]
  (assert (storage? linker))
  (let [process (gen.loop/create
                 :init (fn [process args] [:run linker])
                 :body (fn [linker process]
                        (check-processes linker process)
                        [:run linker])
                 :terminate (fn [reason linker process]
                             (storage-remove linker (gen.process/get-thread process))
                             [:terminate reason])
                 :timeout nil
                 :linker linker
                 :type :linker)]
   process)))
