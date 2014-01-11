(defproject static "1.1.0-SNAPSHOT"
  :description "Simple static site generator."
  :dependencies [[org.clojure/clojure "1.5.0"]
                 [org.clojure/tools.cli "0.2.4"]
                 [org.clojure/tools.logging "0.2.6"]
                 [cssgen "0.3.0-SNAPSHOT"]
                 [hiccup "1.0.4"]
                 [org.pegdown/pegdown "1.4.1"]
                 [org.clojars.amit/commons-io "1.4.0"]
                 [ring/ring-core "1.2.0"]
                 [ring/ring-jetty-adapter "1.2.0"]
                 [watchtower "0.1.1"]
                 [stringtemplate-clj "0.1.0"]
                 [org.clojure/core.memoize "0.5.6"]]
  :main static.core
  :jar-name "static.jar"
  :uberjar-name "static-app.jar")
