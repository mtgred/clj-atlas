(ns atlas.core
    (:require [reagent.core :as reagent]
              [re-frame.core :as re-frame]
              [devtools.core :as devtools]
              [atlas.handlers]
              [atlas.subs]
              [atlas.pages :as pages]
              [atlas.config :as config]))

(when config/debug?
  (println "dev mode")
  (devtools/install!))

(reagent/render [pages/main] (.getElementById js/document "app"))
