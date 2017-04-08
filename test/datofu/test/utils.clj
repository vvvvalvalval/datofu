(ns datofu.test.utils
  (:require [clojure.test :as test :refer :all]
            [datomic.api :as d]
            [datomock.core :as dm]

            [datofu.utils :refer :all]
            [datofu.schema.dsl :as sch]))

(def fresh-conn
  (let [base (dm/mock-conn)]
    @(d/transact base (schema-tx))
    #(dm/fork-conn base)))

(deftest adding-new-attribute
  (testing ":datofu.utils/add-default-attr-value adds a default value
  for entities that don't already have the attribute."
    (let [conn (fresh-conn)]
      ;; initial schema
      @(d/transact conn
         [(sch/attr :user/id :string :identity)
          (sch/attr :user/language :string)])
      ;; initial data
      @(d/transact conn
         [{:user/id "val"}
          {:user/id "solene"
           :user/language "fr-FR"}])
      ;; new attribute migration
      @(d/transact conn
         '[[:datofu.utils/add-default-attr-value
            :user/id :user/language "en-UK"]])
      (is
        (=
          (d/q '[:find ?id ?lang :where
                 [?u :user/id ?id]
                 [?u :user/language ?lang]]
            (d/db conn))
          #{["val" "en-UK"] ["solene" "fr-FR"]}))
      )))
