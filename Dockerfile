# ---- build ----
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -q -B dependency:go-offline
COPY src src
RUN ./mvnw -q -B package -DskipTests

# ---- run ----
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/training-manager-*.jar app.jar
# 公开环境不开 H2 控制台（本地开发时默认开启）
ENV SPRING_H2_CONSOLE_ENABLED=false \
    PORT=8080
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java -XX:MaxRAMPercentage=75 -jar app.jar --server.port=${PORT}"]
