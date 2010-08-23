(ns static.test.dummy-fs
  (:use [clojure.contrib.io :only [delete-file-recursively]])
  (:import (java.io File)))

(defn- create-resources []
  (.mkdir (File. "resources/"))
  (.mkdir (File. "resources/site/"))
  (.mkdir (File. "resources/public/"))
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

(defn- create-template []
  (spit (File. "resources/templates/temp.clj") "content"))

(defn- create-static-file []
  (spit (File. "resources/public/dummy.static") "Hello, World!!"))

(defn create-dummy-fs []
  (create-resources)
  (create-site)
  (create-static-file)
  (create-template))
