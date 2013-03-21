;;; Copyright © 2013 NeedMoreDesu desu@horishniy.org.ua
;;
;;; This program is free software. It comes without any warranty, to
;;; the extent permitted by applicable law. You can redistribute it
;;; and/or modify it under the terms of the Do What The Fuck You Want
;;; To Public License, Version 2, as published by Sam Hocevar. See
;;; http://www.wtfpl.net/ for more details.

(ns gen.creator)

(defn new-context []
 (ref
  {:counter 0
   :pids #{}}))

(defn new-id [creator-context]
 (let [id (:counter @creator-context)]
  (dosync (alter creator-context assoc :counter (inc id)))
  id))

;; (defn start [creator-context]
 
