(ns codebreakers.cypher-test
  (:require [expectations :refer :all]
            [codebreakers.cypher :refer :all]))

;;; Rot 13
(expect "Hello! It's Good."
        (rot 0 "Hello! It's Good."))

(expect "Hello! It's Good."
        (rot 26 "Hello! It's Good."))

(expect "Ifmmp! Ju't Hppe."
        (rot 1 "Hello! It's Good."))

(expect "cbCB"
        (rot 2 "azAZ"))

;; evens-first
(expect "hlowrdel ol"
        (evens-first "hello world"))
