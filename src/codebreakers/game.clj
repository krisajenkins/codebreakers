(ns codebreakers.game
  (:require [clojure.edn :as edn]
            [clojure.core.async :as async :refer [chan go alts! <! <!! >! >!! go-loop put! timeout]]
            [clojure.core.match :refer [match]]
            [schema.macros :as sm]
            [schema.core :as s]
            [clojure.pprint :refer [pprint]]
            [codebreakers.net :as net]
            [codebreakers.cypher :refer [random-cypher]]
            [codebreakers.messages :as msg :refer [map->Encrypted]])
  (:import [codebreakers.messages Join Guess Correct Incorrect Encrypted Tick]))

(defn random-secret
  []
  (-> "doc/secrets.txt"
      slurp
      (clojure.string/split #"\n")
      shuffle
      first))

(defprotocol CypherGame
  (join [this peer team-name language-name])
  (increment-score [this peer])
  (decrement-score [this peer])
  (current-encrypted-message [this])
  (new-game [this]))

(sm/defrecord GameState
    [peers :- [{s/Str {:score s/Int
                       :team-name s/Str
                       :language-name s/Str}}]
     secret :- s/Str
     encryption-function :- (s/pred fn? "Function")])

(extend-type GameState
  CypherGame
  (join
    [this peer team-name language-name]
    (update-in this [:peers peer] merge {:team-name team-name
                                         :language-name language-name}))
  (increment-score
    [this peer]
    (update-in this [:peers peer :score] inc))
  (decrement-score
    [this peer]
    (update-in this [:peers peer :score] dec))
  (new-game
    [this]
    (merge this {:secret (random-secret)
                 :encryption-function (random-cypher)}))
  (current-encrypted-message
    [this]
    (apply str ((:encryption-function this)
                (:secret this)))))

(defprotocol InboundGameMessage
  (process [this game-state]
    "Returns a [new-game-state response-message] pair."))

(extend-protocol InboundGameMessage
  Tick
  (process [this game-state]
    (let [new-game-state (new-game game-state)
          broadcast-message (map->Encrypted {:message (current-encrypted-message new-game-state)})]
      [new-game-state broadcast-message]))

  Join
  (process [this game-state]
    [(join game-state
           (:peer this)
           (:team-name this)
           (:language-name this))
     (Encrypted. (:peer this)
                 (current-encrypted-message game-state))])

  Guess
  (process [this game-state]
    (if (= (:secret game-state)
           (:secret this))
      [(increment-score game-state (:peer this)) (Correct. (:peer this))]
      [(decrement-score game-state (:peer this)) (Incorrect. (:peer this))])))

(def instructions
  "The Cypher Game
----
Periodically, the enemy will send encrypted messages. Your job is to
decode them before the next one arrives.

Points will be awarded for a correct answer.
Points will be deducted for an incorrect answer.

Every message is a single line, ending in \n.

The enemy uses any/all of the following encryption methods, at random:

* Reverse.
* rot(n), where rot(1) means a->b, b->c, ... z->a.
   (Numbers, whitespace & punctuation are unchanged.)
* Even letters first (so letters 0,2,4,1,3,5 eg. 'hacking' becomes 'hcigakn').

Bonus points will be issued to anyone who successfully decrypts all
the messages and builds the foundations of Computer Science while
they're at it.")
