(ns atlas.pages
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [reagent.core :as r]
            [re-frame.core :refer [subscribe dispatch]]
            [cljs-http.client :as http]
            [cljs.core.async :refer [<!]]
            [atlas.routes :as routes]
            [atlas.components :refer [atom-input link user-view navbar]]
            [atlas.websocket :as ws]))

(defn home []
  [:div.container
   [:h1 "Home page"]
   [:div
    [link {:href "/about"} "About"]]
   [:div
    [:button {:on-click #(ws/send! [:atlas/foo {:data "bar"}])} "Send"]]])

(defn about []
  [:div.container
   [:h1 "About page"]])

(defn subatlas [name]
  [:div.container
   [:h1 (str "Subatlas " name)]])

(defn handle-login! [resp error-msg]
  (if (:success resp)
    (do (dispatch [:set-data :current-user (:body resp)])
        (routes/goto "/"))
    (reset! error-msg (-> resp :body :error))))

(defn register []
  (let [username (r/atom "")
        email (r/atom "")
        password (r/atom "")
        error-msg (r/atom "")]
    (fn []
     [:div.container
      [:h1 "Create a new account"]
      [:form {:on-submit (fn [e]
                           (.preventDefault e)
                           (go (let [resp (<! (http/post "/register"
                                                         {:json-params {:username @username
                                                                        :email @email
                                                                        :password @password}}))]
                                 (handle-login! resp error-msg))))}
       [:p
        [:label "Username"]]
       [:p
        [atom-input {:type "text"} username]]
       [:p
        [:label "Email"]]
       [:p
        [atom-input {:type "email"} email]]
       [:p
        [:label "Password"]]
       [:p
        [atom-input {:type "password"} password]]
       [:button "Register"]
       (when-not (empty? @error-msg)
         [:p @error-msg])]])))

(defn login []
  (let [username (r/atom "")
        password (r/atom "")
        error-msg (r/atom "")]
    (fn []
     [:div.container
      [:h1 "Login"]
      [:form {:on-submit (fn [e]
                           (.preventDefault e)
                           (go (let [resp (<! (http/post "/login"
                                                         {:json-params {:username @username
                                                                        :password @password}}))]
                                 (handle-login! resp error-msg))))}
       [:p
        [:label "Username"]]
       [:p
        [atom-input {:type "text"} username]]
       [:p
        [:label "Password"]]
       [:p
        [atom-input {:type "password"} password]]
       [:button "Login"]
       (when-not (empty? @error-msg)
         [:p @error-msg])]])))

(defn settings []
  (let [user-profile (subscribe [:user-profile])
        current-user (subscribe [:current-user])
        email (r/atom nil)]
    (r/create-class
     {:component-will-mount
      #(dispatch [:fetch :user-profile {:username (:username @current-user)}])
      :reagent-render
      (fn []
        [:div.container
         (if-not @user-profile
           "Loading..."
           [:div
            [:h1 (:username @user-profile)]
            [:p "Email"]
            [:input {:type "text"
                     :value (or @email (:email @user-profile))
                     :on-change #(reset! email (-> % .-target .-value))}]
            [:p
             [:button
              {:on-click #(when @email
                            (dispatch [:post :user-profile {:email @email}]))}
              "Save"]]])])})))


(defn profile [user]
  (let [user-profile (subscribe [:user-profile])]
    (r/create-class
     {:component-will-mount
      #(dispatch [:fetch :user-profile {:username user}])
      :reagent-render
      (fn []
        [:div.container
         (if-not @user-profile
           "Loading..."
           [:div
            [:h1 (:username @user-profile)]
            [:p (str "Email: " (:email @user-profile)) ]])])})))

(defn get-page [{:keys [handler route-params]}]
  (case handler
    :home [home]
    :about [about]
    :register [register]
    :login [login]
    :settings [settings]
    :profile [profile (:user route-params)]
    :subatlas [subatlas (:name route-params)]
    [:div]))

(defn main []
  (let [active-page (subscribe [:active-page])]
    (fn []
      [:div
       [navbar]
       (get-page @active-page)])))
