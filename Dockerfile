FROM maven:3.9.16-eclipse-temurin-21 AS build
WORKDIR /src
COPY . .
RUN ./mvnw --batch-mode --no-transfer-progress -DskipTests package

FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache jq tini && addgroup -S archguard && adduser -S -G archguard -u 10001 archguard
COPY --from=build /src/scanner-cli/target/scanner-cli-0.2.1.jar /opt/archguard/scanner.jar
USER 10001:10001
ENTRYPOINT ["/sbin/tini", "--", "java", "-jar", "/opt/archguard/scanner.jar"]
CMD ["--help"]
