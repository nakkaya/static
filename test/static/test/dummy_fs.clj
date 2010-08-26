(ns static.test.dummy-fs
  (:use [clojure.contrib.io :only [delete-file-recursively]])
  (:import (java.io File)))

(defn- create-resources []
  (.mkdir (File. "resources/"))
  (.mkdir (File. "resources/site/"))
  (.mkdir (File. "resources/public/"))
  (.mkdir (File. "resources/posts/"))
  (.mkdir (File. "resources/templates/")))

(defn- create-site []
  (spit (File. "resources/site/dummy.markdown")
	"---
title: dummy content
description: some dummy desc
tags: unit test
template: temp.clj
---

Some dummy file for unit testing."))

(defn- create-dummy-posts []
  (spit 
   (File. "resources/posts/2050-01-01-dummy-future-post-1.markdown")
   "---
title: dummy future post 1
tags: 4673 9c0e same
template: temp.clj
---

text dummy post 1")

  (spit 
   (File. "resources/posts/2050-02-02-dummy-future-post-2.markdown")
   "---
title: dummy future post 2
tags: e8edaab7 25e9 same
template: temp.clj
---

text dummy post 2")

  (spit 
   (File. "resources/posts/2050-03-03-dummy-future-post-3.markdown")
   "---
title: dummy future post 3
tags: 45f5 8a0c same
template: temp.clj
---

text dummy post 3")

  (spit 
   (File. "resources/posts/2050-04-04-dummy-future-post-4.markdown")
   "---
title: dummy future post 4
tags: 4784d643 e4e8 same
template: temp.clj
---

text dummy post 4"))

(defn- create-template []
  (spit (File. "resources/templates/temp.clj") "content"))

(defn- create-static-file []
  (spit (File. "resources/public/dummy.static") "Hello, World!!"))

(defn- create-config []
  (spit (File. "config.clj") 
	"
[:site-title \"Dummy Site\"
 :site-description \"Dummy Description\"
 :site-url \"http://www.dummy.com\"
 :in-dir \"resources/\"
 :out-dir \"html/\"
 :default-template \"temp.clj\"
 :encoding \"UTF-8\"
 :posts-per-page 2
 :blog-as-index true]"))

(defn create-dummy-fs []
  (create-resources)
  (create-site)
  (create-static-file)
  (create-dummy-posts)
  (create-template)
  (create-config))
