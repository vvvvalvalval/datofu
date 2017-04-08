(ns datofu.rel
  "Entity-relationship utilities."
  (:require [datofu.schema.dsl :as sch]))

;; TODO test (Val, 22 Mar 2017)
(defn schema-tx
  []
  [(sch/db-clj-fn ::clear-to-many
     "Given an entity identifier `e` and a cardinality-many attribute `a`,
     retracts all the datoms which have `e` as entity and `a` as attribute.

     In addition, if the `retract-target-entities?` is true, the values will
     be retracted using :db.fn/retractEntity (the presumption being that in this case
      `a` is also a ref-typed attribute.)"
     '[db e a retract-target-entities?]
     '(->> (d/datoms db :eavt e a) seq
        (map (fn [[e a v]]
               (if retract-target-entities?
                 [:db.fn/retractEntity v]
                 [:db/retract e a v])))))

   (sch/db-clj-fn ::reset-to-many-ref
     "Resets the set of entities which are related to `eid` via `ref-attr` to the set given by `val-maps`.
     Requires external ids on both e-side and v-side entities to avoid having to assume that the entities already exist.
     Assumptions:
     * `ref-attr` is a cardinality-many, ref-typed attribute.
     ;; TODO
     * [`e-id-attr` `e-id`] is a valid lookup-ref for the e-side entity (which may not have been created yet).
     * `val-maps` is a seq of transaction maps, all of which have the `v-id-attr` key provided.
     * `retract-target-entity?`: whether to call :db.fn/retractEntity on the old v-side entities which get removed from the relationship.
     * the old values of the relationship all have the `v-id-attr` attribute."
     '[db e-id-attr e-id ref-attr retract-target-entities? v-id-attr v-maps]
     '(let [e-id-attr (d/ident db e-id-attr)
            v-id-attr (d/ident db v-id-attr)
            ref-attr (d/ident db ref-attr)
            ent (d/entity db [e-id-attr e-id])
            new-ids (into #{} (map v-id-attr) v-maps)]
        (into
          [{e-id-attr e-id
            ref-attr v-maps}]
          (comp
            (map v-id-attr)
            (remove new-ids)
            (map (fn [id]
                   (if retract-target-entities?
                     [:db.fn/retractEntity [v-id-attr id]]
                     [:db/retract (:db/id ent) ref-attr [v-id-attr id]])
                   )))
          (get ent ref-attr))
        ))

   (sch/db-clj-fn ::reset-to-many-scalar
     "Given a id attribute and a value identifying a (potentially future) entity `e`,
     a scalar to-many attribute `attr`, and a seq of values `vs`,
     resets the set of values from e via ttr to exactly `vs`, by performing a diff and issuing the appropriate
     additions and retractions."
     '[db e-id-attr e-id attr vs]
     '(let [e-id-attr (d/ident db e-id-attr)
            attr (d/ident db attr)
            ent (d/entity db [e-id-attr e-id])
            vs (set vs)]
        (into
          [{e-id-attr e-id
            attr vs}]
          (comp
            (remove vs)
            (map (fn [v] [:db/retract (:db/id ent) attr v])))
          (get ent attr))
        ))
   ])
