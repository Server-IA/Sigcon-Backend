-- V9-H: HU-PA-RF-39 - Plantillas de reporte con vigencia, archivo binario y flag por defecto.
-- Se agregan las columnas ausentes del alcance original:
--   valid_from (obligatoria en runtime): inicio de vigencia de la version.
--   valid_to (opcional): fin de vigencia; NULL = vigente indefinidamente.
--   is_default: marca la plantilla por defecto del tipo de reporte.
--   file_name / mime_type / file_size / file_content: archivo binario embebido
--      (patron equivalente a invoice_attachments). El campo legado file_path
--      se conserva por compatibilidad retroactiva pero la via oficial es file_content.
-- Todas las columnas son nullable para idempotencia sobre datos existentes.

ALTER TABLE report_templates
    ADD COLUMN IF NOT EXISTS valid_from   DATE NULL,
    ADD COLUMN IF NOT EXISTS valid_to     DATE NULL,
    ADD COLUMN IF NOT EXISTS is_default   BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS file_name    VARCHAR(255) NULL,
    ADD COLUMN IF NOT EXISTS mime_type    VARCHAR(100) NULL,
    ADD COLUMN IF NOT EXISTS file_size    BIGINT NULL,
    ADD COLUMN IF NOT EXISTS file_content BYTEA NULL;

-- Indice unico parcial: solo UNA plantilla por_defecto activa por tipo de reporte.
CREATE UNIQUE INDEX IF NOT EXISTS uk_report_template_default_per_type
    ON report_templates (report_type_id)
    WHERE is_default = TRUE AND deleted_at IS NULL;

-- Indice compuesto para busqueda por vigencia.
CREATE INDEX IF NOT EXISTS idx_report_template_validity
    ON report_templates (report_type_id, valid_from, valid_to)
    WHERE deleted_at IS NULL;

COMMENT ON COLUMN report_templates.valid_from   IS 'HU-PA-RF-39 E1: fecha inicio vigencia';
COMMENT ON COLUMN report_templates.valid_to     IS 'HU-PA-RF-39 E1: fecha fin vigencia (NULL = indefinido)';
COMMENT ON COLUMN report_templates.is_default   IS 'HU-PA-RF-39 E3: plantilla por defecto cuando no hay version especifica';
COMMENT ON COLUMN report_templates.file_content IS 'HU-PA-RF-39 E1: binario del archivo de plantilla';
