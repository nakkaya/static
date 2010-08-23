(ns static.core
  (:gen-class)
  (:use [clojure.contrib.io :only [delete-file-recursively]]
	[clojure.contrib.with-ns]
	[clojure.contrib.command-line]
	[clojure.contrib.logging])
  (:use clojure.java.io)
  (:use static.sftp :reload-all)
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

(defn template [f in-dir encoding]
  ;;get rid of this!!
  (def *f* f)
  (def *in-dir* in-dir)
  (def *encoding* encoding)
  (with-temp-ns
    (use 'static.markdown)
    (use 'hiccup.core)
    (use 'clojure.java.io)
    (let [[m c] (read-markdown static.core/*f*)
	  template (str static.core/*in-dir* "templates/" (:template m))]
      (def metadata m)
      (def content c)
      (-> template 
	  file 
	  (slurp :encoding static.core/*encoding*) 
	  read-string 
	  eval
	  html))))

(defn process-site [in-dir out-dir encoding]
  (let [files (filter #(.isFile %) (file-seq (File. (str in-dir "site/"))))]

    (mirror-folders (str in-dir "site/") out-dir)

    (doseq [f files]
      (spit (-> (str f)
		(.replaceAll (str in-dir "site/") out-dir)
		(.replaceAll ".markdown" ".html")
		(File.)) 
	    (template f in-dir encoding) :encoding encoding))))

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
