FROM eclipse-temurin:21-jdk-jammy AS build

WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw && ./mvnw -B -ntp dependency:go-offline

COPY src/ src/
RUN ./mvnw -B -ntp -DskipTests package \
    && test -f target/hankki-travel-0.1.0-SNAPSHOT.jar \
    && cp target/hankki-travel-0.1.0-SNAPSHOT.jar /tmp/app.jar

FROM eclipse-temurin:21-jre-jammy

RUN groupadd --system --gid 10001 spring \
    && useradd --system --uid 10001 --gid spring --home /app --shell /usr/sbin/nologin spring

WORKDIR /app
COPY --from=build --chown=spring:spring /tmp/app.jar /app/app.jar

USER spring
EXPOSE 8300
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -Djava.io.tmpdir=/tmp"

ENTRYPOINT ["java", "-jar", "/app/app.jar"]