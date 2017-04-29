(defproject atlas "0.1.0-SNAPSHOT"
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/clojurescript "1.8.51"]
                 [com.novemberain/monger "3.0.2"]
                 [ring "1.5.0"]
                 [ring/ring-json "0.4.0"]
                 [http-kit "2.1.18"]
                 [com.taoensso/sente "1.10.0"]
                 [compojure "1.5.2"]
                 [bidi "2.0.9"]
                 [aero "1.1.2"]
                 [buddy/buddy-auth "1.1.0"]
                 [clojurewerkz/scrypt "1.2.0"]
                 [hiccup "1.0.5"]
                 [reagent "0.6.0"]
                 [re-frame "0.8.0"]
                 [kibu/pushy "0.3.6"]
                 [cljs-http "0.1.41"]
                 [binaryage/devtools "0.6.1"]]

  :min-lein-version "2.5.3"

  :source-paths ["src/clj"]

  :plugins [[lein-cljsbuild "1.1.3"]]

  :clean-targets ^{:protect false} ["resources/public/cljs" "target"]

  :figwheel {:css-dirs ["resources/public/css"]
             :ring-handler atlas.core/handler}

  :profiles {:uberjar {:aot :all
                       :uberjar-name "atlas.jar"}
             :dev {:dependencies [[com.cemerick/piggieback "0.2.1"]
                                  [figwheel-sidecar "0.5.4-3"]]
                   :plugins      [[lein-figwheel "0.5.4-3"]]
                   :source-paths ["src/clj" "src/cljs"]}}

  :repl-options {:init-ns atlas.core
                 :port 2041}

  :main atlas.core

  :cljsbuild {:builds [{:id           "dev"
                        :source-paths ["src/cljs"]
                        :figwheel     true
                        :compiler     {:main                 atlas.core
                                       :output-to            "resources/public/js/app.js"
                                       :output-dir           "resources/public/cljs"
                                       :asset-path           "/cljs"
                                       :source-map-timestamp true}}

                       {:id           "prod"
                        :source-paths ["src/cljs"]
                        :compiler     {:main                 atlas.core
                                       :output-to            "resources/public/js/app.js"
                                       :optimizations        :advanced
                                       :closure-defines      {goog.DEBUG false}
                                       :pretty-print         false}}]})
