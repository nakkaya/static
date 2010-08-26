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
	   (org.apache.commons.io FileUtils FilenameUtils)
	   (java.text SimpleDateFormat)))

(defn set-log-format []
  (let [logger (impl-get-log "")]
    (doseq [handler (.getHandlers logger)]
      (. handler setFormatter 
	 (proxy [java.util.logging.Formatter] [] 
	   (format 
	    [record] 
	    (str (.getLevel record) ": " (.getMessage record) "\n")))))))

(defn parse-date [in out date]
  (.format (SimpleDateFormat. out) (.parse (SimpleDateFormat. in) date)))

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

(defn post-file-url [file]
  (let [name (FilenameUtils/getBaseName (str file))]
    (str (apply str (interleave (repeat \/) (.split name "-" 4))) "/")))

(defn post-count-by-mount []
  (reduce (fn [h v]
	    (let  [date (re-find #"\d*-\d*" v)]
	      (if (nil? (h date))
		(assoc h date 1)
		(assoc h date (+ 1 (h date)))))) 
	  {} (.list (File. (dir :posts)))))

(defn template [f]
  ;;get rid of this!!
  (if (coll? f)
    (def *f* f)
    (def *f* (read-markdown f)))
  (with-temp-ns
    (use 'static.markdown)
    (use 'hiccup.core)
    (import java.io.File)
    (let [[m c] static.core/*f*
	  template (str (static.core/dir :templates) (:template m))]
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

(defn pager [page]
  (let [older [:div {:class "pager-left"}
	       [:a {:href (str "/latest-posts/" (- page 1) "/")} 
		"&laquo; Older Entries"]]
	newer [:div {:class "pager-right"}
	       [:a {:href (str "/latest-posts/" (+ page 1) "/")} 
		"Newer Entries &raquo;"]]]
    (cond (= page 0) (list older)
	  (= page -1) (list newer)
	  :default (list older newer))))

(defn snippet
  "Render a post for display in index pages."
  [f]
  (let [[metadata content] (read-markdown (str (dir :posts) f))]
    [:div [:h2 [:a {:href (post-file-url f)} (:title meta)]]
     [:p {:class "publish_date"}  
      (parse-date "yyyy-MM-dd" "dd MMM yyyy" (re-find #"\d*-\d*-\d*" f))]
     [:p content]]))

(defn create-latest-posts []
  (let [posts (partition (:posts-per-page (config))
			 (reverse (.list (File. (dir :posts)))))
	pages (partition 2 (interleave 
			    (reverse posts) 
			    (concat (range (dec (count posts))) [-1])))]
    (doseq [[posts page] pages]
      (doseq [post posts]
    	(FileUtils/writeStringToFile
    	 (File. (:out-dir (config)) (str "latest-posts/" page "/index.html"))
    	 (template
	  [{:title (:site-title (config))
	    :template (:default-template (config))}
	   (html (list (snippet post) (pager page)))])
    	 (:encoding (config)))))))

(defn create-archives []
  (FileUtils/writeStringToFile
   (File. (:out-dir (config)) (str "archives/index.html"))
   (template
    [{:title "Archives" :template (:default-template (config))}
     (html 
      (list [:h2 "Archives"]
	    [:ul 
	     (map 
	      #(let [url (str "/archives/" (.replace (first %) "-" "/") "/")
		     date (parse-date "yyyy-MM" "MMMM yyyy" (first %))
		     count (str " (" (second %) ")")]
		 [:li [:a {:href url} date] count]) 
	      (post-count-by-mount))]))])
   (:encoding (config)))
  ;;create a page for each month
  (doseq [month (keys (post-count-by-mount))] 
    (let [posts (filter #(.startsWith % month) 
			(.list (File. (dir :posts))))]
      (FileUtils/writeStringToFile
       (File. (:out-dir (config)) (str "archives/" 
				       (.replace month "-" "/") 
				       "/index.html"))
       (template
	[{:title "Archives" :template (:default-template (config))}
	 (html 
	  (map snippet posts))])
       (:encoding (config))))))

(defn process-posts []
  (doseq [f (.list (File. (dir :posts)))]
    (let [out-file (apply str (-> (FilenameUtils/removeExtension f)
				  (.split "-" 4)
				  (interleave  (repeat \/))))]
      (FileUtils/writeStringToFile 
       (File. (str (:out-dir (config)) out-file "index.html"))
       (template (str (dir :posts) f)) 
       (:encoding (config))))))

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
      (process-posts)
      (create-rss)
      (create-tags)
      (create-latest-posts)
      (create-archives)
      (when (:blog-as-index (config))
	(FileUtils/moveFile 
	 (File. (str (:out-dir (config)) 
		     "latest-posts/0/index.html")) 
	 (File. (str (:out-dir (config)) "index.html")))))))

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
