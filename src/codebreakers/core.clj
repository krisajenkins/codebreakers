(ns codebreakers.core
  (:require [clojure.core.async :as async :refer [<! >! chan go alts! go go-loop put! filter< timeout go-loop]]
            [schema.core]
            [codebreakers.game :as game]
            [codebreakers.net :as net])
  (:import [codebreakers.net MessageToClient MessageFromClient]))

(schema.core/set-fn-validation! true)

(defn -main
  []
  (let [server (net/make-server 5558)]
    (go-loop []
      (when-let [v (<! (:from-clients server))]
        (println "Got message: " v)
        (let [response
              (net/map->MessageToClient {:peer (:peer v)
                                         :message (format "ACK: %s" (:message v))}) ]
          (println "Sending response: " response)
          (>! (:to-clients server) response)
          (when (zero? (rand-int 3))
            (>! (:to-clients server)
                (net/map->MessageToClient {:peer :all
                                           :message "BROADCAST!"}))))
        (recur)))))

;;; I will create a server, a game state, a doorman, and route messages.
