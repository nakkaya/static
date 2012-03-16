(defproject static "1.1.0-SNAPSHOT"
  :description "Simple static site generator."
  :dependencies [[org.clojure/clojure "1.3.0"]
                 [org.clojure/tools.cli "0.2.1"]
                 [org.clojure/tools.logging "0.2.3"]
                 [hiccup "0.3.8"]
                 [org.pegdown/pegdown "1.1.0"]
                 [org.clojars.amit/commons-io "1.4.0"]
                 [ring/ring-core "1.0.2"]
                 [ring/ring-jetty-adapter "1.0.2"]]
  
  :main static.core
  :jar-name "static.jar"
  :uberjar-name "static-app.jar")
