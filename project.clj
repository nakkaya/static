(defproject static "1.1.0-SNAPSHOT"
  :description "Simple static site generator."
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/tools.cli "0.3.2"]
                 [org.clojure/tools.logging "0.3.1"]
                 [cssgen "0.3.0-SNAPSHOT" :exclusions [org.clojure/clojure]]
                 [hiccup "1.0.5"]
                 [org.pegdown/pegdown "1.5.0"]
                 [org.clojars.amit/commons-io "1.4.0"]
                 [ring/ring-core "1.4.0"]
                 [ring/ring-jetty-adapter "1.4.0"]
                 [watchtower "0.1.1"]
                 [stringtemplate-clj "0.1.0"]
                 [org.clojure/core.memoize "0.5.6"]]
  :main static.core
  :jar-name "static.jar"
  :uberjar-name "static-app.jar")
