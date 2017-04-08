(ns datofu.uid
  "Utilities for generating unique identifiers."
  (:require [datofu.schema.dsl :as sch]
            [datomic.api :as d]
            [clojure.string :as str]))

(defn schema-tx
  []
  [;; storing the state in a separate partitions so that it won't pollute the Peers.
   (sch/part :datofu.parts/uid)
   (sch/attr :datofu.uid.impl.cell.string/id :string :identity)
   (sch/attr :datofu.uid.impl.cell.string/count :long)

   (sch/db-fn ::add-sequential-strings
     "Given an entity identifier `e`, a string-valued attribute `a`, and a string `base`,
     generates a unique `a` value for `e` from `base`.

     The typical use case is to generate a string id from a human-readable root.

     The strategy for avoiding collisions are avoided consists of appending a dash ('-') and number at the end of `base`;
      if `base` already has such a suffix, it is escaped by doubling the dash.
     (Of course, uniqueness is only guaranteed if you generate all the values via this function.).

     An optional namespace string `ns` may be provided (defaults to \"\").
     Collisions are only prevented for call to the same namespace.

     Caveat: there can only be one call to this transaction fn for given base and namespaces in a given transaction,
     therefore if there are several calls in a transaction you will need to dedupe the bases upstream."
     {:lang "clojure"
      :params '[db ns base e+as]
      :requires '[[clojure.string :as str]
                  [datomic.api :as d]]
      :code '(let [normalized-base (if-let [match (re-matches #"(.*)\-(\d+)" base)]
                                     (let [[_ prefix digits] match]
                                       (str prefix "--" digits))
                                     base)
                   escape (fn [s] (cond-> s
                                    (str/includes? s "\\") (str/replace #"\\" "\\\\")
                                    (str/includes? s ":") (str/replace #":" "\\\\:")))
                   id (str (if ns (escape ns) "") ":" (escape normalized-base))
                   n (-> (d/datoms db :eavt
                           [:datofu.uid.impl.cell.string/id id]
                           :datofu.uid.impl.cell.string/count)
                       first :v (or 0))]
               (into [{:db/id (d/tempid :datofu.parts/uid)
                       :datofu.uid.impl.cell.string/id id
                       :datofu.uid.impl.cell.string/count (+ n (count e+as))}]
                 (map-indexed (fn [i [e attr]]
                                (let [m (+ n i)
                                      generated (cond-> normalized-base
                                                  (> m 0) (str "-" m))]
                                  [:db/add e attr generated])))
                 e+as))})

   (sch/attr :datofu.uid.impl.cell.long/ns :string :identity)
   (sch/attr :datofu.uid.impl.cell.long/count :long)
   (sch/db-clj-fn ::add-sequential-longs
     ;; TODO doc
     ""
     '[db ns e+as]
     '(let [ns (or ns "")
            n (-> (d/datoms db :eavt
                    [:datofu.uid.impl.cell.long/ns ns]
                    :datofu.uid.impl.cell.long/count)
                first :v (or 0))]
        (into [{:db/id (d/tempid :datofu.parts/uid)
                :datofu.uid.impl.cell.long/ns ns
                :datofu.uid.impl.cell.long/count (+ n (count e+as))}]
          (map-indexed (fn [i [e attr]]
                         (let [m (+ n i)]
                           [:db/add e attr m])))
          e+as)
        ))
   ])
