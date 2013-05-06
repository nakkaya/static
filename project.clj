(defproject static "1.1.0-SNAPSHOT"
  :description "Simple static site generator."
  :dependencies [[org.clojure/clojure "1.3.0"]
                 [org.clojure/tools.cli "0.2.1"]
                 [org.clojure/tools.logging "0.2.3"]
                 [cssgen "0.3.0-SNAPSHOT"]
                 [hiccup "0.3.8"]
                 [org.pegdown/pegdown "1.2.1"]
                 [org.clojars.amit/commons-io "1.4.0"]
                 [ring/ring-core "1.0.2"]
                 [ring/ring-jetty-adapter "1.0.2"]
                 [watchtower "0.1.0"]]
  :main static.core
  :jar-name "static.jar"
  :uberjar-name "static-app.jar")
