(ns user
  (:require
   [clojure.spec.alpha :as s]
   [clojure.tools.namespace.repl]))

(s/check-asserts true)
(clojure.tools.namespace.repl/set-refresh-dirs "dev" "src" "test")
