(ns atlas.subs
  (:require-macros [reagent.ratom :refer [reaction]])
  (:require [re-frame.core :refer [register-sub]]))

(register-sub :active-page (fn [db _] (reaction (:active-page @db))))
(register-sub :current-user (fn [db _] (reaction (:current-user @db))))
