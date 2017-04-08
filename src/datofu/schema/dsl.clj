(ns datofu.schema.dsl
  "Exposes a concise, function-based Clojure DSL for declaring Datomic schema transactions.
  See the doc of #'attr for more details."
  (:require [datomic.api :as d]
            [datofu.schema.dsl.impl :as dsli]))

(defn attr
  "Creates a attribute named `:ident` from the given DSL clauses.
  A clause can be:
  * a map, which will be merged into the map for the attribute (example: {:db/index true, :db/fulltext false})
  * a string, which will be added to the attribute as its :db/doc
  * a keyword, which will be interpreted specially.

  The allowed keywords and their meaning are:
  * :string / :db.type/string / :boolean / :db.type/boolean / ... : sets the :db/valueType of the attribute.
  See http://docs.datomic.com/schema.html for the full list of value types.
  * :one / db.cardinality/one: sets :db/cardinality to :db.cardinality/one (which is the default if unspecified)
  * :many / db.cardinality/many: sets :db/cardinality to :db.cardinality/many
  * :identity / :db.unique/identity: set :db/unique to :db.unique/identity
  * :value / :db.unique/value: set :db/unique to :db.unique/value
  * :component: sets :db/isComponent to true
  * :index: sets :db/index to true
  * :fulltext: sets :db/fulltext to true
  * :noHistory: sets :db/noHistory to true

  The clauses are applied in order, so a clause on the right may overrule a clause on its left."
  [ident & clauses]
  (dsli/add-clauses
    {:db/ident ident
     :db/cardinality :db.cardinality/one}
    clauses))

(defn to-one
  "Shorthand for a ref-typed, cardinality-one attribute"
  [ident & clauses]
  (apply attr ident
    (cons {:db/valueType :db.type/ref
           :db/cardinality :db.cardinality/one}
      clauses)))

(defn to-many
  "Shorthand for a ref-typed, cardinality-many attribute"
  [ident & clauses]
  (apply attr ident
    (cons {:db/valueType :db.type/ref
           :db/cardinality :db.cardinality/many}
      clauses)))

(defn named
  "Shorthand for named entities (defined with a keyword, e.g enums)"
  [ident & clauses]
  (dsli/add-clauses
    {:db/ident ident}
    clauses))

(defn part
  "Shorhand for creating partitions"
  [ident & clauses]
  (dsli/add-clauses
    {:db/id (d/tempid :db.part/db)
     :db/ident ident
     :db.install/_partition :db.part/db}
    clauses))

;; ------------------------------------------------------------------------------
;; DB functions

(defn db-fn
  "Shorthand for Datomic database functions.
  `config` is what should be passed to "
  ([ident doc config]
   (cond-> {:db/id (d/tempid :db.part/user)
            :db/ident ident
            :db/fn (d/function config)}
           doc (assoc :db/doc doc)))
  ([ident config]
   (db-fn ident nil config)))

;; TODO rename or stop using (Val, 26 Mar 2017)
(defn db-clj-fn "Shorthand for making a Clojure database function. Will require datomic.api with alias d/"
  ([ident doc params code]
   (db-fn ident doc {:lang     "clojure"
                     :params   params
                     :requires '([datomic.api :as d])
                     :code     code
                     }))
  ([ident params code]
   (db-clj-fn ident nil params code)))

;; ------------------------------------------------------------------------------
;; DSL clauses

;; :db/valueType
(doseq [kw [:bigdec :bigint :boolean :bytes
            :double :float :instant :keyword
            :long :ref :string :uri :uuid]]
  (defmethod dsli/dsl* kw [m _]
    (assoc m :db/valueType (keyword "db.type" (name kw)))))
(doseq [kw [:db.type/bigdec
            :db.type/bigint
            :db.type/boolean
            :db.type/bytes
            :db.type/double
            :db.type/float
            :db.type/instant
            :db.type/keyword
            :db.type/long
            :db.type/ref
            :db.type/string
            :db.type/uri
            :db.type/uuid]]
  (defmethod dsli/dsl* kw [m _]
    (assoc m :db/valueType kw)))

;; :db/cardinality
(doseq [[kw c] [[:one :db.cardinality/one]
                [:many :db.cardinality/many]]]
  (defmethod dsli/dsl* kw [m _]
    (assoc m :db/cardinality c))
  (defmethod dsli/dsl* c [m _]
    (assoc m :db/cardinality c)))

;; :db/value
(doseq [[kw u] [[:identity :db.unique/identity]
                [:value :db.unique/value]]]
  (defmethod dsli/dsl* kw [m _]
    (assoc m :db/unique u))
  (defmethod dsli/dsl* u [m _]
    (assoc m :db/unique u)))

;; misc
(defmethod dsli/dsl* :component [m _]
  (assoc m :db/isComponent true))

(defmethod dsli/dsl* :index [m _]
  (assoc m :db/index true))

(defmethod dsli/dsl* :fulltext [m _]
  (assoc m :db/fulltext true))

(defmethod dsli/dsl* :noHistory [m _]
  (assoc m :db/noHistory true))


(comment "schema example"
  [(attr :person/email :string :identity "This person's email")
   (attr :person/firstName :string :fulltext)
   (attr :person/lastName :db.type/string :fulltext)
   (to-one :person/gender)
   (named :person.gender/male)
   (named :person.gender/female "it's a girl")
   (to-many :person/friends "This person's friends")
   (to-many :person/tweets :component)
   (attr :tweet/id :uuid :identity)
   (attr :tweet/time :instant)
   (attr :tweet/tags :string :many :index)
   (db-fn :myapp.fns/renameTag
     "Renames old-tag to new-tag in all tweets where it occurs"
     '{:lang "clojure"
       :requires ([datomic.api :as d])
       :params [db old-tag new-tag]
       :code (->> (d/datoms db :avet :tweet/tags old-tag)
               (mapcat (fn [[e _ _ _]]
                         [[:db/retract e :tweet/tags old-tag]
                          [:db/add e :tweet/tags new-tag]])))
       })
   (part :my.app/persons "Partition for saving Person data.")]
  )

