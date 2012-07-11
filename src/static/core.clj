(ns static.core
  (:gen-class)
  (:require [watchtower.core :as watcher])
  (:use [clojure.tools logging cli]
        [clojure.java.browse]
        [ring.adapter.jetty]
        [ring.middleware.file]
        [ring.util.response]
        [hiccup core page-helpers])

  (:use static.config :reload-all)
  (:use static.io :reload-all)
  (:import (java.io File)
           (java.net URL)
           (org.apache.commons.io FileUtils FilenameUtils)
           (java.text SimpleDateFormat)))

(defn setup-logging []
  (let [logger (java.util.logging.Logger/getLogger "")]
    (doseq [handler (.getHandlers logger)]
      (. handler setFormatter
         (proxy [java.util.logging.Formatter] []
           (format
             [record]
             (str "[+] " (.getLevel record) ": " (.getMessage record) "\n")))))))

(defmacro log-time-elapsed
  "Evaluates expr and logs the time it took.  Returns the value of expr."
  {:added "1.0"}
  [msg & expr]
  `(let [start# (. System (currentTimeMillis))
         ret# (do ~@expr)]
     (info (str ~msg " " (/ (double (- (. System (currentTimeMillis)) start#)) 1000.0) " secs"))
     ret#))

(defn parse-date 
  "Format date from in spec to out spec."
  [in out date]
  (.format (SimpleDateFormat. out) (.parse (SimpleDateFormat. in) date)))

(defn post-url 
  "Given a post file return its URL."
  [file]
  (let [name (FilenameUtils/getBaseName (str file))]
    (str (apply str (interleave (repeat \/) (.split name "-" 4))) "/")))

(defn site-url [f & [ext]]
  (-> (str f)
      (.replaceAll (dir-path :site) "")
      (FilenameUtils/removeExtension)
      (str "."
           (or ext
               (:default-extension (config))))))

(def ^:dynamic metadata nil)
(def ^:dynamic content nil)

(defn template [page]
  (let [[m c] page
        template (if (:template m)
                   (:template m) 
                   (:default-template (static.config/config)))
        template-string (if (= template :none)
                          content
                          (read-template template))]
    (binding [*ns* (the-ns 'static.core)
              metadata m content c]
      (apply str (map #(html (eval %)) template-string)))))

(defn process-site 
  "Process site pages."
  []
  (dorun
   (pmap
    #(let [f %
           [metadata content] (read-doc f)]
       (write-out-dir
        (site-url f (:extension metadata))
        (template [(assoc metadata :type :site) @content])))
    (list-files :site))))

;;
;; Create RSS Feed.
;;

(defn post-xml
  "Create RSS item node."
  [file]
  (let [[metadata content] (read-doc file)]
    [:item 
     [:title (escape-html (:title metadata))]
     [:link  (str (URL. (URL. (:site-url (config))) (post-url file)))]
     [:description @content]]))

(defn create-rss 
  "Create RSS feed."
  []
  (let [in-dir (File. (dir-path :posts))
        posts (take 10 (reverse (list-files :posts)))]
    (write-out-dir "rss-feed"
                   (html (xml-declaration "UTF-8")
                         [:rss {:version "2.0"} 
                          [:channel 
                           [:title (escape-html (:site-title (config)))]
                           [:link (:site-url (config))]
                           [:description
                            (escape-html (:site-description (config)))]
                           (pmap post-xml posts)]]))))

(defn create-sitemap
  "Create sitemap."
  []
  (write-out-dir 
   "sitemap.xml"
   (let [base (:site-url (config))] 
     (html (xml-declaration "UTF-8") 
           [:urlset {:xmlns "http://www.sitemaps.org/schemas/sitemap/0.9"}
            [:url [:loc base]]
            (map #(vector :url [:loc (str base %)]) 
                 (map post-url (list-files :posts)))
            (map #(vector :url [:loc (str base "/" %)]) 
                 (map site-url (list-files :site)))]))))

;;
;; Create Tags Page.
;;

(defn tag-map 
  "Create a map of tags and posts contining them. {tag1 => [url1 url2..]}"
  []
  (reduce 
   (fn[h v]
     (let [[metadata] (read-doc v)
           info [(post-url v) (:title metadata)]
           tags (.split (:tags metadata) " ")]
       (reduce 
        (fn[m p] 
          (let [[tag info] p] 
            (if (nil? (m tag))
              (assoc m tag [info])
              (assoc m tag (conj (m tag) info)))))
        h (partition 2 (interleave tags (repeat info))))))
   (sorted-map)   
   (filter #(not (nil? (:tags (first (read-doc %))))) (list-files :posts))))

(defn create-tags 
  "Create and write tags page."
  []
  (write-out-dir "tags/index.html"
                 (template
                  [{:title "Tags" :template (:default-template (config))}
                   (html
                    [:h2 "Tags"]
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
  [page max-index posts-per-page]
  (let [count-total (count (list-files :posts))
        older [:div {:class "pager-left"}
               [:a {:href (str "/latest-posts/" (- page 1) "/")} 
                "&laquo; Older Entries"]]
        newer [:div {:class "pager-right"}
               [:a {:href (str "/latest-posts/" (+ page 1) "/")} 
                "Newer Entries &raquo;"]]]
    (cond
     (< count-total posts-per-page) nil
     (= page max-index) (list older)
     (= page 0) (list newer)
     :default (list older newer))))

(defn snippet
  "Render a post for display in index pages."
  [f]
  (let [[metadata content] (read-doc f)]
    [:div [:h2 [:a {:href (post-url f)} (:title metadata)]]
     [:p {:class "publish_date"}  
      (parse-date "yyyy-MM-dd" "dd MMM yyyy" 
                  (re-find #"\d*-\d*-\d*" 
                           (FilenameUtils/getBaseName (str f))))]
     [:p @content]]))

(defn create-latest-posts 
  "Create and write latest post pages."
  []
  (let [posts-per-page (:posts-per-page (config))
        posts (partition posts-per-page
                         posts-per-page
                         []
                         (reverse (list-files :posts)))
        pages (partition 2 (interleave (reverse posts) (range)))
        [_ max-index] (last pages)]
    (doseq [[posts page] pages]
      (write-out-dir
       (str "latest-posts/" page "/index.html")
       (template
            [{:title (:site-title (config))
          :description (:site-description (config))
              :template (:default-template (config))}
             (html (list (map #(snippet %) posts) (pager page max-index posts-per-page)))])))))

;;
;; Create Archive Pages.
;;

(defn post-count-by-mount 
  "Create a map of month to post count {month => count}"
  []
  (->> (list-files :posts)
       (reduce (fn [h v]
                 (let  [date (re-find #"\d*-\d*" 
                                      (FilenameUtils/getBaseName (str v)))]
                   (if (nil? (h date))
                     (assoc h date 1)
                     (assoc h date (+ 1 (h date)))))) {})
       (sort-by first)
       reverse))

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
              (fn [[mount count]]
                [:li [:a
                      {:href (str "/archives/" (.replace mount "-" "/") "/")}
                      (parse-date "yyyy-MM" "MMMM yyyy" mount)]
                 (str " (" count ")")])
              (post-count-by-mount))]))]))
  
  ;;create a page for each month.
  (dorun
   (pmap
    (fn [month]
      (let [posts (->> (list-files :posts)
                       (filter #(.startsWith 
                                 (FilenameUtils/getBaseName (str %)) month))
                       reverse)]
        (write-out-dir
         (str "archives/" (.replace month "-" "/") "/index.html")
         (template
          [{:title "Archives" :template (:default-template (config))}
           (html (map snippet posts))]))))
    (keys (post-count-by-mount)))))

(defn create-aliases 
  "Create redirect pages."
  ([]
     (doseq [post (list-files :posts)]
       (create-aliases post))
     (doseq [site (list-files :site)]
       (create-aliases site)))
  ([file]
     (let [doc (read-doc file)]
       (when-let [aliases (-> doc first :alias)]
         (doseq [alias (read-string aliases)]
           (write-out-dir
            alias
            (html [:html
                   [:head
                    [:meta {:http-equiv "content-type" :content "text/html; charset=utf-8"}]
                    [:meta {:http-equiv "refresh" :content (str "0;url=" (post-url file))}]]])))))))

(defn process-posts 
  "Create and write post pages."
  []
  (dorun
   (pmap
    #(let [f %
           [metadata content] (read-doc f)
           out-file (reduce (fn[h v] (.replaceFirst h "-" "/")) 
                            (FilenameUtils/getBaseName (str f)) (range 3))]
       (write-out-dir 
        (str out-file "/index.html")
        (template 
         [(assoc metadata :type :post :url (post-url f)) @content])))
    (list-files :posts))))

(defn process-public 
  "Copy public from in-dir to out-dir."
  []
  (let [in-dir (File. (dir-path :public))
        out-dir (File. (:out-dir (config)))]
    (doseq [f (map #(File. in-dir %) (.list in-dir))]
      (if (.isFile f)
        (FileUtils/copyFileToDirectory f out-dir)
        (FileUtils/copyDirectoryToDirectory f out-dir)))))

(defn create 
  "Build Site."
  [] 
  (doto (File. (:out-dir (config)))
    (FileUtils/deleteDirectory)
    (.mkdir))

  (log-time-elapsed "Processing Public " (process-public))
  (log-time-elapsed "Processing Site " (process-site))
  
  (if (pos? (-> (dir-path :posts) (File.) .list count))
    (do 
      (log-time-elapsed "Processing Posts " (process-posts))
      (log-time-elapsed "Creating RSS " (create-rss))
      (log-time-elapsed "Creating Tags " (create-tags))
      
      (when (:create-archives (config))
        (log-time-elapsed "Creating Archives " (create-archives)))
      
      (log-time-elapsed "Creating Sitemap " (create-sitemap))
      (log-time-elapsed "Creating Aliases " (create-aliases))

      (when (:blog-as-index (config))
        (log-time-elapsed "Creating Latest Posts " (create-latest-posts))
        (let [max (apply max (map read-string (-> (:out-dir (config))
                                                  (str  "latest-posts/")
                                                  (File.)
                                                  .list)))]
          (FileUtils/copyFile 
           (File. (str (:out-dir (config)) 
                       "latest-posts/" max "/index.html")) 
           (File. (str (:out-dir (config)) "index.html"))))))))

(defn serve-static [req] 
  (let [mime-types {".clj" "text/plain"
                    ".mp4" "video/mp4"
                    ".ogv" "video/ogg"}]
    (if-let [f (file-response (:uri req) {:root (:out-dir (config))})] 
      (if-let [mimetype (mime-types (re-find #"\..+$" (:uri req)))] 
        (merge f {:headers {"Content-Type" mimetype}}) 
        f))))

(defn watch-and-rebuild
  "Watch for changes and rebuild site on change."
  []
  (watcher/watcher [(:in-dir (config))]
                   (watcher/rate 1000)
                   (watcher/on-change (fn [_]
                                        (info "Rebuilding site...")
                                        (try
                                          (create)
                                          (catch Exception e
                                            (warn (str "Exception thrown while building site! " e))))))))

(defn -main [& args]
  (let [[opts _ banner] (cli args
                             ["--build" "Build Site." :default false :flag true]
                             ["--tmp" "Use tmp location override :out-dir" :default false :flag true]
                             ["--jetty" "View Site." :default false :flag true]
                             ["--watch" "Watch Site and Rebuild on Change." :default false :flag true]
                             ["--rsync" "Deploy Site." :default false :flag true]
                             ["--help" "Show help" :default false :flag true])
        {:keys [build tmp jetty watch rsync help]} opts]

    (when help
      (println "Static")
      (println banner)
      (System/exit 0))

    (setup-logging)

    (when tmp
      (config) ;;load config
      (let [loc (FilenameUtils/normalize
                 (str (System/getProperty "java.io.tmpdir") "/" "static/"))]
        (set!-config :out-dir loc)
        (info (str "Using tmp location: " (:out-dir (config))))))
    
    (cond build (log-time-elapsed "Build took " (create))
          watch (do (watch-and-rebuild)
                    (future (run-jetty serve-static {:port 8080}))
                    (browse-url "http://127.0.0.1:8080"))
          jetty (do (future (run-jetty serve-static {:port 8080}))
                    (browse-url "http://127.0.0.1:8080"))
          rsync (let [{:keys [rsync out-dir host user deploy-dir]} (config)]
                  (deploy-rsync rsync out-dir host user deploy-dir))
          :default (println "Use --help for options.")))
  (shutdown-agents))