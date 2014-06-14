(defproject codebreakers "0.1.0"
  :description "West London Hack Night's CodeBreakers"
  :url "http://github.com/krisajenins/codebreakers"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/core.async "0.1.278.0-76b25b-alpha"]
                 [org.clojure/core.match "0.2.1"]
                 [quil "2.1.0"]]
  :profiles {:dev {:dependencies [[expectations "2.0.7"]
                                  [org.clojure/core.typed "0.2.48"]]
                   :plugins [[lein-autoexpect "1.2.2"]]}}
  :core.typed {:check [codebreakers.core
                       codebreakers.cypher]}
  :main codebreakers.core
  :aliases {"test-ancient" ["test"]})
