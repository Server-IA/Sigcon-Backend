package com.sigcon.backend.parametrization.parameters.domain.model.enums;

/**
 * Categorias de parametros del sistema.
 *
 * <ul>
 *   <li>{@code COLOR} — parametros visuales (colores de UI)</li>
 *   <li>{@code FONT} — tipografias</li>
 *   <li>{@code COMPANY} — datos de la empresa (NIT, razon social, etc.)</li>
 *   <li>{@code INTEGRATION_AGROFUSION} — configuracion de la integracion AAEF con AgroFusion
 *       (API key, callback URL, JWKS URL, issuer). Agregado en Fase 1 de integracion (V32).</li>
 *   <li>{@code NOMINA} — parametros del modulo de nomina: SMLV vigente, UVT para retencion
 *       en la fuente. Agregado en V9-G.</li>
 * </ul>
 */
public enum CategoryParameter {
    COLOR,
    FONT,
    COMPANY,
    INTEGRATION_AGROFUSION,
    NOMINA
}
