-- Sprint 2 - convertir columnas JSONB a TEXT en entidades JPA mapeadas como String
-- (Hibernate 6 + columnas JSONB + private String causa "Bad value for type long" porque
--  el adapter JSON intenta deserializar a tipo Java incorrecto.)
ALTER TABLE audit_log_platform ALTER COLUMN payload_json TYPE TEXT USING payload_json::TEXT;
ALTER TABLE companies         ALTER COLUMN brand_config TYPE TEXT USING brand_config::TEXT;
ALTER TABLE companies         ALTER COLUMN module_order TYPE TEXT USING module_order::TEXT;
