(ns static.config
  (:require [clojure.tools.logging :as log])
  (:import (java.io File)))


(let [defaults {:site-title "A Static Blog"
                :site-description "Default blog description"
                :site-url "https://github.com/nakkaya/static"
                :in-dir "resources/"
                :out-dir "html/"
                :post-out-subdir ""
                :default-template "default.clj"
                :default-extension "html"
                :encoding "UTF-8"
                :posts-per-page 2
                :blog-as-index true
                :create-archives true
                :org-export-command '(progn 
                                      (org-html-export-as-html nil nil nil t nil)
                                      (with-current-buffer "*Org HTML Export*"
                                        (princ (org-no-properties (buffer-string)))))}]
  
  (def config
    (memoize
     #(try 
        (let [config (apply hash-map (read-string (slurp (File. "config.clj"))))]
          ;;if emacs key is set make sure executable exists.
          (when (:emacs config)
            (if (not (.exists (File. (:emacs config))))
              (do (log/error "Path to Emacs not valid.")
                  (System/exit 0))))
          (merge defaults config))
        (catch Exception e (do 
                             (log/info "Configuration not found using defaults.")
                             defaults))))))

(defn set!-config [k v]
  (alter-var-root (find-var 'static.config/config) (fn [c] #(identity (assoc (c) k v)))))
