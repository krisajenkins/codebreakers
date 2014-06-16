(ns codebreakers.net
  (:require [clojure.core.async :as async :refer [<! >! chan go alts! go go-loop put!]]
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

(defrecord ClientConnection
    [socket-name
     from-client
     to-client]
  IConnection
  (close! [this]
    (async/close! (this :from-client))
    (async/close! (this :to-client))))

(defprotocol IServer
  (broadcast! [this])
  (connection-chan [this]))

(sm/defn make-server
  "Bind a server to the given port. Return two channels {:input c :output c}."
  [port :- s/Int]
  (printf "Starting server on port %d\n" port)
  (let [server-socket (ServerSocket. port)
        new-connections (chan 5)]

    (in-thread
     (while (open? server-socket)
       (try
         (printf "Awaiting connection...\n")
         (let [socket (.accept server-socket)]
           (let [connection (map->ClientConnection {:socket-name (.. socket getInetAddress getHostAddress)
                                                    :from-client (chan 5)
                                                    :to-client (chan 5)})]
             (printf "Connection %s\n" connection)
             (flush)
             (in-thread
              (go
                ;; Write to Client
                (while (open? socket)
                  (let [v (<! (:to-client connection))]
                    (printf "Write %s\n" v)
                    (flush)
                    (write-to socket v)))
                (close! connection))

              (go
                ;; Read from Client
                (while (open? socket)
                  (when-let [v (read-from socket)]
                    (printf "Read %s\n" v)
                    (flush)
                    (>! (:from-client connection) v)))
                (close! connection))

              (put! new-connections connection)

              :ok)))
         (catch SocketException e (println e)))))

    (reify
      IServer
      (connection-chan [this] new-connections))))
