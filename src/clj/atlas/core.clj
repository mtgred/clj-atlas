(ns atlas.core
  (:require [org.httpkit.server :refer [run-server]]
            [monger.core :as mg]
            [monger.collection :as mc]
            [ring.util.response :as resp]
            [ring.middleware.json :as json]
            [ring.middleware.session :as session]
            [buddy.auth.middleware :as buddy]
            [buddy.auth.backends.session :refer [session-backend]]
            [clojurewerkz.scrypt.core :as sc]
            [bidi.ring :as bidi]
            [atlas.utils :as utils]))

(defonce server (atom nil))

(let [connection (mg/connect-via-uri "mongodb://localhost/atlas")]
  (def conn (:conn connection))
  (def db (:db connection)))

(defn index-handler [req]
  (resp/file-response "index.html" {:root "resources/public"}))

(defn json-handler [{:keys [route-params]}]
  (resp/response (map #(dissoc % :_id) (mc/find-maps db (:coll route-params)))))

(defn login-handler [req]
  (let [{{:keys [username password next-url]} :body} req
        {pwd :password email :email} (mc/find-one-as-map db "users" {:username username})]
    (if (and password pwd (sc/verify password pwd))
      (-> (resp/response {:username username
                          :email-hash (utils/md5 email)})
          (assoc :session (assoc (:session req) :identity username)))
      (-> (resp/response [:error "Unauthorized."])
          (resp/status 401)))))

(defn logout-handler [req]
  (-> (resp/response "")
      (update-in [:session] #(dissoc % :identity))))

(defn register-handler [req]
  (let [{:keys [username password email identity]} (:body req)]
    (cond
      identity
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
        (-> (resp/response {:username username
                            :email-hash (utils/md5 email)})
            (assoc :session (assoc (:session req) :identity username)))))))

(def routes
  (bidi/make-handler
   ["/" [[["api/" :coll] json-handler]
         [:post [["login" login-handler]
                 ["logout" logout-handler]
                 ["register" register-handler]]]
         [:get [["" (bidi/resources-maybe {:prefix "public/"})]
                [#".*" index-handler]]]]]))

(def handler (-> routes
                 (buddy/wrap-authentication (session-backend))
                 session/wrap-session
                 (json/wrap-json-body {:keywords? true})
                 json/wrap-json-response))

(defn stop-server []
  (when-not (nil? @server)
    (@server :timeout 100)
    (mg/disconnect conn)
    (reset! server nil)))

(defn -main [& args]
  (reset! server (run-server #'handler {:port 1042})))
