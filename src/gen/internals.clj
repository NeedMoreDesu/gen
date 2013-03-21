;;; Copyright © 2013 NeedMoreDesu desu@horishniy.org.ua
;;
;;; This program is free software. It comes without any warranty, to
;;; the extent permitted by applicable law. You can redistribute it
;;; and/or modify it under the terms of the Do What The Fuck You Want
;;; To Public License, Version 2, as published by Sam Hocevar. See
;;; http://www.wtfpl.net/ for more details.

(ns gen.internals
 (:import (java.util.concurrent TimeoutException TimeUnit FutureTask)))

(def ^:dynamic *sleep-interval* 1)

(def ^{:doc "Create a map of pretty keywords to ugly TimeUnits"}
  uglify-time-unit
  (into {} (for [[enum aliases] {TimeUnit/NANOSECONDS [:ns :nanoseconds]
                                 TimeUnit/MICROSECONDS [:us :microseconds]
                                 TimeUnit/MILLISECONDS [:ms :milliseconds]
                                 TimeUnit/SECONDS [:s :sec :seconds]}
                 alias aliases]
             {alias enum})))

(defn thunk-timeout
  "Takes a function and an amount of time to wait for thse function to finish
executing. The sandbox can do this for you. unit is any of :ns, :us, :ms,
or :s which correspond to TimeUnit/NANOSECONDS, MICROSECONDS, MILLISECONDS,
and SECONDS respectively."
  ([thunk ms]
     (thunk-timeout thunk ms :ms nil)) ; Default to milliseconds, because that's pretty common.
  ([thunk time unit]
     (thunk-timeout thunk time unit nil))
  ([thunk time unit tg]
     (let [task (FutureTask. thunk)
           thr (if tg (Thread. tg task) (Thread. task))]
       (try
         (.start thr)
         (.get task time (or (uglify-time-unit unit) unit))
         (catch TimeoutException e
           (.cancel task true)
           (.stop thr)
           (throw (TimeoutException. "Execution timed out.")))
         (catch Exception e
           (.cancel task true)
           (.stop thr)
           (throw e))
         (finally (when tg (.stop tg)))))))

(defmacro with-timeout [time & body]
  `(thunk-timeout (fn [] ~@body) ~time))
