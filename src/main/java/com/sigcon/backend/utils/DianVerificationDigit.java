package com.sigcon.backend.utils;

/**
 * Algoritmo DIAN para el digito de verificacion (DV) de un NIT colombiano.
 *
 * <p>Fuente unica de verdad reutilizada por Terceros (PT-05/PT-06, TER-RF-02/07)
 * y Plataforma (HU-PA-PLAT-01 E4). Validacion 100% local, sin integracion
 * externa con la DIAN.
 *
 * <p><b>Algoritmo:</b> se asignan factores
 * {@code [3, 7, 13, 17, 19, 23, 29, 37, 41, 43, 47, 53, 59, 67, 71]} a los
 * digitos del NIT de derecha a izquierda, se suma el producto, se obtiene el
 * modulo 11 y:
 * <ul>
 *   <li>si modulo es 0 o 1 -&gt; DV = modulo</li>
 *   <li>si modulo es &gt;=2 -&gt; DV = 11 - modulo</li>
 * </ul>
 */
public final class DianVerificationDigit {

    private static final int[] FACTORS = {3, 7, 13, 17, 19, 23, 29, 37, 41, 43, 47, 53, 59, 67, 71};

    private DianVerificationDigit() {
    }

    /**
     * Calcula el DV de un NIT segun el algoritmo DIAN.
     *
     * @param nit cadena numerica (cualquier longitud razonable, 1-15 digitos)
     * @return el DV como String "0".."9", o {@code null} si el NIT es nulo,
     *         vacio o contiene caracteres no numericos
     */
    public static String compute(String nit) {
        if (nit == null) {
            return null;
        }
        String n = nit.trim();
        if (n.isEmpty() || !n.chars().allMatch(Character::isDigit)) {
            return null;
        }
        int sum = 0;
        for (int i = 0; i < n.length(); i++) {
            int digit = n.charAt(n.length() - 1 - i) - '0';
            int factor = (i < FACTORS.length) ? FACTORS[i] : 0;
            sum += digit * factor;
        }
        int mod = sum % 11;
        int dv = (mod >= 2) ? (11 - mod) : mod;
        return String.valueOf(dv);
    }

    /**
     * Indica si el DV proporcionado corresponde al NIT segun el algoritmo DIAN.
     * Normaliza el DV recibido eliminando un cero a la izquierda (p.ej. "04"
     * se compara como "4") para tolerar el formato de 2 caracteres.
     *
     * @param nit NIT numerico
     * @param dv  digito de verificacion a comprobar
     * @return {@code true} si el DV coincide con el calculado; {@code false} en
     *         caso contrario o si el NIT no es numerico valido
     */
    public static boolean isValid(String nit, String dv) {
        String expected = compute(nit);
        if (expected == null || dv == null) {
            return false;
        }
        String provided = dv.trim();
        // Tolerar DV de 2 caracteres con cero a la izquierda ("04" -> "4").
        if (provided.length() == 2 && provided.charAt(0) == '0') {
            provided = provided.substring(1);
        }
        return expected.equals(provided);
    }
}
