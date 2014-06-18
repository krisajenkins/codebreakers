(ns codebreakers.core
  (:require [clojure.core.async :as async :refer [<! >! chan go alts! go go-loop put! filter< timeout go-loop]]
            [schema.core]
            [clojure.edn :as edn]
            [codebreakers.game :as game]
            [codebreakers.messages :as msg]
            [quil.core :as q]
            [codebreakers.net :as net]
            [clojure.pprint :refer [pprint]])
  (:import [codebreakers.net MessageToClient MessageFromClient]
           [codebreakers.messages Tick]))

(schema.core/set-fn-validation! true)

(def system (atom {}) )

(defn setup []
  (q/smooth)
  (q/text-font (q/create-font "Courier New-Bold" 22 true))
  (q/frame-rate 1)
  (q/background 200)
  (q/set-state! :system system))

(defn draw
  []
  ;; Background
  (q/fill 0)
  (q/rect 0 0 (q/width) (q/height))
  (q/fill 255)
  (q/stroke 0 255 0)
  (q/fill 0 255 0)

  (let [sys (deref (:system (q/state)))
        game-state (:game-state sys)]

    (q/text (format "Address: %s/%d" (net/my-ip) (:port sys)) 30 30)
    (q/text (format "New message every: %dms" (:tick-speed sys)) 30 60)

    (q/text "Current message"  30 100)
    (q/text (game/current-encrypted-message game-state) 30 130)

    (dorun
     (map-indexed (fn [i [address state]]
                    (q/text (format "%s (%s) : %d"
                                    (:team-name state)
                                    (:language-name state)
                                    (:score state)) 30 (+ 190 (* 30 i))))
                  (:peers game-state)))))

(defn -main
  ([] (-main "8000"))

  ([port-string]
     (q/defsketch game
       :title "Cypher Game"
       :size [800 600]
       :setup setup
       :draw draw)
     (let [port (edn/read-string port-string)
           server (net/make-server port)]
       (reset! system {:server server
                       :game-state (game/new-game (game/map->GameState {:peers {}}))
                       :port port
                       :tick-speed 1000
                       :joining {}
                       :joined #{}})

       (go
         (while true
           (pprint [:game-state (@system :game-state)])

           (let [t (timeout (@system :tick-speed))
                 [v c] (alts! [t (:from-clients server)])]
             (if (= c t)
               (let [[new-game-state message] (game/process (msg/->Tick) (@system :game-state))]
                 (pprint [:tick-result message])
                 (>! (:to-clients server) message)
                 (swap! system assoc :game-state new-game-state))
               (do (printf "-----\n%s\n-----\n" [:message v])
                   (cond
                    (contains? (@system :joined) (:peer v))

                    (let [a-guess (msg/map->Guess {:peer (:peer v)
                                                   :secret (:message v)})]
                      (pprint [:processing-a-guess a-guess])
                      (let [[new-game-state message] (game/process a-guess (@system :game-state))]
                        (pprint [:guess-result message])
                        (>! (:to-clients server) message)
                        (swap! system assoc :game-state new-game-state)))

                    (contains? (:joining @system) (:peer v))
                    (let [state ((:joining @system) (:peer v))]
                      (pprint [:state state])
                      (if (:team-name state)
                        (let [[new-game-state message] (game/process (msg/map->Join {:peer (:peer v)
                                                                                     :team-name (:team-name state)
                                                                                     :language-name (:message v)})
                                                                     (@system :game-state))]
                          (pprint [:new-joiner message])
                          (>! (:to-clients server) message)
                          (swap! system update-in [:joined] conj (:peer v))
                          (swap! system assoc :game-state new-game-state))
                        (let [message (net/map->MessageToClient {:message "Language name?"
                                                                 :peer (:peer v)})]
                          (pprint [:asking-language message])
                          (>! (:to-clients server) message)
                          (swap! system update-in [:joining] assoc (:peer v) {:team-name (:message v)}))))

                    :else
                    (let [message1 (net/map->MessageToClient {:message game/instructions
                                                              :peer (:peer v)})
                          message2 (net/map->MessageToClient {:message "Team name?"
                                                              :peer (:peer v)})]
                      (>! (:to-clients server) message1)
                      (>! (:to-clients server) message2)
                      (swap! system update-in [:joining] assoc (:peer v) {})))))))))))
