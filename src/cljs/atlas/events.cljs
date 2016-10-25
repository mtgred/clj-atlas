(ns atlas.events
  (:require [re-frame.core :refer [reg-event-db reg-event-fx reg-fx dispatch]]
            [atlas.websocket :as ws]))

(reg-event-db
 :set-data
 (fn [db [_ key value]]
   (assoc db key value)))

(reg-fx
 :ws-send
 (fn [{:keys [key data callback]}]
   (ws/send! [key {:data data}] 5000 callback)))

(reg-event-fx
 :fetch
 (fn [_ [_ coll args]]
   {:ws-send {:key :atlas/fetch
              :data {:coll coll :args args}
              :callback #(dispatch [:set-data coll %])}}))
