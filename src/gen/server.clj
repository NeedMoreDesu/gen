(ns gen.server
 (:require [gen loop process linker-storage internals]))

(defn message
 ([to message]
  (let [from (gen.process/self)]
   (if (gen.process/process? from)
    (message from to message))))
 ([from to message]
  (gen.process/message to [message from])))

(defn create [& {:keys [init handler terminate timeout linker state-getter type]
                 :or {type :server
                      init (fn [process args] [:run args])
                      handler (fn [message state process] [:run state])
                      linker gen.linker-storage/*linker*}
                 :as args}]
 (assert (fn? init))
 (assert (fn? handler))
 (apply
  gen.loop/create
  :type type
  :init (bound-fn [process args]
         (let [[command state] (init process args)]
          [command {:state state}]))
  :body (bound-fn [{state :state last-message :last-message} process]
         (or
          (gen.process/receive [message process]
           (let [[command state] (handler message state process)]
            [command {:state state :last-message message}]))
          (Thread/sleep gen.internals/*sleep-interval*)
          [:run {:state state :last-message last-message}]))
  (apply concat (dissoc args :init :handler))))

