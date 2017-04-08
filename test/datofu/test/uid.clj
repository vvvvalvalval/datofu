(ns datofu.test.uid
  (:require [clojure.test :as test :refer :all]
            [datomic.api :as d]
            [datomock.core :as dm]

            [datofu.uid :refer :all]
            [datofu.schema.dsl :as sch]))

(def fresh-conn
  (let [base (dm/mock-conn)]
    @(d/transact base (schema-tx))
    #(dm/fork-conn base)))

(deftest gen-sequential-string-for
  (testing "appends numbers to values so as to avoid collisions."
    (let [conn (fresh-conn)]
      @(d/transact conn
         [(sch/attr :id :string "" {:db/unique :db.unique/identity})
          (sch/attr :slug :string)])

      (run! #(deref (d/transact conn %))
        [[{:db/id "a" :id "a"}
          [:datofu.uid/add-sequential-strings
           nil "hello" [["a" :slug]]]
          {:db/id "b" :id "b"}
          {:db/id "b1" :id "b1"}
          {:db/id "b2" :id "b2"}
          [:datofu.uid/add-sequential-strings
           nil "bonjour" [["b" :slug]
                          ["b1" :slug]
                          ["b2" :slug]]]]
         [{:db/id "a1" :id "a1"}
          [:datofu.uid/add-sequential-strings
           nil "hello" [["a1" :slug]]]
          {:db/id "c" :id "c"}
          [:datofu.uid/add-sequential-strings
           nil "" [["c" :slug]]]
          {:db/id "d" :id "d"}
          {:db/id "d1" :id "d1"}
          [:datofu.uid/add-sequential-strings
           "35 " "x-12" [["d" :slug]
                         ["d1" :slug]]]
          {:db/id "e" :id "e"}
          [:datofu.uid/add-sequential-strings
           "" "aaa-" [["e" :slug]]]
          {:db/id "f" :id "f"}
          [:datofu.uid/add-sequential-strings
           "ns1" "Guten Tag" [["f" :slug]]]
          {:db/id "f'" :id "f'"}
          [:datofu.uid/add-sequential-strings
           "ns2" "Guten Tag" [["f'" :slug]]]]
         [{:db/id "g" :id "g"}
          [:datofu.uid/add-sequential-strings
           "\\:" "s:fd\\" [["g" :slug]]]]
         [{:db/id "h" :id "h"}
          [:datofu.uid/add-sequential-strings
           ":" "s:fd\\" [["h" :slug]]]
          [:datofu.uid/add-sequential-strings
           nil "" []]]
         ])

      (is (->> (d/q '[:find [(pull ?e [:id :slug]) ...] :where
                      [?e :id ?id]]
                 (d/db conn))
            (sort-by :id) vec
            (= [{:id "a", :slug "hello"}
                {:id "a1", :slug "hello-1"}
                {:id "b", :slug "bonjour"}
                {:id "b1", :slug "bonjour-1"}
                {:id "b2", :slug "bonjour-2"}
                {:id "c", :slug ""}
                {:id "d", :slug "x--12"}
                {:id "d1", :slug "x--12-1"}
                {:id "e", :slug "aaa-"}
                {:id "f", :slug "Guten Tag"}
                {:id "f'", :slug "Guten Tag"}
                {:id "g", :slug "s:fd\\"}
                {:id "h", :slug "s:fd\\"}])
            )))
    ))

(deftest gen-sequential-long-for
  (testing "generates sequential long values in a given namespace."
    (let [conn (fresh-conn)]
      @(d/transact conn
         [(sch/attr :id :string "" {:db/unique :db.unique/identity})
          (sch/attr :n :long)])

      (run! #(deref (d/transact conn %))
        [[{:db/id "a" :id "a"}
          {:db/id "a1" :id "a1"}
          [:datofu.uid/add-sequential-longs
           nil [["a" :n]
                ["a1" :n]]]]
         [{:db/id "a2" :id "a2"}
          [:datofu.uid/add-sequential-longs
           nil [["a2" :n]]]
          {:db/id "b" :id "b"}
          [:datofu.uid/add-sequential-longs
           "ns1" [["b" :n]]]
          [:datofu.uid/add-sequential-longs
           "ns2" []]]
         ])

      (is (->> (d/q '[:find [(pull ?e [:id :n]) ...] :where
                      [?e :id ?id]]
                 (d/db conn))
            (sort-by :id) vec
            (= [{:id "a", :n 0}
                {:id "a1", :n 1}
                {:id "a2", :n 2}
                {:id "b", :n 0}])
            )))
    ))
