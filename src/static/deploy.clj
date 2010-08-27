(ns static.deploy
  (:use [clojure.contrib.logging]
	[clojure.java.shell])
  (:use clj-ssh.ssh)
  (:import (java.io File)))

(defn- mirror-folders-sftp [channel out-dir deploy-dir]
  (let [file (File. out-dir)
	seq (file-seq file)
	folders (filter #(and (not (.isFile %)) 
			      (not (.equals % file))) seq)]
    (try (.mkdir channel deploy-dir) (catch Exception _))
    (doseq [fd folders]
      (try 
       (.mkdir channel (-> (str fd) (.replaceAll out-dir deploy-dir))) 
       (catch Exception _)))))

(defn- put-files [ch out-dir deploy-dir]
  (doseq [f (filter #(.isFile %) (file-seq (File. out-dir)))]
    (info (str "Sending " f))
    (sftp ch :put (str f) (-> (str f) (.replaceAll out-dir deploy-dir))))
  (info "Transfer Done."))

(defn deploy-sftp [out-dir host port user deploy-dir]
  (with-ssh-agent []
    (let [session (session host :strict-host-key-checking :no
  			   :port port :username user)]
      (with-connection session
  	(let [channel (ssh-sftp session)]
  	  (with-connection channel
	    (mirror-folders-sftp channel out-dir deploy-dir)
	    (put-files channel out-dir deploy-dir)))))))

(defn deploy-rsync [out-dir host user deploy-dir]
  (let [cmd ["rsync" "-avz" "--delete" "-e" "ssh" 
	     out-dir (str user "@" host ":" deploy-dir)]] 
    (info (:out (apply sh cmd)))))
