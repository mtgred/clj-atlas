(ns atlas.handlers
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [re-frame.core :refer [register-handler dispatch]]
            [cljs.core.async :refer [<!]]
            [cljs-http.client :as http]))

(register-handler
 :set-data
 (fn [db [_ key value]]
   (assoc db key value)))

(register-handler
 :fetch
 (fn [db [_ key url]]
   (go (dispatch [:set-data key (:body (<! (http/get url)))]))
   db))
