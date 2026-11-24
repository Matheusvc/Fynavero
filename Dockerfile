# Imagem de produção da Fynavero: um único contêiner com o front-end embutido no .jar.
# Build:  docker build -t fynavero .
# Rodar:  docker run -p 8080:8080 -e DATABASE_URL=postgres://usuario:senha@host:5432/fynavero -e APP_BASE_URL=https://seu-dominio fynavero

# 1) Front-end (React + Vite)
FROM node:22-alpine AS front
WORKDIR /app/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ ./
RUN npm run build

# 2) Back-end (Spring Boot), já com o front em /app/frontend/dist
FROM maven:3.9-eclipse-temurin-21 AS back
WORKDIR /app/backend
COPY backend/pom.xml ./
RUN mvn -B -q dependency:go-offline
COPY backend/src ./src
COPY --from=front /app/frontend/dist /app/frontend/dist
RUN mvn -B -q package -DskipTests

# 3) Execução: só o Java, sem ferramentas de build, com usuário sem privilégios
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=back /app/backend/target/fynavero.jar app.jar
USER app

# Produção: PostgreSQL + sessões no banco, cookie só em HTTPS e IP real vindo do proxy da hospedagem.
ENV SPRING_PROFILES_ACTIVE=postgres \
    COOKIE_SECURE=true \
    FORWARD_HEADERS_STRATEGY=framework \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
