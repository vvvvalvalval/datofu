(ns datofu.schema.dsl.impl)

(defmulti dsl* (fn [m kw] kw) :default ::unknown)

(defmethod dsl* ::unknown
  [m c]
  (throw (ex-info (str "unknown DSL clause: " c) {:m m :c c})))

(defn add-clause [m c]
  (cond
    (string? c) (assoc m :db/doc c)
    (map? c) (merge m c)
    (keyword? c) (dsl* m c)
    :else (throw (ex-info (str "unknown DSL clause: " c) {:m m :c c}))))

(def keys-comp
  (let [priorities {:db/ident -5
                    :db/valueType -4
                    :db/cardinality -3
                    :db/unique -2
                    :db/doc -1
                    :db/id 1
                    :db.install/_partition 1
                    :db.install/_attribute 1}]
    (fn [k1 k2]
      (let [c1 (- (priorities k1 0) (priorities k2 0))]
        (if-not (zero? c1)
          c1
          (compare k1 k2))))))

(defn add-clauses [m cs]
  (reduce add-clause (into (sorted-map-by keys-comp) m) cs))
