FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY orders-service/pom.xml orders-service/pom.xml
COPY orders-service/src orders-service/src
COPY frontend/package.json frontend/package.json
COPY frontend/tsconfig.json frontend/tsconfig.json
COPY frontend/next.config.ts frontend/next.config.ts
COPY frontend/tailwind.config.ts frontend/tailwind.config.ts
COPY frontend/postcss.config.mjs frontend/postcss.config.mjs
COPY frontend/next-env.d.ts frontend/next-env.d.ts
COPY frontend/src frontend/src
RUN mvn -f orders-service/pom.xml clean package -DskipTests

FROM icr.io/appcafe/open-liberty:full-java17-openj9-ubi

USER root

ARG SERVER_CONFIG=server.xml

COPY liberty/${SERVER_CONFIG} /config/server.xml
COPY --from=build /app/orders-service/target/orders-service.war /config/apps/root.war

RUN configure.sh

USER 1001

EXPOSE 9080 9443

CMD ["server", "run", "defaultServer"]
