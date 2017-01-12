# Project Atlas

## Dependencies

- MongoDB
- Leiningen 2
- Stylus

### Optional

- Docker

## Development Mode


```
stylus -w src/css -o resources/public/css
lein figwheel
```

Figwheel will automatically push cljs changes to the browser.

Go to [http://localhost:3449](http://localhost:3449).


## Production Mode

Using Docker:

```
$ bash build.sh
$ docker run --name mongodb -p 27017:27017 -v /data/mongodb:/data/db mongo
$ docker run -p 1042:1042 --link mongodb:mongo mtgred/atlas
```

Without Docker:

```
$ mongod
$ stylus src/css/atlas.styl -o resources/public/css
$ lein cljsbuild once prod
$ lein uberjar
$ java -jar target/atlas.jar
```

Go to [http://localhost:1042](http://localhost:1042).