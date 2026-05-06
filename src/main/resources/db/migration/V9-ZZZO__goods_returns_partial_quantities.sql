-- ============================================================================
-- V9-ZZZO: Devoluciones parciales de mercancia (HU-AP-21 E2/E4)
-- ----------------------------------------------------------------------------
-- QA-BLOQUE-AY (2026-05-05): la HU-AP-21 exige soportar devoluciones por
-- CANTIDADES (no solo rechazo total). Antes el sistema solo permitia marcar
-- la recepcion completa como REJECTED, perdiendo trazabilidad cuando solo
-- una parte de la mercancia regresaba al proveedor.
--
-- Cambios:
--   1. goods_receipt_lines.quantity_returned: cantidad acumulada devuelta
--      por linea. NULL/0 = nada devuelto. Al alcanzar quantity_received
--      la linea se considera totalmente devuelta.
--   2. goods_receipts.status amplia valores admitidos: agregar
--      PARTIALLY_RETURNED. Hibernate ddl-auto no actualiza CHECK constraints
--      automaticamente.
--   3. Tabla goods_returns: documenta cada devolucion con codigo unico
--      DV-{año}{secuencial-6}, motivo, fecha, usuario.
--   4. Tabla goods_return_lines: cantidad devuelta por linea de recepcion,
--      vinculada a un goods_return.
-- ============================================================================

-- 1) Columna quantity_returned en goods_receipt_lines
ALTER TABLE goods_receipt_lines
    ADD COLUMN IF NOT EXISTS quantity_returned NUMERIC(19,2);

-- 2) goods_returns: cabecera de devolucion
CREATE TABLE IF NOT EXISTS goods_returns (
    id              BIGSERIAL PRIMARY KEY,
    company_id      BIGINT NOT NULL,
    return_number   VARCHAR(30) NOT NULL,
    receipt_id      BIGINT NOT NULL,
    return_date     DATE NOT NULL,
    reason          TEXT NOT NULL,
    created_by      BIGINT,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMP,
    CONSTRAINT fk_goods_return_receipt FOREIGN KEY (receipt_id) REFERENCES goods_receipts(id),
    CONSTRAINT fk_goods_return_company FOREIGN KEY (company_id) REFERENCES companies(id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_goods_returns_company_number_active
    ON goods_returns(company_id, return_number)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_goods_returns_receipt
    ON goods_returns(receipt_id) WHERE deleted_at IS NULL;

-- 3) goods_return_lines: detalle de devolucion
CREATE TABLE IF NOT EXISTS goods_return_lines (
    id                       BIGSERIAL PRIMARY KEY,
    company_id               BIGINT NOT NULL,
    goods_return_id          BIGINT NOT NULL,
    goods_receipt_line_id    BIGINT NOT NULL,
    quantity_returned        NUMERIC(19,2) NOT NULL,
    notes                    TEXT,
    created_at               TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at               TIMESTAMP,
    CONSTRAINT fk_grline_return  FOREIGN KEY (goods_return_id)       REFERENCES goods_returns(id),
    CONSTRAINT fk_grline_receipt FOREIGN KEY (goods_receipt_line_id) REFERENCES goods_receipt_lines(id),
    CONSTRAINT fk_grline_company FOREIGN KEY (company_id)            REFERENCES companies(id)
);

CREATE INDEX IF NOT EXISTS idx_grline_return
    ON goods_return_lines(goods_return_id) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_grline_receipt_line
    ON goods_return_lines(goods_receipt_line_id) WHERE deleted_at IS NULL;
