(ns static.config
  (:use [clojure.contrib.logging])
  (:import (java.io File)))

(let [logger (impl-get-log "")]
  (doseq [handler (.getHandlers logger)]
    (. handler setFormatter 
       (proxy [java.util.logging.Formatter] [] 
	 (format 
	  [record] 
	  (str (.getLevel record) ": " (.getMessage record) "\n"))))))

(def config
     (memoize
      #(try 
	 (let [config (apply hash-map (read-string (slurp (File. "config.clj"))))]
	   ;;if emacs key is set make sure executable exists.
	   (when (:emacs config)
	     (if (not (.exists (File. (:emacs config))))
	       (do (error "Path to Emacs not valid.")
		   (System/exit 0))))
	   config)
	(catch Exception e (do 
			     (info "Configuration not found using defaults.")
			     {:in-dir "resources/"
			      :out-dir "html/"
			      :encoding "UTF-8"})))))
