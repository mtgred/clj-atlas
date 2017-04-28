(ns atlas.websocket
  (:require [taoensso.sente :as sente]
            [taoensso.sente.server-adapters.http-kit :refer (get-sch-adapter)]
            [monger.collection :as mc]
            [clojurewerkz.scrypt.core :as sc]
            [atlas.db :refer [conn db]]))

(defonce ws-router (atom nil))

(let [{:keys [ch-recv send-fn connected-uids ajax-post-fn ajax-get-or-ws-handshake-fn]}
      (sente/make-channel-socket! (get-sch-adapter) {})]
  (defonce handshake-handler ajax-get-or-ws-handshake-fn)
  (defonce post-handler ajax-post-fn)
  (defonce <recv ch-recv)
  (defonce send! send-fn)
  (defonce connected-uids connected-uids))

(defmulti handle-fetch :coll)

(defmethod handle-fetch :user-profile [data]
  (let [user (mc/find-one-as-map db "users" {:username (-> data :args :username)})]
    (if user
      {:status :ok :data (select-keys user [:username :email])}
      {:status :error :data "Unknown user"})))

(defmethod handle-fetch :default [data]
  {:status :error :data "Unknown"})

(defmulti handle-post :coll)

(defmethod handle-post :user-profile [{:keys [username args]}]
  (mc/update db "users" {:username username} {"$set" args})
  "Setting updated")

(defmethod handle-post :change-password [{:keys [username args]}]
  (let [{:keys [old-password new-password]} args
        {pwd :password email :email} (mc/find-one-as-map db "users" {:username username})]
    (if (and old-password pwd (sc/verify old-password pwd))
      (do (mc/update db "users"
                     {:username username}
                     {"$set" {:password (sc/encrypt new-password 16384 8 1)}})
          "Password changed")
      "Invalid password")))

(defmulti handle-ws :id)

(defmethod handle-ws :atlas/foo [{:keys [?data send-fn]}]
  (prn "foo" ?data))

(defmethod handle-ws :atlas/fetch [{:keys [?data ?reply-fn]}]
  (let [result (handle-fetch (:data ?data))]
    (?reply-fn result)))

(defmethod handle-ws :atlas/post [{:keys [?data ?reply-fn ring-req]}]
  (clojure.pprint/pprint ring-req)
  (let [result (handle-post (assoc (:data ?data) :username (get-in ring-req [:session :user :username])))]
    (?reply-fn result)))

(defmethod handle-ws :default [msg]
  (prn "ws"))

(defn ws-handler [{:keys [id ?data ring-req ?reply-fn send-fn] :as msg}]
  (prn 'ws id ?data)
  (handle-ws msg))

(defn stop-ws-router! []
  (when-let [stop-fn @ws-router]
    (stop-fn)))

(defn start-ws-router! []
  (stop-ws-router!)
  (reset! ws-router (sente/start-server-chsk-router! <recv ws-handler)))
