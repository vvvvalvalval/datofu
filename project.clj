(defproject vvvvalvalval/datofu "0.1.0"
  :description "Battery-included Datomic"
  :url "https://github.com/vvvvalvalval/datofu"
  :license {:name "MIT"
            :url "https://opensource.org/licenses/MIT"}
  :dependencies [[org.clojure/clojure "1.8.0"]]
  :profiles {:dev
             {:dependencies [[org.clojure/test.check "0.9.0"]
                             [com.datomic/datomic-free "0.9.5561"]
                             [vvvvalvalval/datomock "0.2.0"]
                             [criterium "0.4.3"]]}})
