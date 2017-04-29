(ns atlas.core
  (:gen-class)
  (:require [org.httpkit.server :refer [run-server]]
            [monger.core :refer [disconnect]]
            [atlas.db :refer [conn]]
            [atlas.api :refer [app]]
            [atlas.websocket :as ws]))

(defonce server (atom nil))

(defn stop-server []
  (ws/stop-ws-router!)
  (when-not (nil? @server)
    (@server :timeout 100)
    (disconnect conn)
    (reset! server nil)))

(defn -main [& args]
  (let [port (or (first args) 2042)]
    (reset! server (run-server #'app {:port port}))
    (println "Atlas server started on port:" port))
  (ws/start-ws-router!))
