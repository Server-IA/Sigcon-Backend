-- V9-ZZP (2026-04-28): permitir company_id NULL en audit_logs.
--
-- Bug en produccion (Dokploy 2026-04-28): el LOGOUT de superadmin@gmail.com
-- (PLATFORM_ADMIN, sin company_id) lanzaba ConstraintViolationException
-- porque la columna estaba NOT NULL y el TenantContext del platform admin no
-- tiene tenant. Resultado: el contenedor backend caia en el primer LOGOUT.
--
-- Fix: la columna pasa a NULLABLE. Eventos cross-tenant de plataforma
-- (LOGIN/LOGOUT/CREATE_COMPANY/PLATFORM_RESET_PASSWORD del superadmin) se
-- persisten con company_id NULL. Eventos tenant-scoped siguen poblando la
-- columna via @PrePersist desde TenantContext.
--
-- Las queries del modulo Auditoria que filtran por company_id siguen
-- funcionando porque los eventos tenant-scoped SI tienen el campo poblado.
-- Para ver eventos de plataforma, el PLATFORM_ADMIN ya bypasea @Filter.

ALTER TABLE audit_logs ALTER COLUMN company_id DROP NOT NULL;
