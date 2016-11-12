(ns atlas.core
  (:require [clojure.core.async :refer [go <!]]
            [taoensso.sente :as sente]
            [taoensso.sente.server-adapters.http-kit :refer (get-sch-adapter)]
            [org.httpkit.server :refer [run-server]]
            [clojure.data.json :as json]
            [monger.core :as mg]
            [monger.collection :as mc]
            [ring.util.response :as resp]
            [ring.middleware.json :as ring-json]
            [ring.middleware.session :as session]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [buddy.auth.middleware :as buddy]
            [buddy.auth.backends.session :refer [session-backend]]
            [clojurewerkz.scrypt.core :as sc]
            [bidi.ring :as bidi]
            [hiccup.page :as hiccup]
            [atlas.utils :as utils]))

(defonce server (atom nil))
(defonce ws-router (atom nil))

(let [connection (mg/connect-via-uri "mongodb://localhost/atlas")]
  (defonce conn (:conn connection))
  (defonce db (:db connection)))

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

(defn index-handler [req]
  (resp/response
   (hiccup/html5
    [:head
     [:meta {:charset "utf-8"}]
     [:title "Atlas"]
     (hiccup/include-css "/css/atlas.css")]
    [:body
     [:script {:type "text/javascript"}
      "var user = '" (get-in req [:session :user]) "';"]
     [:div#app]
     (hiccup/include-js "/js/app.js")])))

(defn json-handler [{:keys [route-params]}]
  (resp/response (map #(dissoc % :_id) (mc/find-maps db (:coll route-params)))))

(defn login-handler [req]
  (let [{{:keys [username password next-url]} :body} req
        {pwd :password email :email} (mc/find-one-as-map db "users" {:username username})]
    (if (and password pwd (sc/verify password pwd))
      (let [user {:username username
                  :email-hash (utils/md5 email)}]
       (-> (resp/response user)
           (update-in [:session] assoc :user user)))
      (-> (resp/response {:error "Invalid login or password"})
          (resp/status 401)))))

(defn logout-handler [req]
  (-> (resp/response "")
      (update-in [:session] #(dissoc % :user))))

(defn register-handler [req]
  (let [{:keys [username password email user]} (:body req)]
    (cond
      user
      (-> (resp/response {:error "Already logged in."})
          (resp/status 403))

      (not (and username password email))
      (-> (resp/response {:error "Missing fields."})
          (resp/status 400))

      (mc/find-one-as-map db "users" {:username username})
      (-> (resp/response {:error "Username already taken."})
          (resp/status 409))

      (mc/find-one-as-map db "users" {:email email})
      (-> (resp/response {:error "Email already used."})
          (resp/status 409))

      :else
      (let [now (java.util.Date.)
            user (mc/insert-and-return db "users" {:username username
                                                   :email email
                                                   :password (sc/encrypt password 16384 8 1)
                                                   :registration-date now
                                                   :last-connection now})]
        (let [user {:username username
                    :email-hash (utils/md5 email)}]
         (-> (resp/response user)
             (update-in [:session] assoc :user user)))))))

(def routes
  (bidi/make-handler
   ["/" [[["api/" :coll] json-handler]
         ["ws" [[:get handshake-handler]
                [:post post-handler]]]
         [:post [["login" login-handler]
                 ["logout" logout-handler]
                 ["register" register-handler]]]
         [:get [["" (bidi/resources-maybe {:prefix "public/"})]
                [#".*" index-handler]]]]]))

(def handler (-> routes
                 wrap-keyword-params
                 wrap-params
                 (buddy/wrap-authentication (session-backend))
                 session/wrap-session
                 (ring-json/wrap-json-body {:keywords? true})
                 ring-json/wrap-json-response))

(defn stop-server []
  (stop-ws-router!)
  (when-not (nil? @server)
    (@server :timeout 100)
    (mg/disconnect conn)
    (reset! server nil)))

(defn -main [& args]
  (reset! server (run-server #'handler {:port 1042}))
  (start-ws-router!))
