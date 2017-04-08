(ns datofu.test.migration
  (:require [clojure.set :as cset]
            [clojure.test :as test :refer :all]
            [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [datomic.api :as d]
            [datomock.core :as dm]

            [datofu.schema.dsl :as sch]
            [datofu.all]
            [datofu.migration :as migr :refer :all]
            ))

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


(defn init! [conn]
  (migr/install-and-migrate!
    conn
    (schema-tx)
    migrations))

(deftest install-and-migrate-works
  (let [versions
        [{:schema
          (concat
            (datofu.all/schema-tx)
            [(sch/attr :user/id :db.type/string :db.unique/identity)
             (sch/attr :user/email :db.type/string)])
          :migrations
          (take 0 migrations)
          :data
          [{:user/id "john-doe"
            :user/email " John.Doe@gmail.com  "}]}
         {:schema
          (concat
            (datofu.all/schema-tx)
            [(sch/attr :user/id :db.type/string :db.unique/identity)
             (sch/attr :user/email :db.type/string)
             (sch/attr :user/name :db.type/string)])
          :migrations
          (take 1 migrations)
          :data
          [{:user/id "john-doe"
            :user/email "john.doe@gmail.com"
            :user/name "John Doe"}]}
         {:schema
          (concat
            (datofu.all/schema-tx)
            [(sch/attr :user/id :db.type/string :db.unique/identity)
             (sch/attr :user/email :db.type/string)
             (sch/attr :user/name :db.type/string)
             (sch/to-one :user/preferred-language)
             (sch/attr :language/code :db.type/string :db.unique/identity)])
          :migrations
          migrations
          :data
          [{:user/id "john-doe"
            :user/email "john.doe@gmail.com"
            :user/name "John Doe"
            :user/preferred-language
            {:language/code "en-US"}}]}]
        conn (dm/mock-conn)]
    (testing "from one version to the other"
      (is
        (->>
          (for [from versions
                to versions]
            (let [conn (dm/fork-conn conn)]
              (migr/install-and-migrate! conn (:schema from) (:migrations from))
              @(d/transact conn (:data from))
              (migr/install-and-migrate! conn (:schema to) (:migrations to))))
          (mapv (fn [res]
                  (->> res ::migr/submitted-migrations
                    (mapv #(select-keys % [::migr/id ::migr/wasRun?])))))
          (= [[]
              [{:datofu.migration/id :myapp.migrations/normalize-user-emails, :datofu.migration/wasRun? true}]
              [{:datofu.migration/id :myapp.migrations/normalize-user-emails, :datofu.migration/wasRun? true}
               {:datofu.migration/id :myapp.migrations/add-default-preferred-language, :datofu.migration/wasRun? true}]
              []
              []
              [{:datofu.migration/id :myapp.migrations/add-default-preferred-language, :datofu.migration/wasRun? true}]
              []
              []
              []])))
      )))
