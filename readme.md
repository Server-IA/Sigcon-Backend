# Backend – Spring Boot (Arquitectura Hexagonal)

Backend desarrollado con Spring Boot siguiendo arquitectura hexagonal (Ports & Adapters).

## Requisitos
- Git
- Docker
- Docker Compose
- Java 17 (opcional)

## Clonar repositorio
- git clone https://github.com/WilliamsBD8/sigcon-backend.git
- cd sigcon-backend

## Ejecutar con Docker (recomendado)
- docker-compose up --build -d

## Verificar estado
GET http://localhost:8080

## Detener
docker-compose down

## Instalacion en Mac

- Modificar el archivo Dockerfile
- FROM eclipse-temurin:17-jdk-alpine -> FROM eclipse-temurin:17-jdk