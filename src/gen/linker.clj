;;; Copyright © 2013 NeedMoreDesu desu@horishniy.org.ua
;;
;;; This program is free software. It comes without any warranty, to
;;; the extent permitted by applicable law. You can redistribute it
;;; and/or modify it under the terms of the Do What The Fuck You Want
;;; To Public License, Version 2, as published by Sam Hocevar. See
;;; http://www.wtfpl.net/ for more details.

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
                 :init (bound-fn [process args] [:run linker])
                 :body (bound-fn [linker process]
                        (check-processes linker process)
                        [:run linker])
                 :terminate (bound-fn [reason linker process]
                             (storage-remove linker (gen.process/get-thread process))
                             [:terminate reason])
                 :timeout nil
                 :linker linker
                 :type :linker)]
   process)))
