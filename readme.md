# Backend – Spring Boot (Arquitectura Hexagonal)

Backend desarrollado con Spring Boot siguiendo arquitectura hexagonal (Ports & Adapters).

## Requisitos
- Git
- Docker
- Docker Compose
- Java 17 (opcional)

## Clonar repositorio
```bash
git clone https://github.com/WilliamsBD8/sigcon-backend.git
cd sigcon-backend
```

## Ejecutar con Docker (recomendado)
```bash
docker compose up --build -d
```

## Ejecutar en modo desarrollo con Swagger

### Opción 1: Script automatizado (Windows PowerShell)
```powershell
.\run-dev-with-swagger.ps1
```

### Opción 2: Comando manual
```powershell
# Windows PowerShell
$env:SPRING_PROFILES_ACTIVE = "dev"; .\mvnw.cmd spring-boot:run
```

### Opción 3: Con variables de entorno
```powershell
# Establecer perfil dev
$env:SPRING_PROFILES_ACTIVE = "dev"

# Ejecutar aplicación
.\mvnw.cmd spring-boot:run
```

## Documentación API (Swagger/OpenAPI)

Cuando ejecutas la aplicación con el perfil `dev`, la documentación interactiva de la API queda disponible en:

- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **API Docs (JSON)**: http://localhost:8080/v3/api-docs
- **API Docs (YAML)**: http://localhost:8080/v3/api-docs.yaml

### Características de Swagger UI
- Visualización interactiva de todos los endpoints
- Prueba de endpoints directamente desde el navegador
- Documentación automática de modelos y DTOs
- Especificación OpenAPI 3.0

### Seguridad
⚠️ **Importante**: La documentación Swagger solo está habilitada en el perfil `dev` por razones de seguridad. En producción, la UI no estará disponible.

Para habilitar en otros entornos, modifica `SecurityConfig.java` o usa la propiedad:
```properties
springdoc.api-docs.enabled=true
```

## Verificar estado
```
GET http://localhost:8080
```

## Tests

### Ejecutar todos los tests
```powershell
.\mvnw.cmd test
```

### Ejecutar tests con perfil dev
```powershell
.\mvnw.cmd -Dspring.profiles.active=dev test
```

### Empaquetar sin tests
```powershell
.\mvnw.cmd -DskipTests=true clean package
```

## Detener
```bash
docker-compose down
```

## Instalación en Mac

Modificar el archivo Dockerfile:
```dockerfile
# Cambiar de:
FROM eclipse-temurin:17-jdk-alpine

# A:
FROM eclipse-temurin:17-jdk
```

## Tecnologías
- Spring Boot 3.5.8
- Spring Security + JWT
- Spring Data JPA
- PostgreSQL
- H2 (tests)
- Springdoc OpenAPI 2.1.0 (Swagger)
- Lombok
- Docker