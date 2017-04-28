(ns atlas.db
  (:require [monger.core :as mg]
            [monger.collection :as mc]))

(let [mongo-addr (or (System/getenv "MONGO_PORT_27017_TCP_ADDR") "localhost")
      mongo-port (or (System/getenv "MONGO_PORT") "27017")
      connection (mg/connect-via-uri (str "mongodb://" mongo-addr ":" mongo-port "/atlas"))]
  (defonce conn (:conn connection))
  (defonce db (:db connection)))
