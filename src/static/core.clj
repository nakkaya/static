(ns static.core
  (:gen-class)
  (:use clojure.contrib.command-line)
  (:use clojure.java.io)
  (:use hiccup.core)
  (:use [clojure.contrib.io :only [delete-file-recursively]])
  (:import (java.io File)
  	   (com.petebevin.markdown MarkdownProcessor)))

(defn markdown [txt] (.markdown (MarkdownProcessor.) txt))

(defn split-file [content]
  (let [idx (.indexOf content "---" 4)] 
    [(.substring content 4 idx) (.substring content (+ 3 idx))]))

(defn prepare-metadata [metadata]
  (reduce (fn [h [_ k v]] 
	    (let [key (keyword k)]
	      (assoc h key v)))
	  {} (re-seq #"([^:]+): (.+)(\n|$)" metadata)))

(defn read-markdown [file]
  (let [[metadata content] (split-file (slurp file))]
    [(prepare-metadata metadata) (markdown content)]))

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
  (with-command-line args
    "Static"
    [[in-dir       "Resources Directory" "resources/"]
     [out-dir      "Html Output Directory" "html/"]
     [encoding     "Default Encoding for Files" "UTF-8"]]
    (create in-dir out-dir encoding)))
