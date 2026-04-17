# Configuración y Ejecución de Swagger en SIGCON Backend

## Descripción General
Este documento detalla todos los pasos realizados para configurar Swagger/OpenAPI en el proyecto SIGCON Backend y cómo ejecutar la aplicación para acceder a la documentación interactiva de la API.

---

## 1. Dependencias Agregadas

Se agregaron las siguientes dependencias de Swagger/OpenAPI en el archivo `pom.xml`:

```xml
<!-- Swagger/OpenAPI Documentation -->
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.3.0</version>
</dependency>
```

Esta dependencia incluye:
- Swagger UI
- OpenAPI 3.0 specification
- Integración automática con Spring Boot 3.x

---

## 2. Configuración de Swagger

### 2.1 Archivo de Configuración Principal

Se creó la clase de configuración `OpenApiConfig.java` en:
```
src/main/java/com/sigcon/backend/general/config/OpenApiConfig.java
```

Esta configuración incluye:

#### **Información General de la API**
- **Título**: SIGCON API
- **Versión**: 1.0
- **Descripción**: API para el Sistema de Gestión y Control (SIGCON)
- **Contacto**: Equipo SIGCON

#### **Esquema de Seguridad JWT**
- Configuración de Bearer Token Authentication
- Formato JWT
- Esquema HTTP con prefijo "Bearer"

#### **Personalización de la Documentación**
- Operaciones ordenadas por método HTTP
- Tags alfabéticos
- Exposición de todas las rutas con documentación OpenAPI

### 2.2 Propiedades de Configuración

Se agregaron las siguientes propiedades en `application-dev.properties`:

```properties
# Swagger/OpenAPI Configuration
springdoc.api-docs.enabled=true
springdoc.swagger-ui.enabled=true
springdoc.api-docs.path=/v3/api-docs
springdoc.swagger-ui.path=/swagger-ui.html
springdoc.swagger-ui.operationsSorter=method
springdoc.swagger-ui.tagsSorter=alpha
springdoc.swagger-ui.tryItOutEnabled=true
```

**Nota**: Estas propiedades están **solo habilitadas en el perfil `dev`** por razones de seguridad.

---

## 3. Configuración de Seguridad

### 3.1 Endpoints Públicos para Swagger

Se modificó `SecurityConfig.java` para permitir el acceso a Swagger **solo en perfil dev**:

```java
// Permitir acceso a Swagger/OpenAPI solo en perfil dev
if (isDev) {
    authorize.requestMatchers(
        "/v3/api-docs/**",
        "/swagger-ui/**",
        "/swagger-ui.html",
        "/v3/api-docs.yaml"
    ).permitAll();
}
```

Endpoints públicos de Swagger:
- `/v3/api-docs/**` - Documentación OpenAPI en JSON
- `/swagger-ui/**` - Recursos de la UI de Swagger
- `/swagger-ui.html` - Interfaz web de Swagger
- `/v3/api-docs.yaml` - Documentación OpenAPI en YAML

### 3.2 Seguridad Condicional por Perfil

La configuración de seguridad detecta automáticamente si el perfil activo es `dev` y ajusta los permisos en consecuencia.

---

## 4. Configuración de Docker

### 4.1 Archivo docker-compose.dev.yml

El archivo `docker-compose.dev.yml` configura:

#### **Base de Datos PostgreSQL**
```yaml
db:
  image: postgres:15-alpine
  environment:
    POSTGRES_DB: sigcon_db
    POSTGRES_USER: sigcon_user
    POSTGRES_PASSWORD: sigcon_password
  ports:
    - "5432:5432"
```

#### **pgAdmin (Administrador de BD)**
```yaml
pgadmin:
  image: dpage/pgadmin4:latest
  environment:
    PGADMIN_DEFAULT_EMAIL: admin@sigcon.com
    PGADMIN_DEFAULT_PASSWORD: admin123
  ports:
    - "5050:80"
```

#### **Aplicación Spring Boot**
```yaml
app:
  build: .
  environment:
    SPRING_PROFILES_ACTIVE: dev
    SPRING_DATASOURCE_URL: jdbc:postgresql://db:5432/sigcon_db
    SPRING_DATASOURCE_USERNAME: sigcon_user
    SPRING_DATASOURCE_PASSWORD: sigcon_password
  ports:
    - "8080:8080"
```

---

## 5. Cómo Iniciar la Aplicación

### 5.1 Pre-requisitos

✅ **Docker Desktop** debe estar instalado y en ejecución
- Verificar que Docker Desktop esté corriendo
- Asegurarse de que los puertos 5432, 5050 y 8080 estén disponibles

### 5.2 Pasos para Iniciar

#### **Opción 1: Usando PowerShell (Recomendado)**

```powershell
# Navegar al directorio del proyecto
cd "C:\Users\NICOLAS\Desktop\UNIVERSIDAD\NOVENO SEMESTRE\INTEGRADOR IV\backend\sigcon-backend"

# Compilar el proyecto
./mvnw clean package -DskipTests

# Iniciar los contenedores Docker
docker-compose -f docker-compose.dev.yml up --build
```

#### **Opción 2: Usando Script Automatizado**

Si existe un script `run-dev-with-swagger.ps1`:
```powershell
.\run-dev-with-swagger.ps1
```

### 5.3 Tiempo de Inicio

- La aplicación tarda aproximadamente **30-60 segundos** en iniciar completamente
- Esperar hasta ver el mensaje: `Started BackendApplication in X seconds`

---

## 6. Acceder a Swagger UI

### 6.1 URL de Acceso

Una vez que la aplicación esté corriendo, acceder a:

**🌐 Swagger UI**: http://localhost:8080/swagger-ui.html

o también:

**🌐 Swagger UI (alternativo)**: http://localhost:8080/swagger-ui/index.html

### 6.2 Documentación OpenAPI en JSON

**📄 OpenAPI JSON**: http://localhost:8080/v3/api-docs

### 6.3 Documentación OpenAPI en YAML

**📄 OpenAPI YAML**: http://localhost:8080/v3/api-docs.yaml

---

## 7. Usar Swagger UI

### 7.1 Explorar Endpoints

1. La interfaz muestra todos los controladores agrupados por tags
2. Expandir cualquier endpoint para ver detalles
3. Click en "Try it out" para probar el endpoint

### 7.2 Autenticación con JWT

Para endpoints protegidos:

1. **Obtener un Token JWT**:
   - Usar el endpoint `/auth/login` o `/auth/register`
   - Copiar el token JWT de la respuesta

2. **Configurar la Autorización**:
   - Click en el botón "Authorize" (🔓) en la parte superior derecha
   - Ingresar el token en el formato: `Bearer <tu-token-jwt>`
   - Click en "Authorize"
   - Click en "Close"

3. **Probar Endpoints Protegidos**:
   - Ahora todos los requests incluirán automáticamente el header `Authorization`
   - Probar cualquier endpoint que requiera autenticación

### 7.3 Probar Endpoints

1. Click en "Try it out"
2. Completar los parámetros requeridos
3. Click en "Execute"
4. Ver la respuesta en tiempo real

---

## 8. Otros Servicios Disponibles

### 8.1 pgAdmin (Administrador de PostgreSQL)

**🌐 URL**: http://localhost:5050

**Credenciales**:
- Email: `admin@sigcon.com`
- Password: `admin123`

**Conectar a la Base de Datos**:
- Host: `db`
- Port: `5432`
- Database: `sigcon_db`
- Username: `sigcon_user`
- Password: `sigcon_password`

### 8.2 Base de Datos PostgreSQL

**Conexión Directa**:
- Host: `localhost`
- Port: `5432`
- Database: `sigcon_db`
- Username: `sigcon_user`
- Password: `sigcon_password`

---

## 9. Detener la Aplicación

### 9.1 Detener Contenedores

```powershell
# Detener y remover contenedores
docker-compose -f docker-compose.dev.yml down

# Detener, remover contenedores y volúmenes
docker-compose -f docker-compose.dev.yml down -v
```

### 9.2 Atajos de Teclado

Si los contenedores están corriendo en la terminal:
- Presionar `Ctrl + C` para detener los contenedores
- Ejecutar `docker-compose -f docker-compose.dev.yml down` para limpiar

---

## 10. Solución de Problemas

### 10.1 Docker Desktop no está corriendo

**Error**: `Cannot connect to the Docker daemon`

**Solución**:
1. Abrir Docker Desktop
2. Esperar a que inicie completamente
3. Reintentar el comando

### 10.2 Puerto ya en uso

**Error**: `port is already allocated`

**Solución**:
```powershell
# Verificar qué está usando el puerto
netstat -ano | findstr :8080
netstat -ano | findstr :5432
netstat -ano | findstr :5050

# Detener el proceso o cambiar el puerto en docker-compose.dev.yml
```

### 10.3 Swagger no carga

**Verificar**:
1. El perfil activo es `dev`: Ver los logs de la aplicación
2. La aplicación inició correctamente: Buscar `Started BackendApplication`
3. Acceder a la URL correcta: http://localhost:8080/swagger-ui.html

### 10.4 Error 403 en Swagger

**Causa**: Swagger no está permitido en `SecurityConfig.java`

**Solución**: Verificar que el perfil `dev` esté activo y que SecurityConfig tenga la configuración correcta.

---

## 11. Estructura de Archivos Modificados/Creados

```
sigcon-backend/
├── pom.xml                                          [MODIFICADO]
├── src/main/
│   ├── java/com/sigcon/backend/general/
│   │   ├── config/
│   │   │   └── OpenApiConfig.java                   [CREADO]
│   │   └── security/
│   │       └── SecurityConfig.java                  [MODIFICADO]
│   └── resources/
│       └── application-dev.properties               [MODIFICADO]
├── docker-compose.dev.yml                           [EXISTENTE]
└── CONFIGURACION-SWAGGER.md                         [ESTE ARCHIVO]
```

---

## 12. Comandos Útiles

### 12.1 Compilación

```powershell
# Compilar sin tests
./mvnw clean package -DskipTests

# Compilar con tests
./mvnw clean package

# Limpiar compilación
./mvnw clean
```

### 12.2 Docker

```powershell
# Ver contenedores corriendo
docker ps

# Ver logs de la aplicación
docker-compose -f docker-compose.dev.yml logs app

# Ver logs en tiempo real
docker-compose -f docker-compose.dev.yml logs -f app

# Reconstruir imágenes
docker-compose -f docker-compose.dev.yml build --no-cache

# Reiniciar solo la aplicación
docker-compose -f docker-compose.dev.yml restart app
```

### 12.3 Base de Datos

```powershell
# Conectar a PostgreSQL desde línea de comandos
docker exec -it sigcon-backend-db-1 psql -U sigcon_user -d sigcon_db

# Backup de la base de datos
docker exec sigcon-backend-db-1 pg_dump -U sigcon_user sigcon_db > backup.sql

# Restaurar base de datos
docker exec -i sigcon-backend-db-1 psql -U sigcon_user sigcon_db < backup.sql
```

---

## 13. Notas Importantes

### 13.1 Seguridad

⚠️ **IMPORTANTE**: Swagger está configurado para estar disponible **SOLO en el perfil `dev`**. En producción, estos endpoints estarán bloqueados automáticamente.

### 13.2 Perfil de Desarrollo

El perfil `dev` se activa mediante la variable de entorno:
```yaml
SPRING_PROFILES_ACTIVE: dev
```

### 13.3 Versión de Java

El proyecto usa **Java 17**. Asegurarse de tener la versión correcta instalada:
```powershell
java -version
```

---

## 14. Referencias

- **SpringDoc OpenAPI**: https://springdoc.org/
- **Swagger UI**: https://swagger.io/tools/swagger-ui/
- **OpenAPI Specification**: https://spec.openapis.org/oas/v3.1.0
- **Spring Security**: https://docs.spring.io/spring-security/reference/

---

## 15. Resumen de URLs

| Servicio | URL | Descripción |
|----------|-----|-------------|
| **Swagger UI** | http://localhost:8080/swagger-ui.html | Interfaz interactiva de documentación |
| **OpenAPI JSON** | http://localhost:8080/v3/api-docs | Especificación OpenAPI en JSON |
| **OpenAPI YAML** | http://localhost:8080/v3/api-docs.yaml | Especificación OpenAPI en YAML |
| **API Backend** | http://localhost:8080 | Aplicación principal |
| **pgAdmin** | http://localhost:5050 | Administrador de base de datos |
| **PostgreSQL** | localhost:5432 | Base de datos PostgreSQL |

---

## 16. Autor y Fecha

**Configuración realizada**: 17 de Febrero de 2026  
**Proyecto**: SIGCON Backend  
**Tecnologías**: Spring Boot 3.x, Swagger/OpenAPI 3.0, Docker, PostgreSQL

---

**¡Listo para documentar tu API! 🚀**