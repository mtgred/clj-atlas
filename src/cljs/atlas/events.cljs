(ns atlas.events
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [re-frame.core :refer [reg-event-db reg-event-fx reg-fx dispatch]]
            [cljs-http.client :as http]
            [atlas.websocket :as ws]))

(reg-event-db
 :set-data
 (fn [db [_ key value]]
   (assoc db key value)))

(reg-fx
 :http
 (fn [{:keys [method url query-params json-params key] :as request}]
   (go (let [m (case method
                 :get http/get
                 :post http/post
                 nil)
             resp (<! (m url (cond-> {}
                               query-params (assoc :query-params query-params)
                               json-params (assoc :json-params json-params))))]
         (when (= (:status resp) 200)
           (dispatch [:set-data key (:body resp)]))))))

(reg-fx
 :ws-send
 (fn [{:keys [key data callback]}]
   (ws/send! [key {:data data}] 5000 callback)))

(reg-event-fx
 :fetch
 (fn [_ [_ coll args]]
   {:ws-send {:key :atlas/fetch
              :data {:coll coll :args args}
              :callback #(dispatch [:set-data coll (:data %)])}}))

(reg-event-fx
 :post
 (fn [_ [_ coll args]]
   {:ws-send {:key :atlas/post
              :data {:coll coll :args args}
              :callback #(dispatch [:set-data :notification %])}}))
