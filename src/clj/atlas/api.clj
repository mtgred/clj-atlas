(ns atlas.api
  (:require [compojure.core :refer [defroutes GET POST]]
            [compojure.route :as route]
            [ring.middleware.json :refer [wrap-json-body wrap-json-response]]
            [ring.middleware.session :as session]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [buddy.auth.middleware :refer [wrap-authentication]]
            [buddy.auth.backends.session :refer [session-backend]]
            [monger.collection :as mc]
            [liberator.core :refer [defresource]]
            [ring.util.response :as resp]
            [hiccup.page :as hiccup]
            [clojurewerkz.scrypt.core :as sc]
            [atlas.db :refer [conn db]]
            [atlas.websocket :refer [handshake-handler post-handler]]
            [atlas.utils :as utils]))

(defn index-page [req]
  (hiccup/html5
   [:head
    [:meta {:charset "utf-8"}]
    [:title "Atlas"]
    (hiccup/include-css "/css/atlas.css")]
   [:body
    [:div#app]
    [:script {:type "text/javascript"}
     "var user = '" (get-in req [:session :user]) "';"]
    (when (= (System/getenv "ATLASENV") "production")
      [:script
       "(function(i,s,o,g,r,a,m){i['GoogleAnalyticsObject']=r;i[r]=i[r]||function(){(i[r].q=i[r].q||[]).push(arguments)},i[r].l=1*new Date();a=s.createElement(o),m=s.getElementsByTagName(o)[0];a.async=1;a.src=g;m.parentNode.insertBefore(a,m)})(window,document,'script','https://www.google-analytics.com/analytics.js','ga');ga('create','UA-91781705-1','auto');ga('send','pageview',window.location.pathname);"])
    (hiccup/include-js "/js/app.js")]))

(defresource index-res [req]
  :available-media-types ["text/html"]
  :handle-ok (index-page req))

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

(defroutes routes
  (route/resources "/")
  (GET "/ws" [] handshake-handler)
  (POST "/ws" [] post-handler)
  (POST "/login" [] login-handler)
  (POST "/logout" [] logout-handler)
  (POST "/register" [] register-handler)
  (GET "/api/:coll" [coll] json-handler)
  (GET "/*" [] index-res))

(def app (-> routes
             wrap-keyword-params
             wrap-params
             (wrap-authentication (session-backend))
             session/wrap-session
             (wrap-json-body {:keywords? true})
             wrap-json-response))
