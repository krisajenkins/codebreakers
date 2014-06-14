(ns codebreakers.lang)

(defmacro in-thread
  "Run the body in a thread. Isolates you from interop."
  [& body]
  `(.start (Thread. (fn [] ~@body))))
