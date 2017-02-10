(ns atlas.components
  (:require [reagent.core :as r]
            [re-frame.core :refer [subscribe dispatch]]
            [cljs-http.client :as http]
            [atlas.routes :as routes]))

(defn link [{:keys [href on-click] :as props} children]
  [:a {:href href
       :on-click (fn [e]
                   (.preventDefault e)
                   (if on-click
                     (on-click e)
                     (routes/goto href)))}
   children])

(defn atom-input [props state]
  [:input (assoc props
                 :value @state
                 :on-change #(reset! state (-> % .-target .-value)))])

(defn user-view
  ([user] (user-view user 28))
  ([{:keys [username email-hash] :as user} size]
   [:div
    [:img.avatar {:src (str "https://www.gravatar.com/avatar/" email-hash "?d=retro&s=" size)}]
    username]))

(defn dropdown [props link menu]
  (let [open? (r/atom false)]
    (fn []
      [:div.dropdown
       [:div.link {:on-click #(swap! open? not)} link]
       (when @open?
         [:div
          [:div.overlay {:on-click #(reset! open? false)}]
          [:div.menu menu]])])))

(defn navbar []
  (let [current-user (subscribe [:current-user])]
    (fn []
     [:div.navbar.clearfix
      [:a.logo {:href "/"} "Atlas"]
      [:div.right
       (if @current-user
         [dropdown {}
          [user-view @current-user]
          [:div
           [link {:href (str "/profile/" (:username @current-user))} "Profile"]
           [link {:href "/settings"} "Settings"]
           [link {:href "/logout"
                  :on-click (fn [e]
                              (http/post "/logout")
                              (dispatch [:set-data :current-user nil])
                              (routes/goto "/"))}
            "Log out"]]]
         [:span
          [link {:href "/register"} "Register"]
          [link {:href "/login"} "Login"]])]])))
