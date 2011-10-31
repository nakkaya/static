(ns static.io
  (:use [clojure.contrib.logging])
  (:use [clojure.java.shell :only [sh]])
  (:use static.config :reload-all)
  (:use hiccup.core)
  (:import (com.petebevin.markdown MarkdownProcessor)
	   (java.io File)
	   (org.apache.commons.io FileUtils FilenameUtils)))

(defn- markdown [txt] (.markdown (MarkdownProcessor.) txt))

(defn- split-file [content]
  (let [idx (.indexOf content "---" 4)] 
    [(.substring content 4 idx) (.substring content (+ 3 idx))]))

(defn- prepare-metadata [metadata]
  (reduce (fn [h [_ k v]] 
	    (let [key (keyword k)]
	      (assoc h key v)))
	  {} (re-seq #"([^:#\+]+): (.+)(\n|$)" metadata)))

(defn- read-markdown [file]
  (let [[metadata content] 
	(split-file (slurp file :encoding (:encoding (config))))]
    [(prepare-metadata metadata) (markdown content)]))

(defn- read-html [file]
  (let [[metadata content] 
	(split-file (slurp file :encoding (:encoding (config))))]
    [(prepare-metadata metadata) content]))

(defn- read-org [file]
  (if (not (:emacs (config)))
    (do (error "Path to Emacs is required for org files.")
	(System/exit 0)))
  (let [metadata (prepare-metadata (slurp file :encoding (:encoding (config))))
	content (:out (sh (:emacs (config))
			  "-batch" "-visit" (.getAbsolutePath file) "-eval"
			  (str
                           "(progn "
                           (apply str (map second (:emacs-eval (config))))
                           " (princ (org-no-properties (org-export-as-html nil nil nil 'string t nil))))")))]
    [metadata content]))

(defn- read-clj [file]
  (let [[metadata content] (read-string
                              (str \( (slurp file :encoding (:encoding (config))) \)))]
    [metadata (binding [*ns* (the-ns 'static.core)] (-> content eval html))]))

(def read-doc
     (memoize
      (fn [f]
	(let [extension (FilenameUtils/getExtension (str f))]
	  (cond (= extension "markdown") (read-markdown f)
		(= extension "org") (read-org f)
		(= extension "html") (read-html f)
                (= extension "clj") (read-clj f)
		:default (throw (Exception. "Unknown Extension.")))))))

(defn dir-path [dir]
  (cond (= dir :templates) (str (:in-dir (config)) "templates/")
	(= dir :public) (str (:in-dir (config)) "public/")
	(= dir :site) (str (:in-dir (config)) "site/")
	(= dir :posts) (str (:in-dir (config)) "posts/")
	:default (throw (Exception. "Unknown Directory."))))

(defn list-files [d]
  (let [d (File. (dir-path d))] 
    (if (.isDirectory d)
      (sort
       (filter
	#(let [[metadata _] (read-doc %)
	       published? (:published metadata)]
	   (if (or (nil? published?)
		   (= published? "true"))
	     true false))
	(FileUtils/listFiles d (into-array ["markdown"
                                            "clj"
					    "org"
					    "html"]) true))) [] )))

(def read-template
     (memoize
      (fn [template]
	(-> (str (dir-path :templates) template)
	    (File.) 
	    (slurp :encoding (:encoding (config)))
	    read-string))))

(defn write-out-dir [file str]
  (FileUtils/writeStringToFile 
   (File. (:out-dir (config)) file) str (:encoding (config))))
