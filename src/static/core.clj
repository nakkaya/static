(ns static.core
  (:gen-class)
  (:use clojure.contrib.logging)
  (:use clojure.contrib.command-line)
  (:use clojure.java.io)
  (:use clj-ssh.ssh)
  (:use hiccup.core)
  (:use [clojure.contrib.io :only [delete-file-recursively]])
  (:import (java.io File FileInputStream)
  	   (com.petebevin.markdown MarkdownProcessor)))

(defn set-log-format []
  (let [logger (impl-get-log "")]
    (doseq [handler (.getHandlers logger)]
      (. handler setFormatter 
	 (proxy [java.util.logging.Formatter] [] 
	   (format 
	    [record] 
	    (str (.getLevel record) ": " (.getMessage record) "\n")))))))

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

(defn mirror-folders-sftp [channel out-dir deploy-dir]
  (let [file (File. out-dir)
	seq (file-seq file)
	folders (filter #(and (not (.isFile %)) 
			      (not (.equals % file))) seq)]
    (try (.mkdir channel deploy-dir) (catch Exception _))
    (doseq [fd folders]
      (try 
       (.mkdir channel (-> (str fd) (.replaceAll out-dir deploy-dir))) 
       (catch Exception _)))))

(defn put-files [ch out-dir deploy-dir]
  (doseq [f (filter #(.isFile %) (file-seq (File. out-dir)))]
    (info (str "Sending " f))
    (sftp ch :put (str f) (-> (str f) (.replaceAll out-dir deploy-dir))))
  (info "Transfer Done."))

(defn deploy [out-dir host port user deploy-dir]
  (with-ssh-agent []
    (let [session (session host :strict-host-key-checking :no
  			   :port port :username user)]
      (with-connection session
  	(let [channel (ssh-sftp session)]
  	  (with-connection channel
	    (mirror-folders-sftp channel out-dir deploy-dir)
	    (put-files channel out-dir deploy-dir)))))))

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
