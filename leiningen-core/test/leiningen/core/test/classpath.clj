(ns leiningen.core.test.classpath
  (:use [clojure.test]
        [leiningen.core.classpath])
  (:require [clojure.java.io :as io]
            [clojure.set :as set]
            [leiningen.core.user :as user]
            [leiningen.core.project :as project]))

(defn m2-file [f]
  (io/file (System/getProperty "user.home") ".m2" "repository" f))

(def project {:dependencies '[[org.clojure/clojure "1.3.0"]
                              [ring/ring-core "1.0.0-RC1"
                               :exclusions [commons-codec]]]
              :repositories (:repositories project/defaults)
              :root "/tmp/lein-sample-project"
              :source-paths ["/tmp/lein-sample-project/src"]
              :resource-paths ["/tmp/lein-sample-project/resources"]
              :test-paths ["/tmp/lein-sample-project/test"]})

(deftest test-resolve-deps
  (doseq [f (reverse (file-seq (io/file (:root project))))]
    (when (.exists f) (io/delete-file f)))
  (is (= #{(m2-file "org/clojure/clojure/1.3.0/clojure-1.3.0.jar")
           (m2-file "commons-io/commons-io/1.4/commons-io-1.4.jar")
           (m2-file "javax/servlet/servlet-api/2.5/servlet-api-2.5.jar")
           (m2-file "ring/ring-core/1.0.0-RC1/ring-core-1.0.0-RC1.jar")
           (m2-file (str "commons-fileupload/commons-fileupload/1.2.1/"
                         "commons-fileupload-1.2.1.jar"))}
         (resolve-dependencies :dependencies project))))

(deftest test-dependency-hierarchy
  (doseq [f (reverse (file-seq (io/file (:root project))))]
    (when (.exists f) (io/delete-file f)))
  (is (= '{[org.clojure/clojure "1.3.0"] nil
          [ring/ring-core "1.0.0-RC1"
           :exclusions [[commons-codec]]]
          {[commons-fileupload "1.2.1"] nil
           [commons-io "1.4"] nil
           [javax.servlet/servlet-api "2.5"] nil}}
         (dependency-hierarchy :dependencies project))))

(def directories
  ["/tmp/lein-sample-project/test"
   "/tmp/lein-sample-project/src"
   "/tmp/lein-sample-project/resources"])

(def libs
  #{(str (m2-file "commons-io/commons-io/1.4/commons-io-1.4.jar"))
    (str (m2-file "javax/servlet/servlet-api/2.5/servlet-api-2.5.jar"))
    (str (m2-file "ring/ring-core/1.0.0-RC1/ring-core-1.0.0-RC1.jar"))
    (str (m2-file "commons-fileupload/commons-fileupload/1.2.1/commons-fileupload-1.2.1.jar"))
    (str (m2-file "org/clojure/clojure/1.3.0/clojure-1.3.0.jar"))})

(deftest test-classpath
  (let [classpath (get-classpath project)]
    (is (= (set classpath) (into libs directories)))
    (is (= directories (take 3 classpath)))
    (is (= libs (set (drop 3 classpath))))))

(deftest test-checkout-deps
  (let [d1 (io/file (:root project) "checkouts" "d1")]
    (try
      (.mkdirs d1)
      (spit (io/file d1 "project.clj")
            (pr-str '(defproject hello "1.0")))
      (is (= (for [path ["src" "resources" "target/classes"]]
               (format "/tmp/lein-sample-project/checkouts/d1/%s" path))
             (#'leiningen.core.classpath/checkout-deps-paths project)))
      (finally
       ;; can't recur from finally
       (dorun (map #(.delete %) (reverse (file-seq d1))))))))

(deftest test-add-auth
  (with-redefs [user/profiles (constantly
                               {:auth
                                {:repository-auth
                                 {"https://sekrit.info/repo"
                                  {:username "milgrim" :password "reindur"}}}})]
    (is (= [["sonatype" {:url "https://oss.sonatype.org/"}]
            ["internal" {:password "reindur" :username "milgrim"
                         :url "https://sekrit.info/repo"}]]
           (add-auth [["sonatype" {:url "https://oss.sonatype.org/"}]
                      ["internal" {:url "https://sekrit.info/repo"}]])))))
