(ns static.test.core
  (:use [static.core] :reload)
  (:use [static.io] :reload)
  (:use [static.test.dummy-fs] :reload)
  (:use [clojure.test])
  (:import (java.io File)
           (org.apache.commons.io FileUtils)))

(defn delete-file-recursively [f]
  (FileUtils/deleteDirectory f))

(defn dummy-fs-fixture [f]
  (create-dummy-fs)
  (create)
  (f)
  (delete-file-recursively (File. "resources/"))
  (delete-file-recursively (File. "html/"))
  (.delete (File. "config.clj")))

(use-fixtures :once dummy-fs-fixture)

(deftest test-markdown
  (let [[metadata content] (read-doc "resources/site/dummy.markdown")] 
    (is (= "unit test"  (:tags metadata)))
    (is (= "some dummy desc" (:description metadata)))
    (is (= "dummy content" (:title metadata)))
    (is (= "Some dummy file for unit testing."
	   (re-find #"Some dummy file for unit testing." content)))))

(deftest test-cssgen
  (let [[metadata content] (read-doc "resources/site/style.cssgen")]
    (is (= "font-size: 1em;" (re-find #"font-size: 1em;" content)))))

(deftest test-org
  (let [[metadata content] (read-doc (File. "resources/posts/2050-07-07-dummy-future-post-7.org"))] 
    (is (= "org-mode org-babel"  (:tags metadata)))
    (is (= "Dummy org-mode post" (:title metadata)))
    (is (= "Sum 1 and 2" (re-find #"Sum 1 and 2" content)))))

(deftest test-clj
  (let [[metadata content] (read-doc (File. "resources/site/dummy_clj.clj"))] 
    (is (= "Dummy Clj File" (:title metadata)))
    (is (= "Dummy Clj Content" (re-find #"Dummy Clj Content" content)))
    (is (= "<h3>" (re-find #"<h3>" content)))))

(deftest test-io
  (is (= (count (list-files :posts)) 7))
  (is (.exists (File. "html/first-alias/index.html")))
  (is (.exists (File. "html/a/b/c/alias/index.html")))
  (is (.exists (File. "html/second-alias/index.html"))))

(deftest test-rss-feed
  (let [rss (File. "html/rss-feed")
	content (slurp rss)] 
    (is (= true (.exists rss)))
    (is (= "<title>Dummy Site</title>"
    	   (re-find #"<title>Dummy Site</title>" content)))
    (is (= "<link>http://www.dummy.com</link>"
    	   (re-find #"<link>http://www.dummy.com</link>" content)))
    (is (= "<title>dummy future post 1</title>"
    	   (re-find #"<title>dummy future post 1</title>" content)))
    (is (= "http://www.dummy.com/2050/04/04/dummy-future-post-4/"
	   (re-find #"http://www.dummy.com/2050/04/04/dummy-future-post-4/" 
		    content)))))

(deftest test-site-map
  (let [sitemap (File. "html/sitemap.xml")
	content (slurp sitemap)] 
    (is (= true (.exists sitemap)))
    (is (= "<loc>http://www.dummy.com</loc>"
    	   (re-find #"<loc>http://www.dummy.com</loc>" content)))
    (is (= "http://www.dummy.com/2050/01/01/dummy-future-post-1/"
    	   (re-find #"http://www.dummy.com/2050/01/01/dummy-future-post-1/" 
		    content)))
    (is (= "<loc>http://www.dummy.com/dummy.html</loc>"
    	   (re-find #"<loc>http://www.dummy.com/dummy.html</loc>" 
		    content)))))

(deftest test-rss-feed
  (let [tags (File. "html/tags/index.html")
	content (slurp tags)] 
    (is (= 5 (count ((tag-map) "same"))))
    (is (= true (.exists tags)))
    (is (= "<a name=\"e4e8\">e4e8</a>" 
	   (re-find #"<a name=\"e4e8\">e4e8</a>" content)))
    (is (= "<a href=\"/2050/01/01/dummy-future-post-1/\">"
    	   (re-find #"<a href=\"/2050/01/01/dummy-future-post-1/\">" 
		    content)))))

(deftest test-latest-posts
  (let [page (File. "html/latest-posts/0/index.html")] 
    (is (= true (.exists page)))))

(deftest test-archives
  (let [index (File. "html/archives/index.html")
	a-2050-01 (File. "html/archives/2050/01/index.html")] 
    (is (= true (.exists index)))
    (is (= true (.exists a-2050-01)))))

(deftest test-process-posts
  (let [post1 (File. "html/2050/02/02/dummy-future-post-2/index.html")
	post2 (File. "html/2050/04/04/dummy-future-post-4/index.html")] 
    (is (= true (.exists post1)))
    (is (= true (.exists post2)))))

(deftest test-process-site
  (let [html (File. "html/dummy.html")
	static (File. "html/dummy.static")] 
    (is (= true (.exists html)))
    (is (= true (.exists static)))
    (is (= "Some dummy file for unit testing."
	   (re-find #"Some dummy file for unit testing." (slurp html))))
    (is (= "Hello, World!!" (re-find #"Hello, World!!" (slurp static))))))
