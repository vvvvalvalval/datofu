# Datofu

> There's a :db/fn for that.

[![Clojars Project](https://img.shields.io/clojars/v/vvvvalvalval/datofu.svg)](https://clojars.org/vvvvalvalval/datofu)

This library provides common utilities for working with Datomic,
 mostly in the form of [database functions](http://docs.datomic.com/database-functions.html).

Project status: alpha. Requires Datomic 0.9.5561 or higher.

**Features:**
* [Generating unique, readable ids](https://github.com/vvvvalvalval/datofu#generating-unique-readable-ids)
* [Writing your schema concisely](https://github.com/vvvvalvalval/datofu#writing-your-schema-concisely-in-clojure)
* [Schema evolution and migrations](https://github.com/vvvvalvalval/datofu#managing-data-schema-evolutions)
* [Writing migrations in Datalog](https://github.com/vvvvalvalval/datofu#writing-migrations-in-datalog)
* [Array-like data structures](https://github.com/vvvvalvalval/datofu#implementing-ordered-to-many-relationships-with-an-array-data-structure)
* [Resetting to-many relationships](https://github.com/vvvvalvalval/datofu#resetting-to-many-relationships)

## Usage

### Installation

The functionality of Datofu is spread in completely independent namespaces.
 You usually need to install some [Datomic schema](http://docs.datomic.com/schema.html) 
 before you can use a particular feature; you can obtain the schema installation [transaction request](http://docs.datomic.com/transactions.html)
  by calling `(datofu.{{ns}}/schema-tx)`, e.g:
 
```clojure
(datofu.coll.array/schema-tx)
=>
[{:db/ident :datofu.coll.array/cells,
  :db/valueType :db.type/ref,
  :db/cardinality :db.cardinality/many,
  :db/isComponent true}
 {:db/ident :datofu.coll.array.cell/index, :db/valueType :db.type/long, :db/cardinality :db.cardinality/one}
 {:db/ident :datofu.coll.array.cell/bigdec, :db/valueType :db.type/bigdec, :db/cardinality :db.cardinality/one}
 {:db/ident :datofu.coll.array.cell/bigint, :db/valueType :db.type/bigint, :db/cardinality :db.cardinality/one}
 ;; ...
```

If you want to adopt all Datofu features, you can call `(datofu.all/schema-tx)`.

The transaction requests returned by `(datofu.{{...}}/schema-tx)` are always idempotent - 
 you can safely `transact` them several times in a row. They usually only create a bunch of database functions, 
 attributes, partitions and named entities. All the created idents are namespaced-qualified, 
 so there is no risk for name collision.

### Generating unique, readable ids

While `datomic.api/squuid` lets you create unique ids that require no coordination to avoid conflicts, 
you sometimes need to generate human-readable ids, e.g for SEO purposes. 

The common approach in this case is to start from a *slug* string and make it unique by 
appending a sequential number to it. The `:datofu.uid/add-sequential-strings` database function 
lets you do exactly that:

```clojure
[[:datofu.uid/add-sequential-strings
  nil "my-human-readable-id" [["a" :my.ent/id]
                              ["b" :my.ent/id]]]]
;; will expand to something like:
[[:db/add "a" :my.ent/id "my-human-readable-id"]
 [:db/add "b" :my.ent/id "my-human-readable-id-1"]
 ,,,] 
```

Notes:
* the first argument to `:datofu.uid/add-sequential-strings` is an optional 'namespace' string (if `nil`, defaults to `""`). 
 The generated values are only unique inside a given namespace.
* the second argument is the *base* for the id. Don't make several calls with the same base and namespace in a given transaction!
* the last argument is a *seq* of (entity, attribute) tuples.
* the attributes are not required to be `:db/unique`, but it's usually the use case.

There is a similar database function for generating sequential `:db.type/long` values:

```clojure 
[[:datofu.uid/add-sequential-longs
  nil [["a" :my.ent/long-id]
       ["b" :my.ent/long-id]]]]
;; will expand to something like:
[[:db/add "a" :my.ent/long-id 0]
 [:db/add "b" :my.ent/long-id 1]]
```

### Writing your schema concisely in Clojure

Writing your Datomic schema in EDN form can be very verbose, 
 so if you're OK with writing your schema in Clojure code ([not everyone is!](http://stackoverflow.com/questions/31416378/recommended-way-to-declare-datomic-schema-in-clojure-application)),
 Datofu provides a lightweight, unopinionated API to write your schema installation transactions more concisely (no macros!).
 
Example:

```clojure
(require '[datofu.schema.dsl :refer :all])

(def my-schema
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
   (part :my.app/persons "Partition for saving Person data.")])
```

See the docstring of `datofu.schema.dsl/attr` for more details.
 
Note that there are several Datomic schema DSL libraries out there, with varying opinions
 ([Adi](https://github.com/zcaudate/adi), [datomic-schema](https://github.com/Yuppiechef/datomic-schema),
 [Tupelo-Datomic](https://github.com/cloojure/tupelo-datomic), [spec-tacular](https://github.com/SparkFund/spec-tacular)
 , ...). 
 The one provided by Datofu is very 'close to the metal'.

### Managing data schema evolutions

Compared to managing a SQL database, it's much rarer in Datomic to need to write migrations as your schema evolves.

You can usually just install the attributes corresponding to new functionality and ignore the ones you don't use anymore.
 Unless you make [alterations](http://docs.datomic.com/schema.html) to your attributes, installing your attributes is an idempotent operation, 
 so you can just reinstall all of your schema at each deployment.
 
However, sometimes, you need to run non-idempotent migrations on your data: filling in default values for a new attribute,
 cleaning up some bad data, installing initial data, etc. Datofu provides some helpers to help you manage that.

#### Ensuring a named migration runs exactly once

*Inspired by the [Conformity](https://github.com/rkneufeld/conformity) library.*

The `:datofu.migration/ensure` database function ensures a named transaction runs exactly once.
It accepts a *name* for the transaction (a keyword) and a transaction request.
When the function executes, it looks up whether that name has already been run, and executes the transaction if that's not the case. 

Example:

```clojure 
[[:datofu.migration/ensure :my-migration-name
  [[:my-migration-fn ,,,]]]]
```

#### Writing migrations in Datalog

Writing a specific database function for each migration can be tedious and operationally constraining 
 (a database function can't be run in the same migration where it's installed).

On the other hand, many migrations can be written directly in the form of Datalog queries, 
 so all you need is a database function to run datalog queries!
 Datofu provides that with `:datofu.utils/datalog-tx`
 
For instance, imagine that all the Purchase entities in your e-commerce database have 
their `:purchase/amount` in US dollars, and you need them to be in cents. You can write this migration as:

```clojure
[[:datofu.utils/datalog-tx 
  '[:find ?op ?e ?a ?v :in $ ?a :where
    [(ground :db/add) ?op]
    [?e ?a ?dollars]
    [(* 100 ?dollars) ?v]]
  [:purchase/amount]]]
```

Most migrations consist only of `:db/add` operations, so Datofu also provides `:datofu.utils/datalog-add`:

```clojure
[[:datofu.utils/datalog-add 
  '[:find ?e ?a ?v :in $ ?a :where
    [?e ?a ?dollars]
    [(* 100 ?dollars) ?v]]
  [:purchase/amount]]]
```

*Note: you can use these database functions for other purposes than migrations, 
but be aware that Datalog can have some overhead compared to the other querying APIs of Datomic, 
so you don't want to run too much of it in your Transactor - it's OK for rare events like migrations.* 

#### Setting a default value for a new attribute

In many cases, all you need from a migration is setting a default value for a new attribute.
 Datofu provides `:datofu.utils/add-default-attr-value` for that.
 
For instance, imagine that you have added a `:user/language` attribute to your schema, 
and want to set it by default to `"en-UK"` for all entities that don't already have this attribute and have a 
`:user/id` attribute:

```clojure
[[:datofu.utils/add-default-attr-value
  :user/id :user/language "en-UK"]]
```

#### Orchestrating schema installation and migrations

*Note: this feature is especially opiniated regarding your architecture and deployment process.*

`datofu.migration/install-and-migrate!` is a high-level utility for both syncing up a Datomic connection to
 a Clojure codebase by installing the necessary schema and migrations.
 You give it a Datomic connection, a transaction request for installing your schema (must be idempotent),
 and a seq of maps representing specifications of migrations. 

Here is a complete example:

```clojure
(ns myapp.schema
  (:require [datofu.schema.dsl :as sch]
            [datofu.all]))

(defn schema-tx
  []
  (concat
    (datofu.all/schema-tx)
    [(sch/attr :user/id :db.type/uuid :db.unique/identity)
     (sch/attr :user/email :db.type/string)
     (sch/attr :user/name :db.type/string)
     (sch/to-one :user/preferred-language)
     (sch/attr :language/code :db.type/string :db.unique/identity)]))


(ns myapp.schema.migrations
  (:require [datofu.migration :as migr]
            [datofu.schema.dsl :as sch]))

(def migrations 
  [{::migr/id :myapp.migrations/normalize-user-emails
    ::migr/doc 
    "We used to not normalize emails, so this migration 
    cleans up the data by normalizing all emails."
    ::migr/schema-tx
    [(sch/db-fn :myapp.fns/normalize-user-emails
       "Normalizes all user emails by lowercasing and trimming them."
       {:lang "clojure"
        :requires '[[clojure.string :as str]
                    [datomic.api :as d]]
        :params '[db]
        :code
        '(for [[e v] (d/q '[:find ?user ?email :where
                            [?user :user/email ?email]]
                       db)]
           [:db/add e :user/email
            (-> v str/lower-case str/trim)])
        })]
    ::migr/tx
    [[:myapp.fns/normalize-user-emails]]}
   {::migr/id :myapp.migrations/add-default-preferred-language 
    ::migr/doc 
    "After adding the :user/preferred-language attribute, we set the 
    default preferred language to 'en-UK'"
    ::migr/tx
    [[:datofu.utils/add-default-attr-value 
      :user/id :user/preferred-language 
      {:language/code "en-UK"}]]}])


(ns myapp.server.init
  (:require [datofu.migration :as migr]
            [myapp.schema]
            [myapp.schema.migrations]))

(defn init! [conn]
  (migr/install-and-migrate! 
    conn
    (myapp.schema/schema-tx)
    myapp.schema.migrations/migrations))
```

### Implementing ordered, to-many relationships with an array data structure

In Datomic, it's not trivial to encode an *ordered* to-many relationship. 
 A common workaround is to have entities form a data structure like an array or a linked list.

Datofu gives you attributes for creating generic arrays in the `datofu.coll.array` namespaces.

Example: a Person having a ReadingList which is an ordered sequence of Books.
 
```clojure
;; schema 1: :person/reading-list is a to-one, ref-typed, component attribute.
;; The Reading list is an 'array' entity.
[{:db/id "john doe"
  :person/reading-list
  {:datofu.coll.array/cells
   [{:datofu.coll.array.cell/index 0
     :datofu.coll.array.cell/ref
     {:book/title "Shantaram"}}
    {:datofu.coll.array.cell/index 1
     :datofu.coll.array.cell/ref
     {:book/title "American Gods"}}
    {:datofu.coll.array.cell/index 2
     :datofu.coll.array.cell/ref
     {:book/title "The Erl-King"}}]}}]
     
;; schema 2: :person.reading-list/books is a to-many, ref-typed, component attribute.
;; There is no concrete ReadingList entity.
[{:db/id "john doe"
  :person.reading-list/books
  [{:datofu.coll.array.cell/index 0
    :datofu.coll.array.cell/ref
    {:book/title "Shantaram"}}
   {:datofu.coll.array.cell/index 1
    :datofu.coll.array.cell/ref
    {:book/title "American Gods"}}
   {:datofu.coll.array.cell/index 2
    :datofu.coll.array.cell/ref
    {:book/title "The Erl-King"}}]}]
```

### Resetting to-many relationships

In Datomic, to-many relationships are easy to manipulate with *incremental* operations
(adding/removing a link to/from the relationship). 
 Absolute operations (resetting the relationship to a set of datoms) are trickier,
 as they require to perform a diff to compute what datoms need to be added, retracted, or just left alone.
 
Datofu provides generic database functions for performing these operations in the 
 `datofu.rel` namespace. These functions tend to rely on explicit, external ids 
 instead of opaque identifiers (such as tempids, entity ids or lookup refs) so as to 
 make very few assumptions about entity lifecycle.
 
#### Clearing a to-many relationship 
 
`:datofu.rel/clear-to-many` removes all the datoms from a to-many relationship.
 Optionally, the entities at the v-side of the relationship can be retracted using `:db.fn/retractEntites`. 
 
Example:
 
```clojure 
[;; Gregory House got all his friends to run away from him
 [:datofu.rel/clear-to-many
  [:person/name "Gregory House"]
  :person/friends 
  false]
 
 ;; Hannibal Lecter doesn't just lose his friends, he makes them disappear
 [:datofu.rel/clear-to-many
  [:person/name "Hannibal Lecter"]
  :person/friends
  true]] 
```
 
#### Resetting a to-many, scalar-typed relationship

`:datofu.rel/reset-to-many-scalar` lets you reset a relationship for a *scalar* attribute (anything but ref-typed).

Example with BlogPosts having a set of Tags
 (`:blog.post/tags` is a to-many, string-typed attribute, i.e Tags are represented as String values in the schema):

```clojure
;; assume we have a blog post with a set of tags...
(d/q '[:find [?tag ...] :in $ ?blog-post :where 
       [?blog-post :blog.post/tags ?tag]]
  (d/db conn) [:blog.post/id 42])
=> ["datomic" "flowers"]  

;; then the author edits the blog post, resetting the tags in the process
@(d/transact conn
   [[:datofu.rel/reset-to-many-scalar
     :blog.post/id 42 ;; notice how we need to explicitly specify a :db/unique attribute identifying the entity. 
     :blog.post/tags ["datomic" "birds"]]])
     
(d/q '[:find [?tag ...] :in $ ?blog-post :where 
       [?blog-post :blog.post/tags ?tag]]
  (d/db conn) [:blog.post/id 42])
=> ["datomic" "birds"]  
```

#### Resetting a to-many, ref-typed relationship

`:datofu.rel/reset-to-many-ref` lets you reset a relationship for a ref-typed attribute.

Here is the same example as above, except that now Tags are represented as entities in the schema. 

```clojure
;; assume we have a blog post with a set of tags...
(d/q '[:find [?tagName ...] :in $ ?blog-post :where
       [?blog-post :blog.post/tags ?tag]
       [?tag :blog.tag/name ?tagName]]
  (d/db conn) [:blog.post/id 42])
=> ["datomic" "flowers"]

;; then the author edits the blog post, resetting the tags in the process
@(d/transact conn
   [[:datofu.rel/reset-to-many-ref
     :blog.post/id 42 ;; notice how we need to explicitly specify a :db/unique attribute identifying the entity. 
     :blog.post/tags
     false ;; we dont retract the Tag entities that get removed from the relationship
     :blog.tag/tagName 
     ;; notice how the v-side entities are represented in map form. The maps *have* to contain the :blog.tag/tagName key! 
     [{:blog.tag/tagName "datomic"}
      {:blog.tag/tagName "birds"}]]])

(d/q '[:find [?tagName ...] :in $ ?blog-post :where
       [?blog-post :blog.post/tags ?tag]
       [?tag :blog.tag/name ?tagName]]
  (d/db conn) [:blog.post/id 42])
=> ["datomic" "flowers"]
```

This API may seem uselessly verbose and unwieldy, but it is designed this way to prevent
 subtle edge cases around entity lifecycle and entity resolution. 

## License

Copyright © 2017 Valentin Waeselynck and contributors 

Distributed under the MIT License.
