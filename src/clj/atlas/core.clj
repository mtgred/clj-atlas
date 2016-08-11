(ns atlas.core
  (:require [org.httpkit.server :refer [run-server]]
            [monger.core :as mg]
            [monger.collection :as mc]
            [ring.util.response :as resp]
            [ring.middleware.json :as json]
            [bidi.ring :as bidi]))

(defonce server (atom nil))

(defn index-handler [req]
  (resp/file-response "index.html" {:root "resources/public"}))

(defn json-handler [{:keys [route-params]}]
  (resp/response (map #(dissoc % :_id) (mc/find-maps db (:coll route-params)))))

(def routes
  (bidi/make-handler
   ["/" [[["api/" :coll] json-handler]
         [:get [["" (bidi/resources-maybe {:prefix "public/"})]
                [#".*" index-handler]]]]]))

(def handler (-> routes
                 (json/wrap-json-body {:keywords? true})
                 json/wrap-json-response))

(defn stop-server []
  (when-not (nil? @server)
    (@server :timeout 100)
    (mg/disconnect conn)
    (reset! server nil)))

(defn -main [& args]
  (let [connection (mg/connect-via-uri "mongodb://localhost/atlas")]
    (def conn (:conn connection))
    (def db (:db connection)))
  (reset! server (run-server #'handler {:port 1042})))
