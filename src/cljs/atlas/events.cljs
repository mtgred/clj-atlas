(ns atlas.events
  (:require [re-frame.core :refer [reg-event-db reg-event-fx]]))

(reg-event-db
 :set-data
 (fn [db [_ key value]]
   (assoc db key value)))
