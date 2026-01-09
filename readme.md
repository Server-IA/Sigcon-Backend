# Backend – Spring Boot (Arquitectura Hexagonal)

Backend desarrollado con Spring Boot siguiendo arquitectura hexagonal (Ports & Adapters).

## Requisitos
- Git
- Docker
- Docker Compose
- Java 17 (opcional)

## Clonar repositorio
- git clone https://github.com/WilliamsBD8/sigcon-backend.git
- cd backend

## Ejecutar con Docker (recomendado)
- ./mvnw clean package -DskipTests
- docker-compose up --build

## Verificar estado
GET http://localhost:8080

## Detener
docker-compose down
