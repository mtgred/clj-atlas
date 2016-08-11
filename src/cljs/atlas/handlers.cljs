(ns atlas.handlers
    (:require [re-frame.core :as re-frame]
              [atlas.db :as db]))

(re-frame/register-handler
 :initialize-db
 (fn  [_ _]
   db/default-db))
