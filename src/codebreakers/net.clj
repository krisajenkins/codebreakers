(ns codebreakers.net
  (:require [clojure.core.async :as async :refer [<! >! chan go alts! go go-loop put! filter< timeout pub sub unsub]]
            [clojure.core.match :refer [match]]
            [schema.macros :as sm]
            [schema.core :as s]
            [codebreakers.lang :refer [in-thread]])
  (:import (java.io BufferedReader InputStreamReader OutputStream)
           (java.net InetAddress ServerSocket Socket SocketException)))

(defn my-ip
  "Return the IP address of the current process."
  []
  (-> (InetAddress/getLocalHost)
      .getHostAddress))

(defprotocol IConnection
  (open? [this])
  (close! [this]))

(defprotocol IWritable
  (write-to [this str]))

(defprotocol IReadable
  (read-from [this]))

(extend-protocol IWritable
  OutputStream
  (write-to [this str] (.write this (.getBytes str "UTF-8")))
  Socket
  (write-to [this str] (write-to (.getOutputStream this) str)))

(extend-type ServerSocket
  IConnection
  (open? [this]
    (not (.isClosed this)))
  (close! [this]
    (.close this)))

(extend-type Socket
  IConnection
  (open? [this]
    (not (.isClosed this)))
  IReadable
  (read-from [this]
    (-> this
        .getInputStream
        InputStreamReader.
        BufferedReader.
        .readLine)))

(defn writeln
  [destination string]
  (write-to destination (str string "\n")))

(sm/defrecord ClientConnection
    [peer :- s/Str
     from-client
     to-client]
  IConnection
  (close! [this]
          (async/close! (this :from-client))
          (async/close! (this :to-client))))

(sm/defrecord MessageToClient
    [message :- s/Str
     peer :- (s/either s/Str (s/enum :broadcast))])

(sm/defrecord MessageFromClient
    [message :- s/Str
     peer :- s/Str])

(defprotocol IServer)

(sm/defn make-server
  "Bind a server to the given port. Return two channels {:input c :output c}."
  [port :- s/Int]
  (printf "Starting server on port %d\n" port)
  (let [server {:server-socket (ServerSocket. port)
                :rate-limit 100
                :to-clients (chan 10)
                :from-clients (chan 10)}
        publication (pub (:to-clients server) :peer)]
    (in-thread
     (while (open? (:server-socket server))
       (printf "Awaiting connection...\n")
       (let [socket (.accept (:server-socket server))
             peer (.. socket getInetAddress getHostAddress)]
         (printf "Connection %s\n" peer)
         (flush)
         (in-thread
          (let [to-client (chan)]
            (try
              (sub publication peer to-client)
              (sub publication :all to-client)
              ;;; Write-to-client loop.
              (go-loop []
                (when-let [v (<! to-client)]
                  (println "Sending to client: " v)
                  (flush)

                  (when (open? socket)

                    (try
                      (write-to socket (format "%s\n" (:message v)))
                      (catch SocketException e
                        (println e)
                        (async/close! to-client)))
                    (recur))))
              ;;; Read-from-client loop.
              (go
                (while (open? socket)
                  (let [t (timeout (:rate-limit server))]
                    (try
                      (when-let [v (read-from socket)]
                        (println "Read: " v)
                        (flush)
                        (>! (:from-clients server)
                            (map->MessageFromClient {:peer peer
                                                     :message v}))
                        (<! t))
                      (catch SocketException e
                        (println e)
                        (async/close! to-client))))))
              ))))))
    server))
