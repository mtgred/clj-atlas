(ns atlas.core
    (:require [reagent.core :as reagent]
              [re-frame.core :as rf]
              [devtools.core :as devtools]
              [atlas.events]
              [atlas.subs]
              [atlas.pages :as pages]
              [atlas.config :as config]))

(when config/debug?
  (println "dev mode")
  (devtools/install!))

(when-not (empty? js/user)
  (rf/dispatch [:set-data :current-user (cljs.reader/read-string js/user)]))

(reagent/render [pages/main] (.getElementById js/document "app"))
