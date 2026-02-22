# Script para ejecutar el backend con Swagger habilitado (perfil dev)
# Uso: .\run-dev-with-swagger.ps1

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  SIGCON Backend - Modo Desarrollo" -ForegroundColor Cyan
Write-Host "  Swagger UI habilitado" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Configurar perfil dev
$env:SPRING_PROFILES_ACTIVE = "dev"

Write-Host "Iniciando aplicación..." -ForegroundColor Green
Write-Host "Perfil activo: dev" -ForegroundColor Yellow
Write-Host ""
Write-Host "Una vez iniciado, accede a:" -ForegroundColor Green
Write-Host "  - Swagger UI: http://localhost:8080/swagger-ui.html" -ForegroundColor White
Write-Host "  - API Docs (JSON): http://localhost:8080/v3/api-docs" -ForegroundColor White
Write-Host ""
Write-Host "Presiona Ctrl+C para detener la aplicación" -ForegroundColor Yellow
Write-Host ""

# Ejecutar con Maven wrapper
.\mvnw.cmd spring-boot:run