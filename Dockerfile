FROM eclipse-temurin:21-jre-alpine

# Install tools that skills might need
RUN apk add --no-cache \
    curl \
    postgresql-client \
    python3 \
    bash \
    jq \
    redis \
    aws-cli \
    openssh-client \
    kubectl \
    && rm -rf /var/cache/apk/*

WORKDIR /app

COPY sre-app/target/sre-app-0.1.0-SNAPSHOT.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", \
  "-XX:+UseZGC", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
