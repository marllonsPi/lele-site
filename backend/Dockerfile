FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -B -q dependency:go-offline
COPY src src
# test.skip nao compila nem baixa dependencias de teste (Postgres embutido): importa em VM de 1 GB
RUN ./mvnw -B -q -Dmaven.test.skip=true package && mv target/gestao-*.jar /app.jar

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 gestao && mkdir -p /data/storage && chown gestao /data/storage
ENV STORAGE_DIR=/data/storage
USER gestao
COPY --from=build /app.jar /app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app.jar"]
