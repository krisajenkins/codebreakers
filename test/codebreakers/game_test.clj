(ns codebreakers.game-test
  (:require [clojure.test :refer :all]
            [schema.core]
            [clojure.core.async :as async :refer [alts!! <! >! <!! chan go alts! go go-loop put! timeout]]
            [clojure.algo.monads :refer :all]
            [codebreakers.game :refer :all])
  (:import [codebreakers.game GameState]
           [codebreakers.messages Join Encrypted Tick Guess Correct Incorrect]))

(deftest encryption-test
  (is (= "olleH"
         (current-encrypted-message (map->GameState {:peers {}
                                                     :secret "Hello"
                                                     :encryption-function reverse})))))

(deftest join-game-test
  (let [[new-state message] (process (Join. "10.0.0.1"
                                            "Kris' Team"
                                            "Clojure")
                                     (map->GameState {:peers {}
                                                      :secret "Hello"
                                                      :encryption-function reverse}))]
    (is (instance? Encrypted message))
    (is (= "10.0.0.1" (:peer message)))
    (is (= (:message message)
           "olleH"))
    (is (= {"10.0.0.1" {:team-name "Kris' Team"
                        :language-name "Clojure"}}
           (:peers new-state)))))

(deftest new-game-test
  (let [[new-state message] (process (Tick.)
                                     (map->GameState {:peers {}
                                                      :secret "Hello"
                                                      :encryption-function reverse}))]
    (is (instance? Encrypted message))
    (is (nil? (:peer message)))
    (is (seq (:message message)))
    (is (not= (reverse "Hello")
              (:message message)))))

(deftest correct-guess-test
  (let [[new-state message] (process (Guess. "10.0.0.1" "Hello")
                                     (map->GameState {:peers {"10.0.0.1" {:score 0}}
                                                      :secret "Hello"
                                                      :encryption-function reverse}))]
    (is (instance? Correct message))
    (is (= "10.0.0.1" (:peer message)))
    (is (= 1 (-> new-state :peers (get "10.0.0.1") :score)))))

(deftest incorrect-guess-test
  (let [[new-state message] (process (Guess. "10.0.0.1" "Hello")
                                     (map->GameState {:peers {"10.0.0.1" {:score 0}}
                                                      :secret "Goodbye"
                                                      :encryption-function reverse}))]
    (is (instance? Incorrect message))
    (is (= "10.0.0.1" (:peer message)))
    (is (= -1 (-> new-state :peers (get "10.0.0.1") :score)))))
