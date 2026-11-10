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
# 部署环境：prod 配置（Postgres＋session 存数据库），不开 H2 控制台
ENV SPRING_PROFILES_ACTIVE=prod \
    SPRING_H2_CONSOLE_ENABLED=false \
    PORT=8080
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java -XX:MaxRAMPercentage=75 -jar app.jar --server.port=${PORT}"]
