package com.sigcon.backend.general.accounting;

/**
 * Estados del periodo contable.
 * <ul>
 *   <li>{@link #OPEN}: permite registrar operaciones.</li>
 *   <li>{@link #CLOSED}: CG cerrado, modulos bloqueados. Puede reabrirse con autorizacion.</li>
 *   <li>{@link #LOCKED}: bloqueo permanente, solo lectura.</li>
 * </ul>
 *
 * <p>QA Bloque BP (2026-05-19): se agregan helpers {@link #toLabelEs()} y
 * {@link #labelOf(String)} para que los reportes muestren el estado en
 * espanol (Abierto / Cerrado / Bloqueado) en lugar del literal raw.</p>
 */
public enum AccountingPeriodStatus {
    OPEN, CLOSED, LOCKED;

    /** Devuelve la etiqueta en espanol del estado. */
    public String toLabelEs() {
        switch (this) {
            case OPEN:   return "Abierto";
            case CLOSED: return "Cerrado";
            case LOCKED: return "Bloqueado";
            default:     return this.name();
        }
    }

    /**
     * Convierte un nombre crudo (OPEN / CLOSED / LOCKED) a etiqueta espanol.
     * Si no coincide con ningun valor, devuelve el {@code raw} original.
     */
    public static String labelOf(String raw) {
        if (raw == null) return "";
        try {
            return valueOf(raw.trim().toUpperCase()).toLabelEs();
        } catch (IllegalArgumentException ex) {
            return raw;
        }
    }
}
