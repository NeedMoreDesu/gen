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
