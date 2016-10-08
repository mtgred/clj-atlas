(ns atlas.websocket
  (:require-macros [cljs.core.async.macros :refer (go go-loop)])
  (:require [cljs.core.async :as async :refer (<! >! put! chan)]
            [taoensso.sente  :as sente :refer (cb-success?)]))

(let [{:keys [chsk ch-recv send-fn state]}
      (sente/make-channel-socket! "/ws" {:type :auto})]
  (def chsk chsk)
  (def <recv ch-recv)
  (def send! send-fn)
  (def chsk-state state))
