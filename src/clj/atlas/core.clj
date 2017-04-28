(ns atlas.core
  (:gen-class)
  (:require [clojure.core.async :refer [go <!]]
            [org.httpkit.server :refer [run-server]]
            [monger.core :as mg]
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
            [atlas.db :refer [conn db]]
            [atlas.websocket :as ws]
            [atlas.utils :as utils]))

(defonce server (atom nil))

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
     (hiccup/include-js "/js/app.js")
     (when (= (System/getenv "ATLASENV") "production")
       [:script
        "(function(i,s,o,g,r,a,m){i['GoogleAnalyticsObject']=r;i[r]=i[r]||function(){(i[r].q=i[r].q||[]).push(arguments)},i[r].l=1*new Date();a=s.createElement(o),m=s.getElementsByTagName(o)[0];a.async=1;a.src=g;m.parentNode.insertBefore(a,m)})(window,document,'script','https://www.google-analytics.com/analytics.js','ga');ga('create','UA-91781705-1','auto');ga('send','pageview',window.location.pathname);"])])))

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
         ["ws" [[:get ws/handshake-handler]
                [:post ws/post-handler]]]
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
  (ws/stop-ws-router!)
  (when-not (nil? @server)
    (@server :timeout 100)
    (mg/disconnect conn)
    (reset! server nil)))

(defn -main [& args]
  (reset! server (run-server #'handler {:port (or (first args) 2042)}))
  (ws/start-ws-router!))
