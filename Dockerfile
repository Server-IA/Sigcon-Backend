# ---------- STAGE 1: BUILD ----------
    FROM maven:3.9.6-eclipse-temurin-17 AS build

    WORKDIR /build
    
    # Copiamos solo lo necesario primero (mejora cache)
    COPY pom.xml .
    RUN mvn dependency:go-offline
    
    # Copiamos el resto del proyecto
    COPY src ./src
    
    # Compilamos
    RUN mvn clean package -DskipTests
    
    
    # ---------- STAGE 2: RUNTIME ----------
    FROM eclipse-temurin:17-jdk-alpine
    
    WORKDIR /app
    
    COPY --from=build /build/target/*.jar app.jar
    
    EXPOSE 8080
    
    ENTRYPOINT ["java", "-Xms128m", "-Xmx256m", "-XX:+UseContainerSupport", "-jar", "app.jar"]
    