(defproject datofu "0.1.0"
  :description "Battery-included Datomic"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]]
  :profiles {:dev
             {:dependencies [[org.clojure/test.check "0.9.0"]
                             [com.datomic/datomic-free "0.9.5561"]
                             [vvvvalvalval/datomock "0.2.0"]
                             [criterium "0.4.3"]]}})
