(ns static.core
  (:gen-class)
  (:use [clojure.contrib.io :only [delete-file-recursively]]
	[clojure.contrib.with-ns]
	[clojure.contrib.command-line]
	[clojure.contrib.logging]
	[clojure.contrib.prxml])
  (:use hiccup.core)
  (:use static.config :reload-all)
  (:use static.io :reload-all)
  (:use static.deploy :reload-all)
  (:import (java.io File)
	   (java.net URL)
	   (org.apache.commons.io FileUtils FilenameUtils)
	   (java.text SimpleDateFormat)))

(defn parse-date 
  "Format date from in spec to out spec."
  [in out date]
  (.format (SimpleDateFormat. out) (.parse (SimpleDateFormat. in) date)))

(defn post-url 
  "Given a post file return its URL."
  [file]
  (let [name (FilenameUtils/getBaseName (str file))]
    (str (apply str (interleave (repeat \/) (.split name "-" 4))) "/")))

(declare metadata content)

(defn template [page]
  (let [[m c] page
	template (if (:template m)
		   (:template m) 
		   (:default-template (static.config/config)))]
    (binding [*ns* (the-ns 'static.core)
	      metadata m content c]
      (-> (read-template template) eval html))))

(defn process-site 
  "Process site pages."
  []
  (doseq [f (FileUtils/listFiles (File. (dir :site)) nil true)]
    (let [[metadata content] (read-markdown f)]
      (write-out-dir
       (-> (str f)
	   (.replaceAll (dir :site) "")
	   (FilenameUtils/removeExtension)
	   (str ".html"))
       (template [(assoc metadata :type :site) content])))))

;;
;; Create RSS Feed.
;;

(defn post-xml
  "Create RSS item node."
  [file]
  (let [[metadata content] (read-markdown file)]
    [:item 
     [:title (:title metadata)]
     [:link  (str (URL. (URL. (:site-url (config))) (post-url file)))]
     [:description content]]))

(defn create-rss 
  "Create RSS feed."
  []
  (let [in-dir (File. (dir :posts))
	posts (take 10 (map #(File. in-dir %) (.list in-dir)))]
    (write-out-dir "rss-feed"
		   (with-out-str
		     (prxml [:decl! {:version "1.0"}] 
			    [:rss {:version "2.0"} 
			     [:channel 
			      [:title (:site-title (config))]
			      [:link (:site-url (config))]
			      [:description (:site-description (config))]
			      (map post-xml posts)]])))))

;;
;; Create Tags Page.
;;

(defn tag-map 
  "Create a map of tags and posts contining them. {tag1 => [url1 url2..]}"
  []
  (reduce 
   (fn[h v] 
     (let [post (File. (str (dir :posts) v))
	   [metadata _] (read-markdown post)
	   info [(post-url v) (:title metadata)]
	   tags (.split (:tags metadata) " ")]
       (reduce 
	(fn[m p] 
	  (let [[tag info] p] 
	    (if (nil? (m tag))
	      (assoc m tag [info])
	      (assoc m tag (conj (m tag) info)))))
	h (partition 2 (interleave tags (repeat info))))))
   (sorted-map) (.list (File. (dir :posts)))))

(defn create-tags 
  "Create and write tags page."
  []
  (write-out-dir "tags/index.html"
		 (template
		  [{:title "Tags" :template (:default-template (config))}
		   (html
		    (map (fn[t]
			   (let [[tag posts] t] 
			     [:h4 [:a {:name tag} tag]
			      [:ul
			       (map #(let [[url title] %]
				       [:li [:a {:href url} title]]) 
				    posts)]]))
			 (tag-map)))])))

;;
;; Create pages for latest posts.
;;

(defn pager
  "Return previous, next navigation links."
  [page max-index]
  (let [older [:div {:class "pager-left"}
	       [:a {:href (str "/latest-posts/" (- page 1) "/")} 
		"&laquo; Older Entries"]]
	newer [:div {:class "pager-right"}
	       [:a {:href (str "/latest-posts/" (+ page 1) "/")} 
		"Newer Entries &raquo;"]]]
    (cond (= page max-index) (list older)
	  (= page 0) (list newer)
	  :default (list older newer))))

(defn snippet
  "Render a post for display in index pages."
  [f]
  (let [[metadata content] (read-markdown (str (dir :posts) f))]
    [:div [:h2 [:a {:href (post-url f)} (:title metadata)]]
     [:p {:class "publish_date"}  
      (parse-date "yyyy-MM-dd" "dd MMM yyyy" (re-find #"\d*-\d*-\d*" f))]
     [:p content]]))

(defn create-latest-posts 
  "Create and write latest post pages."
  []
  (let [posts (partition (:posts-per-page (config))
			 (reverse (.list (File. (dir :posts)))))
	pages (partition 2 (interleave (reverse posts) (range)))
	[_ max-index] (last pages)]
    (doseq [[posts page] pages]
      (write-out-dir
       (str "latest-posts/" page "/index.html")
       (template
    	[{:title (:site-title (config))
    	  :template (:default-template (config))}
    	 (html (list (map #(snippet %) posts) (pager page max-index)))])))))

;;
;; Create Archive Pages.
;;

(defn post-count-by-mount 
  "Create a map of month to post count {month => count}"
  []
  (reduce (fn [h v]
	    (let  [date (re-find #"\d*-\d*" v)]
	      (if (nil? (h date))
		(assoc h date 1)
		(assoc h date (+ 1 (h date)))))) 
	  {} (.list (File. (dir :posts)))))

(defn create-archives 
  "Create and write archive pages."
  []
  ;;create main archive page.
  (write-out-dir
   (str "archives/index.html")
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
	      (post-count-by-mount))]))]))
  ;;create a page for each month.
  (doseq [month (keys (post-count-by-mount))] 
    (let [posts (filter #(.startsWith % month) 
			(.list (File. (dir :posts))))]
      (write-out-dir
       (str "archives/" (.replace month "-" "/") "/index.html")
       (template
	[{:title "Archives" :template (:default-template (config))}
	 (html (map snippet posts))])))))

(defn process-posts 
  "Create and write post pages."
  []
  (doseq [f (.list (File. (dir :posts)))]
    (let [[metadata content] (read-markdown (str (dir :posts) f))
	  out-file (reduce (fn[h v] (.replaceFirst h "-" "/")) 
			   (FilenameUtils/removeExtension f) (range 3))]
      (write-out-dir 
       (str out-file "/index.html")
       (template 
	[(assoc metadata :type :post :url (post-url f)) content])))))

(defn process-public 
  "Copy public from in-dir to out-dir."
  []
  (let [in-dir (File. (dir :public))
	out-dir (File. (:out-dir (config)))]
    (doseq [f (map #(File. in-dir %) (.list in-dir))]
      (if (.isFile f)
	(FileUtils/copyFileToDirectory f out-dir)
	(FileUtils/copyDirectoryToDirectory f out-dir)))))

(defn create 
  "Build Site."
  [] 
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
	(let [max (apply max (map read-string (-> (:out-dir (config))
						  (str  "latest-posts/")
						  (File.)
						  .list)))]
	  (FileUtils/copyFile 
	   (File. (str (:out-dir (config)) 
		       "latest-posts/" max "/index.html")) 
	   (File. (str (:out-dir (config)) "index.html"))))))))

(defn -main [& args]
  (with-command-line args
    "Static"
    [[build? b? "Build Site."]
     [ssh? s?    "Deploy using SFTP."]
     [rsync? r?  "Deploy using rsync."]]
    (cond build? (info (with-out-str (time (create))))
	  ssh? (deploy-sftp (:out-dir (config)) 
			    (:host (config)) 
			    (:port (config))
			    (:user (config))
			    (:deploy-dir (config)))
	  rsync? (deploy-rsync (:out-dir (config)) 
			       (:host (config)) 
			       (:user (config))
			       (:deploy-dir (config)))
	  :default (println "Use -h for options."))))
