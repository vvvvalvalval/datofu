(ns datofu.test.rel
  (:require [clojure.set :as cset]
            [clojure.test :as test :refer :all]
            [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [datomic.api :as d]
            [datomock.core :as dm]

            [datofu.schema.dsl :as sch]
            [datofu.rel :refer :all]
            ))

(def fresh-conn
  (let [base (dm/mock-conn)]
    @(d/transact base (schema-tx))
    #(dm/fork-conn base)))

(def reset-ref-prop
  (let [schema [(sch/attr :a/id :string :identity)
                (sch/to-many :a/bs)
                (sch/attr :b/id :string :identity)]
        as [{:a/id "a1"}
            {:a/id "a2"}]
        bs [{:b/id "b1"}
            {:b/id "b2"}
            {:b/id "b3"}
            {:b/id "b4"}
            {:b/id "b5"}]]
    (prop/for-all
      [as-init (gen/list-distinct (gen/elements as))
       bs-init (gen/list-distinct (gen/elements bs))
       rel-init (gen/list-distinct (gen/tuple (gen/elements as) (gen/elements bs)))
       a (gen/elements as)
       new-bs (gen/list-distinct (gen/elements bs))
       r? gen/boolean]
      (let [conn (fresh-conn)
            _ @(d/transact conn schema)
            {db1 :db-after} @(d/transact conn
                               (concat
                                 as-init bs-init
                                 (for [[a b] rel-init]
                                   (assoc a :a/bs [b]))))
            {db2 :db-after} @(d/transact conn
                               [[:datofu.rel/reset-to-many-ref
                                 :a/id (:a/id a)
                                 :a/bs r?
                                 :b/id new-bs]])]
        (and
          (=
            (->> (d/entity db2 [:a/id (:a/id a)])
              :a/bs (map :b/id) set)
            (->> new-bs (map :b/id) set))
          (or (not r?)
            (let [old-bs (->> (d/entity db1 [:a/id (:a/id a)])
                           :a/bs (map :b/id) set)
                  new-bs (->> (d/entity db2 [:a/id (:a/id a)])
                           :a/bs (map :b/id) set)
                  to-retract (cset/difference old-bs new-bs)]
              (->> to-retract
                (map #(d/entity db2 [:b/id (:b/id %)]))
                (every? nil?))
              )))
        ))))

(deftest reset-ref
  (is (-> (tc/quick-check 100 reset-ref-prop)
        :result (= true))))

(def reset-scalar-prop
  (let [schema [(sch/attr :a/id :string :identity)
                (sch/attr :a/bs :string :many)]
        as [{:a/id "a1"}
            {:a/id "a2"}]
        bs ["b1"
            "b2"
            "b3"
            "b4"
            "b5"]]
    (prop/for-all
      [as-init (gen/list-distinct (gen/elements as))
       bs-init (gen/list-distinct (gen/elements bs))
       rel-init (gen/list-distinct (gen/tuple (gen/elements as) (gen/elements bs)))
       a (gen/elements as)
       new-bs (gen/list-distinct (gen/elements bs))
       r? gen/boolean]
      (let [conn (fresh-conn)
            _ @(d/transact conn schema)
            {db1 :db-after} @(d/transact conn
                               (concat
                                 as-init
                                 (for [[a b] rel-init]
                                   (assoc a :a/bs [b]))))
            {db2 :db-after} @(d/transact conn
                               [[:datofu.rel/reset-to-many-scalar
                                 :a/id (:a/id a)
                                 :a/bs new-bs]])]
        (and
          (=
            (->> (d/entity db2 [:a/id (:a/id a)])
              :a/bs  set)
            (->> new-bs set)))
        ))))

(deftest reset-scalar
  (is (-> (tc/quick-check 100 reset-scalar-prop)
        :result (= true))))
