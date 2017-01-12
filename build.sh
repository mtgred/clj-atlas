#!/bin/bash

stylus src/css/atlas.styl -o resources/public/css
lein cljsbuild once prod
lein uberjar
mv target/atlas.jar .
rm -fr target
lein clean
docker build -t mtgred/atlas .
rm atlas.jar
