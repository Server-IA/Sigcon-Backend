package com.sigcon.backend.parametrization.settings.domain.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sigcon.backend.audit.domain.model.enums.AuditModule;
import com.sigcon.backend.audit.domain.service.AuditPublisher;
import com.sigcon.backend.platform.companies.domain.model.Company;
import com.sigcon.backend.platform.companies.domain.repository.CompanyRepository;
import com.sigcon.backend.platform.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * HU-PA-BRAND-01: configurar identidad visual de la empresa (colores,
 * logo, favicon, nombre comercial).
 *
 * <p>Validaciones:
 * <ul>
 *   <li>E2: contraste WCAG AA (>= 4.5:1) entre color primario y texto blanco.</li>
 *   <li>E3: formato y tamanio del logo (PNG/JPG/SVG, max 500KB).</li>
 *   <li>E5: reset a default elimina la config persistida.</li>
 *   <li>E7: aislamiento por company_id del JWT (TenantContext).</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BrandConfigService {

    private final CompanyRepository companyRepository;
    private final AuditPublisher auditPublisher;

    @Autowired(required = false)
    private ObjectMapper objectMapper;

    private static final Pattern HEX_COLOR =
            Pattern.compile("^#([0-9a-fA-F]{6}|[0-9a-fA-F]{3})$");
    private static final long MAX_LOGO_BYTES = 500_000;

    private ObjectMapper mapper() {
        return objectMapper != null ? objectMapper : new ObjectMapper();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> get(Long companyId) {
        Company c = companyRepository.findById(companyId)
                .orElseThrow(() -> new IllegalArgumentException("Empresa no encontrada"));
        if (c.getBrandConfig() == null || c.getBrandConfig().isBlank()) {
            return Map.of();
        }
        try {
            return mapper().readValue(c.getBrandConfig(), Map.class);
        } catch (JsonProcessingException ex) {
            log.warn("brand_config JSON invalido en company {}: {}", companyId, ex.getMessage());
            return Map.of();
        }
    }

    /**
     * HU-PA-BRAND-01 E1: persistir configuracion. Valida cada campo segun
     * las reglas E2/E3 antes de guardar.
     */
    @Transactional
    public Map<String, Object> save(Map<String, Object> input) {
        Long tenant = TenantContext.getCompanyId();
        if (tenant == null) {
            throw new IllegalStateException("No hay empresa activa para guardar identidad visual");
        }

        Map<String, Object> cfg = new HashMap<>(input == null ? Map.of() : input);

        // E2 colores
        String primary = strOrNull(cfg.get("primaryColor"));
        String secondary = strOrNull(cfg.get("secondaryColor"));
        if (primary != null && !HEX_COLOR.matcher(primary).matches()) {
            throw new IllegalArgumentException(
                "El color primario debe ser un hex valido (#RRGGBB o #RGB)");
        }
        if (secondary != null && !HEX_COLOR.matcher(secondary).matches()) {
            throw new IllegalArgumentException(
                "El color secundario debe ser un hex valido (#RRGGBB o #RGB)");
        }

        // E3 logo: si viene base64 con data URL, valida MIME y tamanio
        String logoData = strOrNull(cfg.get("logoData"));
        if (logoData != null) {
            validateImageDataUrl(logoData, "logo");
        }
        String faviconData = strOrNull(cfg.get("faviconData"));
        if (faviconData != null) {
            validateImageDataUrl(faviconData, "favicon");
        }

        // E2 contraste (best-effort): si hay primaryColor pero contraste < 4.5:1 con texto blanco,
        // marcamos un warning en el resultado (no bloqueamos por defecto, segun HU "permite guardar
        // pero con warning persistente").
        if (primary != null) {
            double ratio = wcagContrast(primary, "#FFFFFF");
            cfg.put("contrastRatio", round2(ratio));
            cfg.put("wcagAA", ratio >= 4.5);
            if (ratio < 4.5) {
                cfg.put("contrastWarning",
                        "Contraste " + round2(ratio) + ":1, mínimo requerido 4.5:1");
            }
        }

        try {
            String json = mapper().writeValueAsString(cfg);
            Company c = companyRepository.findById(tenant)
                    .orElseThrow(() -> new IllegalStateException("Empresa actual no encontrada"));
            c.setBrandConfig(json);
            companyRepository.save(c);
            auditPublisher.publishUpdate(AuditModule.PA, "Company.brandConfig", tenant,
                    "Identidad visual actualizada: primaryColor=" + primary
                            + " | secondaryColor=" + secondary
                            + " | brandName=" + cfg.get("brandName"));
            return cfg;
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Error serializando brand_config: " + ex.getMessage(), ex);
        }
    }

    /** HU-PA-BRAND-01 E5: reset a default elimina brand_config. */
    @Transactional
    public void reset() {
        Long tenant = TenantContext.getCompanyId();
        if (tenant == null) throw new IllegalStateException("No hay empresa activa");
        Company c = companyRepository.findById(tenant)
                .orElseThrow(() -> new IllegalStateException("Empresa actual no encontrada"));
        c.setBrandConfig(null);
        companyRepository.save(c);
        auditPublisher.publishUpdate(AuditModule.PA, "Company.brandConfig", tenant,
                "Identidad visual reseteada al theme default");
    }

    // ---------- helpers ----------

    private static String strOrNull(Object o) {
        if (o == null) return null;
        String s = String.valueOf(o).trim();
        return s.isEmpty() ? null : s;
    }

    /**
     * Valida data URL de imagen (E3): "data:image/png;base64,xxx".
     * Acepta PNG/JPG/JPEG/SVG. Rechaza si supera 500 KB de payload binario.
     */
    private void validateImageDataUrl(String dataUrl, String label) {
        if (!dataUrl.startsWith("data:image/")) {
            throw new IllegalArgumentException(
                "El " + label + " debe ser PNG transparente, JPG o SVG");
        }
        int commaIdx = dataUrl.indexOf(',');
        if (commaIdx < 0) {
            throw new IllegalArgumentException(
                "El " + label + " debe ser un data URL valido (data:image/...;base64,...)");
        }
        String mime = dataUrl.substring(5, dataUrl.indexOf(';', 5));
        if (!mime.equals("image/png") && !mime.equals("image/jpeg")
                && !mime.equals("image/jpg") && !mime.equals("image/svg+xml")) {
            throw new IllegalArgumentException(
                "El " + label + " debe ser PNG transparente, JPG o SVG");
        }
        String base64 = dataUrl.substring(commaIdx + 1);
        // Decoded size = base64Len * 3/4 (sin padding)
        long approxBytes = (long) base64.length() * 3 / 4;
        if (approxBytes > MAX_LOGO_BYTES) {
            throw new IllegalArgumentException(
                "El archivo excede el tamaño máximo permitido");
        }
    }

    /**
     * Calculo de contraste WCAG (W3C). Recibe dos colores hex (ej. "#1E5DAB", "#FFFFFF")
     * y devuelve el ratio: 1.0 (sin contraste) -> 21.0 (negro vs blanco).
     */
    public static double wcagContrast(String hex1, String hex2) {
        double l1 = relativeLuminance(hex1);
        double l2 = relativeLuminance(hex2);
        double lighter = Math.max(l1, l2);
        double darker = Math.min(l1, l2);
        return (lighter + 0.05) / (darker + 0.05);
    }

    private static double relativeLuminance(String hex) {
        int[] rgb = parseHex(hex);
        double r = sRgbToLinear(rgb[0] / 255.0);
        double g = sRgbToLinear(rgb[1] / 255.0);
        double b = sRgbToLinear(rgb[2] / 255.0);
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    private static double sRgbToLinear(double c) {
        return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    private static int[] parseHex(String hex) {
        String h = hex.startsWith("#") ? hex.substring(1) : hex;
        if (h.length() == 3) {
            h = "" + h.charAt(0) + h.charAt(0) + h.charAt(1) + h.charAt(1) + h.charAt(2) + h.charAt(2);
        }
        return new int[] {
            Integer.parseInt(h.substring(0, 2), 16),
            Integer.parseInt(h.substring(2, 4), 16),
            Integer.parseInt(h.substring(4, 6), 16)
        };
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
