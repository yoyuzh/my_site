ARG RUNTIME_IMAGE=eclipse-temurin:17-jre-jammy

FROM ${RUNTIME_IMAGE}

WORKDIR /app

ENV SPRING_PROFILES_ACTIVE=prod \
    SERVER_ADDRESS=0.0.0.0 \
    SERVER_PORT=8080 \
    APP_REDIS_ENABLED=false \
    YOYUZH_STORAGE_LOCAL_ROOT_DIR=/app/storage

COPY target/yoyuzh-portal-backend-0.0.1-SNAPSHOT.jar /app/app.jar

RUN mkdir -p /app/storage

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
