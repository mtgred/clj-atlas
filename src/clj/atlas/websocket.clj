(ns atlas.websocket
  (:require [clojure.core.async :refer [go <!]]
            [taoensso.sente :as sente]
            [taoensso.sente.server-adapters.http-kit :refer (get-sch-adapter)]))

(let [{:keys [ch-recv send-fn connected-uids ajax-post-fn ajax-get-or-ws-handshake-fn]}
      (sente/make-channel-socket! (get-sch-adapter) {})]
  (def handshake-handler ajax-get-or-ws-handshake-fn)
  (def post-handler ajax-post-fn)
  (def <recv ch-recv)
  (def send! send-fn)
  (def connected-uids connected-uids))

(defn handle [{:keys [id ?data ring-req ?reply-fn send-fn] :as msg}]
  (case id
    :atlas/foo (do (prn "foo" ?data) (send-fn [:atlas/foo {:data ?data}]))
    :atlas/fetch (when ?reply-fn
                   (?reply-fn {:username "mtgred" :email "mtgred@gmail.com"}))
    (prn "ws event:" id)))
(go (while true
      (handle (<! <recv))))
