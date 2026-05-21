package com.sigcon.backend.general.accounting.journal.domain.model.enums;

/**
 * Estado del asiento contable.
 * <ul>
 *   <li>{@link #DRAFT}: borrador.</li>
 *   <li>{@link #POSTED}: contabilizado.</li>
 *   <li>{@link #REVERSED}: reversado.</li>
 * </ul>
 *
 * <p>QA Bloque BP (2026-05-19): se agregan helpers {@link #toLabelEs()} y
 * {@link #labelOf(String)} para que TODA exportacion (CSV, XLSX, PDF) muestre
 * el estado en espanol en lugar del literal raw del enum. La BD se mantiene
 * con los nombres en ingles (sin migracion necesaria); el mapeo a espanol se
 * hace al renderizar.</p>
 */
public enum JournalEntryStatus {
    DRAFT, POSTED, REVERSED;

    /**
     * Devuelve la etiqueta en espanol del estado para uso en reportes y UI.
     */
    public String toLabelEs() {
        switch (this) {
            case DRAFT:    return "Borrador";
            case POSTED:   return "Contabilizado";
            case REVERSED: return "Reversado";
            default:       return this.name();
        }
    }

    /**
     * Convierte un nombre crudo (DRAFT / POSTED / REVERSED) a su etiqueta
     * en espanol. Si el nombre no coincide con ningun valor del enum,
     * devuelve el valor original (defensivo: no rompe el flujo del export).
     *
     * @param raw nombre del enum como string
     * @return etiqueta en espanol o el mismo {@code raw} si no se reconoce
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
