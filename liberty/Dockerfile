FROM icr.io/appcafe/open-liberty:full-java17-openj9-ubi

USER root

ARG SERVER_CONFIG=server.xml

COPY ${SERVER_CONFIG} /config/server.xml
COPY orders-service.war /config/apps/orders-service.war

RUN configure.sh

USER 1001

EXPOSE 9080 9443

CMD ["server", "run", "defaultServer"]
