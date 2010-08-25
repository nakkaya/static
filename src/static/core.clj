(ns static.core
  (:gen-class)
  (:use [clojure.contrib.io :only [delete-file-recursively]]
	[clojure.contrib.with-ns]
	[clojure.contrib.command-line]
	[clojure.contrib.logging]
	[clojure.contrib.prxml])
  (:use static.markdown :reload-all)
  (:use static.sftp :reload-all)
  (:import (java.io File)
	   (java.net URL)
	   (org.apache.commons.io FileUtils FilenameUtils)))

(defn set-log-format []
  (let [logger (impl-get-log "")]
    (doseq [handler (.getHandlers logger)]
      (. handler setFormatter 
	 (proxy [java.util.logging.Formatter] [] 
	   (format 
	    [record] 
	    (str (.getLevel record) ": " (.getMessage record) "\n")))))))

(def config
     (memoize
      #(try 
	(apply hash-map (read-string (slurp (File. "config.clj"))))
	(catch Exception e (do 
			     (info "Configuration not found using defaults.")
			     {:in-dir "resources/"
			      :out-dir "html/"
			      :encoding "UTF-8"})))))

(defn dir [dir]
  (cond (= dir :templates) (str (:in-dir (config)) "templates/")
	(= dir :public) (str (:in-dir (config)) "public/")
	(= dir :site) (str (:in-dir (config)) "site/")
	(= dir :posts) (str (:in-dir (config)) "posts/")
	:default (throw (Exception. "Unknown Directory."))))

(defn template [f]
  ;;get rid of this!!
  (def *f* f)
  (with-temp-ns
    (use 'static.markdown)
    (use 'hiccup.core)
    (import java.io.File)
    (let [[m c] (read-markdown static.core/*f*)
	  template (str  (static.core/dir :templates) (:template m))]
      (def metadata m)
      (def content c)
      (-> template 
	  (File.) 
	  (slurp :encoding (:encoding (static.core/config))) 
	  read-string 
	  eval
	  html))))

(defn process-site []
  (doseq [f (FileUtils/listFiles (File. (dir :site)) nil true)]
    (FileUtils/writeStringToFile 
     (-> (str f)
	 (.replaceAll (dir :site) (:out-dir (config)))
	 (FilenameUtils/removeExtension)
	 (str ".html")
	 (File.)) 
     (template f) (:encoding (config)))))

(defn process-public []
  (let [in-dir (File. (dir :public))
	out-dir (File. (:out-dir (config)))]
    (doseq [f (map #(File. in-dir %) (.list in-dir))]
      (if (.isFile f)
	(FileUtils/copyFileToDirectory f out-dir)
	(FileUtils/copyDirectoryToDirectory f out-dir)))))

(defn create [] 
  (doto (File. (:out-dir (config)))
    (delete-file-recursively true)
    (.mkdir))
  (process-site)
  (process-public))

(defn -main [& args]
  (set-log-format)
  (with-command-line args
    "Static"
    [[build? b?   "Build Site."]
     [deploy? d?   "Deploy over SSH."]]
    (cond build? (create)
	  deploy? (deploy (:out-dir (config)) 
			  (:host (config)) 
			  (:port (config))
			  (:user (config))
			  (:deploy-dir (config)))
	  :default (println "Use -h for options."))))
