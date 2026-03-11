--Segmentacion ECL - RF-08 (risk_segmentation) 
--Garantiza un unico segmento vigente por cliente
CREATE UNIQUE INDEX IF NOT EXISTS uk_risk_segmentation_client_active
ON risk_segmentation (client_id)
WHERE deleted_at IS NULL;