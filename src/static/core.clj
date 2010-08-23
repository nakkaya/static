(ns static.core
  (:gen-class)
  (:use clojure.contrib.logging)
  (:use clojure.contrib.command-line)
  (:use clojure.java.io)
  (:use hiccup.core)
  (:use static.sftp :reload-all)
  (:use static.markdown :reload-all)
  (:use [clojure.contrib.io :only [delete-file-recursively]])
  (:import (java.io File)))

(defn set-log-format []
  (let [logger (impl-get-log "")]
    (doseq [handler (.getHandlers logger)]
      (. handler setFormatter 
	 (proxy [java.util.logging.Formatter] [] 
	   (format 
	    [record] 
	    (str (.getLevel record) ": " (.getMessage record) "\n")))))))

(defn mirror-folders [in-dir out-dir]
  (let [file (File. in-dir)
	seq (file-seq file)
	folders (filter #(and (not (.isFile %)) 
			      (not (.equals % file))) seq)]
    (doseq [f folders]
      (-> (str f)
    	  (.replaceAll in-dir out-dir)
    	  (File.)
    	  (.mkdir)))))

;;(mirror-folders "resources/public/" "html/")

(defn process-site [in-dir out-dir encoding]
  (let [files (filter #(.isFile %) (file-seq (File. (str in-dir "site/"))))]

    (mirror-folders (str in-dir "site/") out-dir)

    (doseq [f files]
      (let [[metadata content] (read-markdown f)
    	    template (-> (str (str in-dir "templates/") (:template metadata))
    	    		 file
    	    		 (slurp :encoding encoding)
    	    		 read-string
    	    		 html
    	    		 (.replaceAll "\\$content\\$" content))]
    	(spit (-> (str f)
    		  (.replaceAll (str in-dir "site/") out-dir)
    		  (.replaceAll ".markdown" ".html")
    		  (File.)) 
    	      template :encoding encoding)))))

;;(process-site "resources/" "html/" "UTF-8")

(defn process-public [in-dir out-dir]
  (let [files (filter #(.isFile %) 
		      (file-seq (File. (str in-dir "public/")))) ] 
    (mirror-folders (str in-dir "public/") out-dir)
    (doseq [f files]
      (copy f (-> (str f)
		  (.replaceAll (str in-dir "public/") out-dir)
		  (File.))))))

(defn create [in-dir out-dir encoding] 
  (doto (File. out-dir)
    (delete-file-recursively true)
    (.mkdir))
  (process-site in-dir out-dir encoding)
  (process-public in-dir out-dir))

;;(create "resources/" "html/" "UTF-8")

(defn -main [& args]
  (set-log-format)
  (with-command-line args
    "Static"
    [[in-dir       "Resources Directory" "resources/"]
     [out-dir      "Html Output Directory" "html/"]
     [encoding     "Default Encoding for Files" "UTF-8"]
     [deploy? d?   "Deploy over SSH."]
     [deploy-dir   "Remote Directory to Deploy"]
     [host         "Remote Host to Deploy"]
     [port         "SSH Port"]
     [user         "SSH Username"]]
    (if-not deploy?
      (create in-dir out-dir encoding)
      (deploy out-dir host (read-string port) user deploy-dir))))
