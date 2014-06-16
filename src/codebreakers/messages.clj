(ns codebreakers.messages
  (:require [schema.macros :as sm]
            [schema.core :as s]))

(def IP s/Str)

(sm/defrecord Join
    [peer :- IP
     team-name :- s/Str
     language-name :- s/Str])

(sm/defrecord Guess
    [peer :- IP
     secret :- s/Str])

(sm/defrecord Encrypted
    [peer :- IP
     message :- s/Str])

(sm/defrecord Correct
    [peer :- IP])

(sm/defrecord Incorrect
    [peer :- IP])

(sm/defrecord Tick [])
