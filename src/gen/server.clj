;;; Copyright © 2013 NeedMoreDesu desu@horishniy.org.ua
;;
;;; This program is free software. It comes without any warranty, to
;;; the extent permitted by applicable law. You can redistribute it
;;; and/or modify it under the terms of the Do What The Fuck You Want
;;; To Public License, Version 2, as published by Sam Hocevar. See
;;; http://www.wtfpl.net/ for more details.

(ns gen.server
 (:require [gen loop process linker-storage internals]))

(defn create [& {:keys [init handler terminate timeout linker state-getter type name]
                 :or {type :server
                      init (fn [process args] [:run args])
                      terminate (fn [reason state process] [:terminated reason])
                      handler (fn [[message from] state process] [:run state])
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
         (gen.process/receive [message+from process]
          (let [[command state] (handler @message+from state process)]
           [command {:state state :last-message @message+from}])))
  :terminate (fn [reason {state :state last-message :last-message} process]
              (terminate reason state process))
  (apply concat (dissoc args :init :handler :type :terminate))))

