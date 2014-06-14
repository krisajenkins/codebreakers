(ns codebreakers.net
  (:require [clojure.core.async :as async :refer [chan go alts! go go-loop put!]]
            [clojure.core.match :refer [match]]
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

(extend-type OutputStream
  IWritable
  (write-to [this str] (.write this (.getBytes str "UTF-8"))))

(extend-type Socket
  IConnection
  (open? [this]
    (not (.isClosed this)))
  IWritable
  (write-to [this str] (write-to (.getOutputStream this) str))
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
    [socket-name from-client to-client]
  IConnection
  (close! [this]
    (async/close! (this :from-client))
    (async/close! (this :to-client))))

(defn make-server
  "Bind a server to the given port. Return two channels {:input c :output c}."
  [port]
  (let [server-socket (ServerSocket. port)
        new-connections (chan 5)]

    (in-thread
     (while (not (.isClosed server-socket))
       (try
         (let [socket (.accept server-socket)
               connection (map->ClientConnection {:socket-name (.. socket getInetAddress getHostAddress)
                                                  :from-client (chan 5)
                                                  :to-client (chan 5)})]
           (in-thread
            (go
              ;; Write to Client
              (while (open? socket)
                (let [v (<! (:to-client connection))]
                  (write-to socket v)))
              (close! connection))

            (go
              ;; Read from Client
              (while (open? socket)
                (let [v (read-from socket)]
                  (>! (:from-client connection) v)))
              (close! connection))

            (put! new-connections connection)

            :ok))
         (catch SocketException e (println e)))))
    new-connections))
