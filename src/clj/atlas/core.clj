(ns atlas.core
  (:require [org.httpkit.server :refer [run-server]]
            [ring.util.response :as resp]
            [bidi.ring :as bidi]))

(defn index-handler [req]
  (resp/file-response "index.html" {:root "resources/public"}))

(def handler
  (bidi/make-handler
   ["/" [[:get [[""    (bidi/resources-maybe {:prefix "public/"})]
                [#".*" index-handler]]]]]))

(defn -main []
  (run-server handler {:port 1042}))
