(ns codebreakers.cypher
  (:require [clojure.core.typed :refer [ann Seq check-ns cf Option HVec]]))

(ann rot-char [Integer Character -> Character])
(defn- rot-char
  [n c]
  (let [A (int \A)
        a (int \a)
        Z (int \Z)
        z (int \z)]
    (cond
     (< (int c) A) c
     (<= (int c) Z) (char (+ (mod (+ n (- (int c)
                                          A))
                                  26)
                             A))
     (< (int c) a) c
     (<= (int c) z) (char (+ (mod (+ n (- (int c)
                                          a))
                                  26)
                             a))
     :else c)))

(ann rot [Integer String -> String])
(defn rot
  [n string]
  (apply str (map (partial rot-char n)
                  string)))

(ann divide-with
  (All [x]
       [(Fn [x -> Any]) (Option (Seq x))
        -> (HVec [(Option (Seq x))
                  (Option (Seq x))])]))
(defn divide-with
  "Divide a sequence into [(items that pass the predicate) (items that don't)]."
  [pred coll]
  [(filter pred coll)
   (remove pred coll)])

(ann evens-first [String -> String])
(defn evens-first
  "Returns a new string with chars 0,2,4,6,8...first, followed by chars 1,3,5,7..."
  [string]
  (->> (map vector (range) string)
       (divide-with (comp even? first))
       (apply concat)
       (map second)
       (apply str)))
