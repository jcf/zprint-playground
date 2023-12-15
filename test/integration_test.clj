(ns integration-test
  (:require
   [babashka.fs :as fs]
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.test :refer [deftest is]]
   [zprint.core :as zprint]
   [clojure.string :as str])
  (:import
   (java.io PushbackReader)))

(def ^:private cases-dir
  (fs/file (System/getProperty "user.dir") "cases"))

(defn- read-edn
  [readable]
  (with-open [rdr (io/reader readable)]
    (edn/read {:readers *data-readers*} (PushbackReader. rdr))))

(defn- load-test-case
  [dir]
  (let [edn-file (fs/file dir ".zprint.edn")
        in-file  (fs/file dir "in.clj")
        out-file (fs/file dir "out.clj")
        edn      (read-edn edn-file)
        in       (slurp in-file)
        out      (slurp out-file)]
    {:dir      dir
     :edn-file edn-file
     :in-file  in-file
     :out-file out-file
     :edn      edn
     :in       in
     :out      out}))

(defn- explain-err
  [c s]
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
          s))

(defn- format-str
  "Format `in` string with `zprint` and return the result with a newline
  appended to match our test cases."
  [s]
  (format "%s\n" (zprint/zprint-str s {:parse-string? true})))

;;; ----------------------------------------------------------------------------

(deftest integrate
  (doseq [dir   (fs/list-dir cases-dir)
          :when (fs/directory? dir)
          :let  [c (load-test-case dir)
                 s (format-str (:in c))]]
    (is (= (:out c) s)
        (explain-err c s))))
