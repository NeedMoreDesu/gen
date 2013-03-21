;;; Copyright © 2013 NeedMoreDesu desu@horishniy.org.ua
;;
;;; This program is free software. It comes without any warranty, to
;;; the extent permitted by applicable law. You can redistribute it
;;; and/or modify it under the terms of the Do What The Fuck You Want
;;; To Public License, Version 2, as published by Sam Hocevar. See
;;; http://www.wtfpl.net/ for more details.

(ns gen.core
 (:use [gen
        [process :only [*stacktraces* *stacktrace-max-length*]]
        [linker :only [*print-dead-link-kills*]]
        [supervisor :only [*print-start-reports*
                           *print-stop-reports*
                           *print-crash-reports*
                           *print-fail-reports*
                           *print-restart-reports*]]]))

(defmacro set-stacktrace-max-length [length & BODY]
 `(binding [*stacktrace-max-length* length]
   ~@BODY))

(defmacro silent-messages [[stacktrace linker supervisor] & BODY]
 `(binding [*stacktraces* ~(not stacktrace)
            *print-dead-link-kills* ~(not linker)
            *print-start-reports* ~(not supervisor)
            *print-stop-reports* ~(not supervisor)
            *print-crash-reports* ~(not supervisor)
            *print-fail-reports* ~(not supervisor)
            *print-restart-reports* ~(not supervisor)]
   ~@BODY))

(defmacro silent-all-messages [& BODY]
 `(silent-messages [true true true] ~@BODY))
