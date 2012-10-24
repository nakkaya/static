(ns static.io
  (:use [clojure.tools logging]
        [clojure.java.shell :only [sh]]
        [cssgen]
        [hiccup core])
  (:use static.config :reload-all)
  (:import (org.pegdown PegDownProcessor)
           (java.io File)
           (java.io InputStreamReader OutputStreamWriter)
           (org.apache.commons.io FileUtils FilenameUtils)))

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
    [(prepare-metadata metadata)
     (delay (.markdownToHtml (PegDownProcessor.) content))]))

(defn- read-html [file]
  (let [[metadata content]
        (split-file (slurp file :encoding (:encoding (config))))]
    [(prepare-metadata metadata) (delay content)]))

(defn emacs-start []
  (.exec (Runtime/getRuntime)
         (into-array [(:emacs (config))
                      "-q"
                      "-daemon"
                      "-eval"
                      (str   
                       "(progn
                         (setq server-name \"staticEmacsServer\")
                       " (apply str (map second (:emacs-eval (config)))) ")")])))

;;(emacs-start)

(defn emacs-stop []
  (sh (:emacsclient (config))
      "-s"
      "staticEmacsServer"
      "-eval"
      "(progn (setq kill-emacs-hook 'nil) (kill-emacs))"))

;;(emacs-stop)

(let [process (Object.)]
  (defn- read-org [file]
    (if (or (not (:emacs (config)))
            (not (:emacsclient (config))))
      (do (error "Path to Emacs and Emacs Client are required for org files.")
          (System/exit 0)))
    (locking process
      (let [metadata (prepare-metadata
                      (apply str
                             (take 500 (slurp file :encoding (:encoding (config))))))
            content (delay
                     (:out (sh (:emacsclient (config))
                               "-s"
                               "staticEmacsServer"
                               "-n"
                               "-eval"
                               (str
                                "(progn "
                                " (find-file \"" (.getAbsolutePath file) "\") "
                                " (org-no-properties (org-export-as-html nil nil nil 'string t nil)))"))))]
        [metadata content]))))

(defn- read-clj [file]
  (let [[metadata content] (read-string
                              (str \( (slurp file :encoding (:encoding (config))) \)))]
    [metadata (delay (binding [*ns* (the-ns 'static.core)] (-> content eval html)))]))

(defn- read-cssgen [file]
  (let [metadata {:extension "css" :template :none}
        content (read-string
                 (slurp file :encoding (:encoding (config))))
        to-css  #(clojure.string/join "\n" (doall (map css %)))]
    [metadata (delay (binding [*ns* (the-ns 'static.core)] (-> content eval to-css)))]))

(defn read-doc [f]
  (let [extension (FilenameUtils/getExtension (str f))]
    (cond (or (= extension "markdown") (= extension "md"))
          (read-markdown f)
          (= extension "md") (read-markdown f)
          (= extension "org") (read-org f)
          (= extension "html") (read-html f)
          (= extension "clj") (read-clj f)
          (= extension "cssgen") (read-cssgen f)
          :default (throw (Exception. "Unknown Extension.")))))

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
       (FileUtils/listFiles d (into-array ["markdown"
                                           "md"
                                           "clj"
                                           "cssgen"
                                           "org"
                                           "html"]) true)) [] )))

(def read-template
     (memoize
      (fn [template]
        (-> (str (dir-path :templates) template)
            (File.)
            (#(str \( (slurp % :encoding (:encoding (config))) \) ))
            read-string))))

(defn write-out-dir [file str]
  (FileUtils/writeStringToFile
   (File. (:out-dir (config)) file) str (:encoding (config))))

(defn deploy-rsync [rsync out-dir host user deploy-dir]
  (let [cmd [rsync "-avz" "--delete" "--checksum" "-e" "ssh"
             out-dir (str user "@" host ":" deploy-dir)]]
    (info (:out (apply sh cmd)))))
