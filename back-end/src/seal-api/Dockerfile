FROM eclipse-temurin:21-jre-jammy AS runtime-base

# Build stage
FROM maven:3.9.6-eclipse-temurin-21-jammy AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests
COPY --from=runtime-base /opt/java/openjdk/lib/security/cacerts /tmp/runtime-cacerts
ADD https://truststore.pki.rds.amazonaws.com/ap-southeast-1/ap-southeast-1-bundle.pem /tmp/rds-ca-bundle.pem
COPY deploy/BuildRdsTruststore.java /tmp/BuildRdsTruststore.java
RUN java /tmp/BuildRdsTruststore.java /tmp/runtime-cacerts /tmp/rds-ca-bundle.pem /tmp/rds-truststore.p12

# Run stage
FROM runtime-base
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
# Extracted by the release runner, so the host needs no source checkout.
COPY Test/deploy-verified.sh /opt/seal/deploy-verified.sh
COPY --from=build /tmp/rds-truststore.p12 /opt/seal/rds-truststore.p12
# Public CA certificates only. No DB credential or truststore password is logged.
ENV JAVA_TOOL_OPTIONS="-Djavax.net.ssl.trustStore=/opt/seal/rds-truststore.p12 -Djavax.net.ssl.trustStoreType=PKCS12"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
