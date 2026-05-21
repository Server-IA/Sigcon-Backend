package com.sigcon.backend.general.accounting.journal.application;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request para reversar un asiento contable contabilizado.
 *
 * <p>HU-CG-07B (QA 2026-05-18): el flujo de reversion crea siempre un asiento
 * espejo (REV-XXXX) en estado POSTED para mantener la inmutabilidad contable
 * del original. Adicionalmente, el contador puede solicitar que el sistema
 * cree un comprobante correctivo en BORRADOR (clonado del original) listo
 * para editar. Esto cumple el escenario E1 de la HU.</p>
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ReverseEntryRequest {

    @NotBlank(message = "La descripcion de la reversion es obligatoria.")
    private String description;

    /**
     * Si es true, ademas de generar el REV (espejo POSTED) se crea
     * automaticamente una copia del original en estado BORRADOR vinculada
     * como `correctionOf` del original. El contador puede editarla y luego
     * contabilizarla con el flujo normal.
     *
     * <p>Default: false (comportamiento legacy) para no romper integraciones
     * existentes que solo esperaban el REV.</p>
     */
    private Boolean createCorrectionDraft;
}
