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
 
