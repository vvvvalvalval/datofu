(ns datofu.coll.array
  "An indexed data structure for implementing ordered relationships."
  (:require [datofu.schema.dsl :as sch]))

(defn schema-tx []
  [(sch/to-many :datofu.coll.array/cells :component)

   (sch/attr :datofu.coll.array.cell/index :db.type/long)

   (sch/attr :datofu.coll.array.cell/bigdec :db.type/bigdec)
   (sch/attr :datofu.coll.array.cell/bigint :db.type/bigint)
   (sch/attr :datofu.coll.array.cell/boolean :db.type/boolean)
   (sch/attr :datofu.coll.array.cell/bytes :db.type/bytes)
   (sch/attr :datofu.coll.array.cell/double :db.type/double)
   (sch/attr :datofu.coll.array.cell/float :db.type/float)
   (sch/attr :datofu.coll.array.cell/instant :db.type/instant)
   (sch/attr :datofu.coll.array.cell/keyword :db.type/keyword)
   (sch/attr :datofu.coll.array.cell/long :db.type/long)
   (sch/attr :datofu.coll.array.cell/string :db.type/string)
   (sch/attr :datofu.coll.array.cell/uri :db.type/uri)
   (sch/attr :datofu.coll.array.cell/uuid :db.type/uuid)
   (sch/to-one :datofu.coll.array.cell/ref)
   ])

