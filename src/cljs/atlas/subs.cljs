(ns atlas.subs
  (:require [re-frame.core :refer [reg-sub]]))

(reg-sub :active-page (fn [db _] (:active-page db)))
(reg-sub :current-user (fn [db _] (:current-user db)))
(reg-sub :user-profile (fn [db _] (:user-profile db)))
