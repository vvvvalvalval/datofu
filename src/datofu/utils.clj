(ns datofu.utils
  (:require [datofu.schema.dsl :as sch]
            [datomic.api :as d]))

(defn schema-tx
  []
  [(sch/db-clj-fn ::datalog-tx
     "Constructs a transaction request by running a Datalog query.
     "
     '[db query inputs]
     '(apply d/q query db inputs))
   (sch/db-clj-fn ::datalog-add
     "Given a Datalog query `query`, and a seq `inputs` of inputs for this query,
     will emit :db/add txes which e, a and v are the triples returned by running `(apply d/q query db inputs)`."
     '[db query inputs]
     '(for [[e a v] (apply d/q query db inputs)]
        [:db/add e a v]))
   (sch/db-clj-fn ::add-default-attr-value
     "Given an attribute `match-attr`, an attribute `attr`
      and a value `default-value` for `attr`, yields a transaction
      adding [attr default-value] to all entities having `match-attr` and not `attr`.

      Useful for migrations where `attr` is a new attribute "
     '[db match-attr attr default-value]
     '(for [e (d/q '[:find [?e ...] :in $ ?ma ?a :where
                      [?e ?ma]
                      (not [?e ?a])]
                 db match-attr attr)]
        {:db/id e
         attr default-value}))])

(comment
  ;; adding a :my.ent/createdAt time entity from txInstant
  ;; for all entities having the :my.ent/id attribute.
  '[[::datalog-add
     [:find ?e ?t-attr ?v :in $ ?id-attr ?t-attr :where
      [?e ?id-attr _ ?tx]
      [?tx :db/txInstant ?v]
      (not [?e ?t-attr])]
     [[:db/ident :my.ent/id] [:db.ident :my.ent/createdAt]]]]
  )
