(ns atlas.routes
  (:require [re-frame.core :refer [dispatch]]
            [bidi.bidi :as bidi]
            [pushy.core :as pushy]))

(def routes
  ["/" {"" :home
        "about" :about
        "register" :register
        "login" :login
        "profile/" {[:user] :profile}
        "a/" {[:name] :subatlas}}])

(def history
  (pushy/pushy #(dispatch [:set-data :active-page %]) (partial bidi/match-route routes)))

(pushy/start! history)

(defn goto [url]
  (pushy/set-token! history url))
