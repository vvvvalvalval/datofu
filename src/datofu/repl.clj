(ns datofu.repl
  (:require [datomic.api :as d]))

(defn doc
  "Given a Database `db` and an Entity Identifier `e`
  (typically a keyword naming an attribute),
  prints the :db/doc of `e` if it has one in `db`."
  [db e]
  (println (:db/doc (d/entity db e))))
