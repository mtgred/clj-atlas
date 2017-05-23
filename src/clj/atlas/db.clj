(ns atlas.db
  (:require [aero.core :refer [read-config]]
            [monger.core :as mg]
            [monger.collection :as mc]))

(let [{:keys [address port]} (:db (read-config "config.edn"))
      connection (mg/connect-via-uri (str "mongodb://" address ":" port "/atlas"))]
  (defonce conn (:conn connection))
  (defonce db (:db connection)))
