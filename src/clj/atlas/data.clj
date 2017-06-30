(ns atlas.data
  (:require [clj-http.client :as http]
            [clojure.data.json :as json]
            [monger.collection :as mc]
            [monger.operators :refer [$gt]]
            [aero.core :refer [read-config]]
            [clojure.java.io :as io]
            [atlas.db :refer [db]]))

(let [intrinio (:intrinio (read-config "config.edn"))]
  (def auth [(:username intrinio) (:password intrinio)]))

(def companies (read-string (slurp "data/tickers.edn")))

(def daily ["adj_close_price"
            "52_week_high"
            "52_week_low"
            "pricetonextyearearnings"
            "dividendyield"
            "marketcap"])

(def quarterly ["freecashflow"
                "netdebt"
                "debttototalcapital"
                "capex"
                "totalrevenue"
                "netincome"
                "weightedavebasicsharesos"
                "bookvaluepershare"
                "roe"
                "roic"
                "grossmargin"
                "profitmargin"
                "short_interest"
                "days_to_cover"])

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

(defn json-fetch-companies! []
  (doseq [{:keys [ticker]} (mc/find-maps db "tickers")]
    (let [filename (str "data/companies/" ticker ".edn")]
     (when-not (.exists (io/file filename))
       (spit filename (fetch-company ticker))))))

(defn fetch-historical [ticker item]
  (println "Fetching" ticker item)
  (-> (fetch "historical_data" {"identifier" ticker
                                "item" item
                                "page_size" 1000})
      :data))

(defn json-fetch-historical! [ticker items]
  (doseq [item items]
    (let [filename (str "data/historical/" ticker "/" ticker " - " item ".edn")]
      (io/make-parents filename)
      (when-not (.exists (io/file filename))
        (spit filename (fetch-historical ticker item))))))

(defn json-fetch-all-historical! [companies]
  (loop [[ticker & tickers] companies]
    (let [error (atom false)]
      (try
        (json-fetch-historical! ticker quarterly)
        (catch Exception e (let [status (-> e ex-data :status)]
                             (println ticker "error" status)
                             (spit "data/error.log" (str ticker (ex-data e) "\n") :append true)
                             (when (= status 429)
                               (reset! error status)))))
      (when-not @error
        (recur tickers)))))


(defn json-fetch-macro! []
  (doseq [identifier ["$GDP" "$FEDFUNDS" "$PAYEMS" "$UNRATE"]]
    (let [filename (str "data/macro/" identifier ".edn")]
      (spit filename (fetch-historical identifier "level")))))




