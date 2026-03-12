--Tabla Principal 
CREATE TABLE IF NOT EXISTS risk_segmentation (
    id BIGSERIAL PRIMARY KEY, 
    client_id BIGINT NOT NULL UNIQUE,
    auto_segment VARCHAR(255), 
    final_segment VARCHAR(255), 
    segmentation_source VARCHAR(255) NOT NULL DEFAULT 'AUTOMATIC', 
    justification TEXT, 
    calculation_date TIMESTAMP, 
    created_at TIMESTAMP NOT NULL DEFAULT NOW(), 
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(), 
    deleted_at TIMESTAMP NULL,
    CONSTRAINT fk_risk_segmentation_client_id  
        FOREIGN KEY (client_id) REFERENCES third_parties(id)
); 
--Tabla Historico de cambios de la segmentacion 
CREATE TABLE IF NOT EXISTS risk_segmentation_history (
    id BIGSERIAL PRIMARY KEY, 
    client_id BIGINT NOT NULL,
    previous_segment VARCHAR(255) NOT NULL,
    new_segment VARCHAR(255) NOT NULL, 
    segmentation_source VARCHAR(255) NOT NULL, 
    justification TEXT,
    change_date TIMESTAMP NOT NULL DEFAULT NOW(), 
    CONSTRAINT fk_risk_segmentation_history_client_id
        FOREIGN KEY (client_id) REFERENCES third_parties(id)
); 
--Dependencias pendientes para modulos no implementados aun: 
--1. Cuentas por Cobrar (Accounts Receivable - AR) - modulo aun no implementado. 
--una vez implementado el modulo se deben conectar los datos de la antiguedad de saldos y dias mora
--para poder realizar el calculo automatico de segmentacion ECL segun el RF-08 del modulo de Terceros. 
--ALTER TABLE risk_segmentation ADD CONSTRAINT fk_risk_segmentation_ar_data
-- FOREIGN KEY (ar_references_id) REFERENCES account_receivable(id)

--auto_segment y final_segment estaran en estado PENDING porque el modulo AR no esta implementado (ECL_001)
INSERT INTO risk_segmentation (
    client_id,
    auto_segment,
    final_segment,
    segmentation_source, 
    justification, 
    calculation_date, 
    created_at,
    updated_at
)
SELECT 
    tp.id, 
    'PENDING', 
    'PENDING',
    'AUTOMATIC', 
    NULL, 
    NOW(),
    NOW(),
    NOW()
FROM third_parties tp
JOIN third_party_role_assignments tra ON tra.third_party_id = tp.id
JOIN third_party_role_catalog rc ON rc.id = tra.role_id
WHERE rc.name = 'CLIENTE'
   AND tp.deleted_at IS NULL
   AND NOT EXISTS (
    SELECT 1 FROM risk_segmentation rs 
    WHERE rs.client_id = tp.id
    AND rs.deleted_at IS NULL
   ); 
INSERT INTO risk_segmentation_history (
    client_id,
    previous_segment, 
    new_segment, 
    segmentation_source,
    justification,
    change_date
)
SELECT 
    rs.client_id,
    'PENDING',
    'PENDING',
    'AUTOMATIC',
    NULL,
    NOW()
FROM risk_segmentation rs 
JOIN third_parties tp ON rs.client_id = tp.id
JOIN third_party_role_assignments tra ON tra.third_party_id = tp.id
JOIN third_party_role_catalog rc ON rc.id = tra.role_id
WHERE rc.name = 'CLIENTE'
  AND tp.deleted_at IS NULL
  AND NOT EXISTS (
    SELECT 1 FROM risk_segmentation_history rsh 
    WHERE rsh.client_id = rs.client_id
  )