# static

Static is a simple static site generator written in Clojure.
For a sample site build using static see
[http://nakkaya.com](nakkaya.com)

## Usage

Creating a static site usually involves the following,

 - Set up the basic structure of the site
 - Create some posts
 - Run your site locally to see how it looks
 - Deploy your site

A basic static site usually looks something like this:
     .
     |-- config.clj
     `-- resources
         |-- posts
         |   |-- 2009-04-17-back-up-and-restore-a-mysql-database.markdown
         |   |-- 2010-02-10-a-simple-clojure-irc-client.markdown
         |   `-- 2010-08-02-using-clojure-contrib-generic-interfaces.markdown
         |-- public
         |   `-- 404.html
         |-- site
         |   `-- index.markdown
         `-- templates
             `-- default.clj

An overview of what each of these does:

### config.clj

Contains a vector of configuration options.

 - :site-title - Title of the site.
 - :site-description - Description of the site.
 - :site-url - URL of the site
 - :in-dir - Directory containing site content by default *resources/*
 - :out-dir - Directory to save compiled files.
 - :default-template - Default template to use.
 - :encoding - Encoding to use for read write operations.
 - :posts-per-page - Number of posts in latest post pages.
 - :blog-as-index - If true use blog as index.

 - :host - remote host to deploy to.
 - :user - remote username
 - :port - Remote SSH port
 - :deploy-dir - Remote directory to deploy to.

### posts/

Folder containing posts, the format of these files are important, as
named as *YEAR-MONTH-DATE-title.MAKDOWN*.

### public/

Folder containing static data such as images, css, javascript
etc. Folder structure will be mirrored exactly.

### site/

Folder containing pages that are not posts.

### templates/

Folder containing templates that are used to render posts and pages with.

## Installation

You need to place the uberjar lein created to the folder containing
config.clj.

### Building the site

    java -jar static.jar -b

### Deploying the site

#### SFTP

    java -jar static.jar --ssh

#### RSYNC

    java -jar static.jar --rsync

## License

Copyright (C) 2010

Distributed under the Eclipse Public License, the same as Clojure.
