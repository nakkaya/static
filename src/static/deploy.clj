(ns static.deploy
  (:use [clojure.contrib.logging]
	[clojure.java.shell])
  (:import (java.io File)))

(defn deploy-rsync [out-dir host user deploy-dir]
  (let [cmd ["rsync" "-avz" "--delete" "-e" "ssh" 
	     out-dir (str user "@" host ":" deploy-dir)]] 
    (info (:out (apply sh cmd)))))
