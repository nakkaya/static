(defproject static "1.0.0-SNAPSHOT"
  :description "Simple static site generator."
  :dependencies [[org.clojure/clojure "1.2.0"]
		 [org.clojure/clojure-contrib "1.2.0"]
		 [org.clojars.paraseba/hiccup "0.2.3"]
		 [org.clojars.nakkaya/markdownj "1.0.2b4"]
		 [org.clojars.amit/commons-io "1.4.0"]
		 [ring/ring-core "0.2.5"]
		 [ring/ring-jetty-adapter "0.2.5"]]
  :main static.core)
