(ns integration-test
  (:require
   [babashka.fs :as fs]
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [clojure.test :refer [deftest is]]
   [zprint.core :as zprint])
  (:import
   (java.io File PushbackReader)
   (java.nio.file Path)))

(defn- file? [x] (instance? File x))
(defn- path? [x] (instance? Path x))

(s/def ::config (s/map-of keyword? some?))
(s/def ::dir path?)
(s/def ::edn ::config)
(s/def ::edn-file file?)
(s/def ::in string?)
(s/def ::in-file file?)
(s/def ::out string?)
(s/def ::out-file file?)
(s/def ::z string?)

(s/def ::prepared-case
  (s/keys :req-un [::config
                   ::dir
                   ::edn
                   ::edn-file
                   ::in
                   ::in-file
                   ::out
                   ::out-file]))

(s/def ::zprinted-case
  (s/merge ::prepared-case (s/keys :req-un [::z])))

(s/def ::test-case
  (s/or :prepared ::prepared-case
        :zprinted ::zprinted-case))

;;; ----------------------------------------------------------------------------

(def ^:private cases-dir
  (fs/file (System/getProperty "user.dir") "cases"))

(defn- read-edn
  [readable]
  (with-open [rdr (io/reader readable)]
    (edn/read {:readers *data-readers*} (PushbackReader. rdr))))

(s/fdef load-test-case
  :args (s/cat :dir fs/directory?)
  :ret  ::prepared-case)

(defn- load-test-case
  [dir]
  (let [edn-file (fs/file dir ".zprint.edn")
        edn      (read-edn edn-file)
        config   (assoc edn :parse-string? true)
        in-file  (fs/file dir "in.clj")
        in       (slurp in-file)
        out-file (fs/file dir "out.clj")
        out      (slurp out-file)]
    {:dir      dir
     :config   config
     :edn      edn
     :edn-file edn-file
     :in       in
     :in-file  in-file
     :out      out
     :out-file out-file}))

(defn- pad [s] (str s "\n"))

(s/fdef process-test-case
  :args (s/cat :c ::prepared-case)
  :ret  ::zprinted-case)

(defn- process-test-case
  "Format `in` string with `zprint` and return the result with a newline
  appended to match our test cases."
  [c]
  (assoc c :z (-> c :in (zprint/zprint-str (:config c)) pad)))

(s/fdef explain-err
  :args (s/cat :c ::zprinted-case)
  :ret  string?)

(defn- explain-err
  [c]
  (format (str/join "\n"
                    ["\033[1;31m==>\033[1;0m Mismatch for \"cases/%s\".\033[0m"
                     ""
                     "Wanted:"
                     ""
                     "%s"
                     ""
                     "Got:"
                     ""
                     "%s\n"])
          (fs/file-name (:dir c))
          (:out c)
          (:z c)))

;;; ----------------------------------------------------------------------------

(deftest integrate
  (doseq [dir   (fs/list-dir cases-dir)
          :when (fs/directory? dir)
          :let  [c (process-test-case (load-test-case dir))]]
    (when (is (s/valid? ::zprinted-case c)
              (s/explain-str ::zprinted-case c))
      (is (= (:out c) (:z c))
          (explain-err c)))))
