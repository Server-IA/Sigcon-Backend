# ---------- STAGE 1: BUILD ----------
    FROM maven:3.9.6-eclipse-temurin-17 AS build

    WORKDIR /build
    
    # Copiamos solo lo necesario primero (mejora cache)
    COPY pom.xml .
    RUN mvn dependency:go-offline
    
    # Copiamos el resto del proyecto
    COPY . .
    
    # Compilamos
    RUN mvn clean package -DskipTests
    
    
    # ---------- STAGE 2: RUNTIME ----------
    FROM eclipse-temurin:17-jdk-alpine
    
    WORKDIR /app
    
    COPY --from=build /build/target/*.jar app.jar
    
    EXPOSE 8080
    
    # QA-BLOQUE-AN (2026-04-29): heap subido de 128/256MB a 256/640MB tras OOMKilled
    # (exit 137) en Dokploy. Spring Boot 3 + Hibernate 6 + multi-tenant filters +
    # 11 modulos no cabia en 256MB. El container tiene 1024MB (mem_limit en compose),
    # asi 640MB heap deja ~384MB para metaspace, threads, buffers de log.
    # MaxRAMPercentage como fallback si en algun deploy no se setean -Xmx explicitos.
    ENTRYPOINT ["java", "-Xms256m", "-Xmx640m", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=70.0", "-jar", "app.jar"]
