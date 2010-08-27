(ns static.markdown
  (:import (com.petebevin.markdown MarkdownProcessor)))

(defn- markdown [txt] (.markdown (MarkdownProcessor.) txt))

(defn- split-file [content]
  (let [idx (.indexOf content "---" 4)] 
    [(.substring content 4 idx) (.substring content (+ 3 idx))]))

(defn- prepare-metadata [metadata]
  (reduce (fn [h [_ k v]] 
	    (let [key (keyword k)]
	      (assoc h key v)))
	  {} (re-seq #"([^:]+): (.+)(\n|$)" metadata)))

(def read-markdown 
     (memoize
      (fn [file]
	(let [[metadata content] (split-file (slurp file))]
	  [(prepare-metadata metadata) (markdown content)]))))
