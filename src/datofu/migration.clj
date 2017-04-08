(ns datofu.migration
  (:require [datofu.schema.dsl :as sch]
            [datomic.api :as d]))

;; TODO test (Val, 02 Apr 2017)
(defn schema-tx
  []
  [(sch/part :datofu.parts/migration)
   (sch/attr :datofu.migration.impl/id :keyword :identity
     "the migration with this name has been run.")
   (sch/db-clj-fn :datofu.migration/ensure
     "Runs a named transaction if it's not been done already."
     '[db migr-id tx]
     '(when-not (seq (d/datoms db :avet :datofu.migration.impl/id migr-id))
        (cons
          [:db/add (d/tempid :datofu.parts/migration)
           :datofu.migration.impl/id migr-id]
          tx)))])

(defn already-run
  [db]
  (->>
    (d/datoms db :aevt :datofu.migration.impl/id)
    (sort-by :tx)
    (mapv (fn [[e a v tx]]
            {:datofu.migration/id v
             :tx tx}))))

(defn to-run-diagnosis
  [db migrs]
  (let [already-run (into #{}
                      (map :datofu.migration/id)
                      (already-run db))]
    (into [] (remove #(already-run (:datofu.migration/id %))) migrs)))

(defn install-and-migrate!
  "Basic high-level utility for installing a Datomic schema and install migrations in an indempotent way.
  * `conn` should be a Datomic connection
  * `schema-tx` should be a Datomic schema installation transaction (will be run before the migrations, should be idempotent.)
  * `migrs` is a seq of maps, representing migrations to be run in order.

  Each map of `migrs` should have the following properties:
  * :datofu.migration/id (required):  a keyword, the id of the migration.
  * :datofu.migration/tx (required): a Datomic transaction request, to be run exactly once.
  * :datofu.migration/schema-tx (optional): a Datomic transaction request, installing a schema specific to this transaction.
  (will be run before the migrations, should be idempotent.)

  The migrations are run one after the other (since a migration may rely on the state
  of the database after a previous migration).

  The goal of this function is to easily sync a Datomic system to a given codebase.
  "
  [conn schema-tx migrs]
  (let [schema (into schema-tx
                 (mapcat ::schema-tx)
                 migrs)
        db (d/db conn)
        to-be-run (to-run-diagnosis db migrs)
        installed-schema (try
                           (select-keys @(d/transact conn schema)
                             [:db-before :db-after])
                            (catch Throwable err
                              (throw (ex-info "Error when transacting schema installation."
                                       {:schema-tx schema-tx}
                                       err))))
        ran-migrations (->> to-be-run
                         (mapv (fn run-migration [{:as m, id :datofu.migration/id}]
                                 (let [{:keys [db-before db-after tx-data]}
                                       (try
                                         @(d/transact conn
                                            [[:datofu.migration/ensure id
                                              (:datofu.migration/tx m)]])
                                         (catch Throwable err
                                           (throw (ex-info (str "Error when transacting migration " id)
                                                    m err))))]
                                   {:datofu.migration/id id
                                    :db-before db-before :db-after db-after
                                    :datofu.migration/wasRun?
                                    (->> tx-data
                                      (some (fn [[e a v t op]]
                                              (and
                                                (->> a (d/ident db-after) (= :datofu.migration.impl/id))
                                                (= v id)
                                                (= op true)))))
                                    }))))]
    {:datofu.migration/installed-schema installed-schema
     :datofu.migration/submitted-migrations ran-migrations}
    ))
