FROM clojure
MAINTAINER Minh Tran <mtgred@gmail.com>
RUN mkdir -p /usr/code/atlas
WORKDIR . /usr/code/atlas
COPY . /usr/code/atlas
CMD ["java", "-jar", "/usr/code/atlas/atlas.jar"]