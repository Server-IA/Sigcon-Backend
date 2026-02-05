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
<<<<<<< HEAD
- ./mvnw clean package -DskipTests
=======
- mvn clean package -DskipTests 
>>>>>>> 105d2fb9ac5238992584f1f3871d4fe6195f767a
- docker-compose up --build -d

## Verificar estado
GET http://localhost:8080

## Detener
docker-compose down
