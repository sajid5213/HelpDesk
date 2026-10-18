# One image: Spring Boot serves the API and the built React app.
# No secrets are baked in; they come from environment variables at run time.

# 1. Build the React app
FROM node:24-alpine AS web
WORKDIR /web
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

# 2. Build the Spring Boot jar with the React files inside (tests run in CI, not here)
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app
COPY backend/.mvn/ .mvn/
COPY backend/mvnw backend/pom.xml ./
RUN --mount=type=cache,target=/root/.m2 sh mvnw -B -q dependency:go-offline
COPY backend/src/ src/
COPY --from=web /web/dist/ src/main/resources/static/
RUN --mount=type=cache,target=/root/.m2 sh mvnw -B -q -DskipTests package \
 && cp target/helpdesk-*.jar app.jar

# 3. Run on a JRE as a non-root user
FROM eclipse-temurin:25-jre
RUN useradd --system --uid 10001 --no-create-home helpdesk
WORKDIR /app
COPY --from=build /app/app.jar app.jar
USER helpdesk
ENV PORT=8080 \
    SPRING_PROFILES_ACTIVE=prod \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
