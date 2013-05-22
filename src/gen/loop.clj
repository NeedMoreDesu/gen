;;; Copyright © 2013 NeedMoreDesu desu@horishniy.org.ua
;;
;;; This program is free software. It comes without any warranty, to
;;; the extent permitted by applicable law. You can redistribute it
;;; and/or modify it under the terms of the Do What The Fuck You Want
;;; To Public License, Version 2, as published by Sam Hocevar. See
;;; http://www.wtfpl.net/ for more details.

(ns gen.loop
 (:require [gen process linker-storage]))

(defn create [& {:keys [init body terminate timeout linker state-getter type name]
                 :or {timeout 500
                      init (fn [process args] [:run args])
                      terminate (fn [reason state process] [:terminated reason])
                      body (fn [state process] [:stop state])
                      state-getter (fn [state] state)
                      type :loop
                      linker gen.linker-storage/*linker*}
                 :as args}]
 (assert (fn? body))
 (assert (fn? init))
 (assert (fn? terminate))
 (assert (fn? state-getter))
 (let [state# (atom nil)
       start-fn (bound-fn [process args stop-promise]
                 (bound-fn []
                  (loop [[command state] (init process args)]
                   (assert (#{:run :stop :self-term} command))
                   (reset! state# state)
                   (cond
                    (realized? stop-promise) (terminate @stop-promise state process)
                    (= command :stop) [:stopped state]
                    (= command :self-term) (terminate :self-term state process)
                    (= command :run) (recur (body state process))))))
       state-getter-fn (bound-fn [] (state-getter @state#))
       self-process (gen.process/create
                     :type type
                     :start start-fn
                     :stop-timeout timeout
                     :state-getter state-getter-fn
                     :linker linker
                     :name name)]
  self-process))
