(ns atlas.data
  (:require [clj-http.client :as http]
            [monger.collection :as mc]
            [aero.core :refer [read-config]]
            [clojure.java.io :as io]
            [atlas.db :refer [db]]))

(let [intrinio (:intrinio (read-config "config.edn"))]
  (def auth [(:username intrinio) (:password intrinio)]))

(defn fetch
  ([endpoint] (fetch endpoint nil))
  ([endpoint query-params]
   (let [options (cond-> {:basic-auth auth}
                   query-params (assoc :query-params query-params))]
     (-> (http/get (str "https://api.intrinio.com/" endpoint) options)
         :body
         (json/read-str :key-fn keyword)))))

(defn refresh-tickers! []
 (let [{:keys [data total_pages]} (fetch "companies")]
   (mc/remove db "tickers")
   (mc/insert-batch db "tickers" data)
   (dotimes [i (dec total_pages)]
     (let [tickers (:data (fetch "companies" {"page_number" (+ i 2)}))]
       (mc/insert-batch db "tickers" tickers)))))

(defn fetch-company [ticker]
  (println "Fetching" ticker)
  (-> (fetch "companies" {"identifier" ticker})
      (dissoc :securities)))

(defn json-fetch! []
  (doseq [{:keys [ticker]} (mc/find-maps db "tickers")]
    (let [filename (str "data/" ticker ".json")]
     (when-not (.exists (io/file filename))
       (spit filename
             (fetch-company ticker))))))

