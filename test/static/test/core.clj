(ns static.test.core
  (:use [static.core] :reload)
  (:use [static.markdown] :reload)
  (:use [static.test.dummy-fs] :reload)
  (:use [clojure.test])
  (:use [clojure.contrib.io :only [delete-file-recursively]])
  (:import (java.io File)))

(defn dummy-fs-fixture [f]
  (create-dummy-fs)
  (create)
  (f)
  (delete-file-recursively (File. "resources/") true)
  (delete-file-recursively (File. "html/") true)
  (delete-file-recursively (File. "config.clj") true))

(use-fixtures :once dummy-fs-fixture)

(deftest test-markdown
  (let [[metadata content] (read-markdown "resources/site/dummy.markdown")] 
    (is (= "unit test"  (:tags metadata)))
    (is (= "some dummy desc" (:description metadata)))
    (is (= "dummy content" (:title metadata)))
    (is (= "Some dummy file for unit testing."
	   (re-find #"Some dummy file for unit testing." content)))))

(deftest test-process-site
  (let [html (File. "html/dummy.html")
	static (File. "html/dummy.static")] 
    (is (= true (.exists html)))
    (is (= true (.exists static)))
    (is (= "Some dummy file for unit testing."
	   (re-find #"Some dummy file for unit testing." (slurp html))))
    (is (= "Hello, World!!" (re-find #"Hello, World!!" (slurp static))))))
