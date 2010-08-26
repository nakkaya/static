(ns static.core
  (:gen-class)
  (:use [clojure.contrib.io :only [delete-file-recursively]]
	[clojure.contrib.with-ns]
	[clojure.contrib.command-line]
	[clojure.contrib.logging]
	[clojure.contrib.prxml])
  (:use hiccup.core)
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

(defn post-file-url [file]
  (let [name (FilenameUtils/getBaseName (str file))]
    (str (apply str (interleave (repeat \/) (.split name "-" 4))) "/")))

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

(defn post-xml
  "Create RSS item node."
  [file]
  (let [[metadata content] (read-markdown file)]
    [:item 
     [:title (:title metadata)]
     [:link  (str (URL. (URL. (:site-url (config))) (post-file-url file)))]
     [:description content]]))

(defn create-rss 
  "Create RSS feed."
  []
  (let [in-dir (File. (dir :posts))
	posts (take 10 (map #(File. in-dir %) (.list in-dir)))]
    (FileUtils/writeStringToFile
     (File. (:out-dir (config)) "rss-feed")
     (with-out-str
       (prxml [:decl! {:version "1.0"}] 
	      [:rss {:version "2.0"} 
	       [:channel 
		[:title (:site-title (config))]
		[:link (:site-url (config))]
		[:description (:site-description (config))]
		(map post-xml posts)]]))
     (:encoding (config)))))

(defn tag-map []
  (reduce 
   (fn[h v] 
     (let [post (File. (str (dir :posts) v))
	   [metadata _] (read-markdown post)
	   info [(post-file-url v) (:title metadata)]
	   tags (.split (:tags metadata) " ")]
       (reduce 
	(fn[m p] 
	  (let [[tag info] p] 
	    (if (nil? (m tag))
	      (assoc m tag [info])
	      (assoc m tag (conj (m tag) info)))))
	    h (partition 2 (interleave tags (repeat info))))))
   {} (.list (File. (dir :posts)))))

(defn create-tags []
  (FileUtils/writeStringToFile
   (File. (:out-dir (config)) "tags/index.html")
   (html
    (map (fn[t]
	   (let [[tag posts] t] 
	     [:h4 [:a {:name tag} tag]
	      (map #(let [[url title] %]
		      [:li [:a {:href url} title]]) posts)]))
	 (tag-map)))
   (:encoding (config))))

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
  (process-public)
  (if (pos? (-> (dir :posts) (File.) .list count))
    (do 
      (create-rss)
      (create-tags))))

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
