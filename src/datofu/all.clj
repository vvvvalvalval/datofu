(ns datofu.all
  (:require [datofu.coll.array]
            [datofu.migration]
            [datofu.rel]
            [datofu.uid]
            [datofu.utils]))

(defn schema-tx
  []
  (vec
    (concat
      (datofu.coll.array/schema-tx)
      (datofu.migration/schema-tx)
      (datofu.rel/schema-tx)
      (datofu.uid/schema-tx)
      (datofu.utils/schema-tx)
      )))
