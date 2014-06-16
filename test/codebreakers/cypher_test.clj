(ns codebreakers.cypher-test
  (:require [expectations :refer :all]
            [codebreakers.cypher :refer :all]))

;;; Rot 13
(expect "Hello! It's Good. 0"
        (rot 0 "Hello! It's Good. 0"))

(expect "Hello! It's Good. 26"
        (rot 26 "Hello! It's Good. 26"))

(expect "Ifmmp! Ju't Hppe. 1"
        (rot 1 "Hello! It's Good. 1"))

(expect "cbCB"
        (rot 2 "azAZ"))

;; evens-first
(expect "hlowrdel ol"
        (evens-first "hello world"))
