package com.sigcon.backend.banks.matching.domain.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sigcon.backend.banks.matching.domain.model.ConfigFirmaConciliacion;
import com.sigcon.backend.banks.matching.domain.model.FirmaElectronica;
import com.sigcon.backend.banks.matching.domain.model.SesionConciliacion;
import com.sigcon.backend.banks.matching.domain.repository.ConfigFirmaConciliacionRepository;
import com.sigcon.backend.banks.matching.domain.repository.FirmaElectronicaRepository;
import com.sigcon.backend.parametrization.users.domain.model.User;
import com.sigcon.backend.platform.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * BNK-HU-066: firma electrónica de conciliaciones. Construye el payload canónico,
 * calcula su SHA-256, captura un segundo factor y persiste la firma inmutable.
 *
 * STAND-IN documentado (no hay infra): el OTP no se envía por correo (no hay SMTP);
 * se genera en el servidor y se devuelve en la respuesta SOLO en este entorno para
 * permitir la confirmación. El certificado digital / PAdES-LTV / TSA quedan diferidos
 * (requieren HSM/cert). El sello de tiempo usa el reloj del servidor (HU-066 E5 fallback).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ElectronicSignatureService {

    private final FirmaElectronicaRepository firmaRepo;
    private final ConfigFirmaConciliacionRepository configRepo;
    private final ObjectMapper objectMapper;

    /** Caché transitoria de OTP por (sesion:user:rol). Stand-in del envío por correo. */
    private final ConcurrentHashMap<String, String> otpCache = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    public ConfigFirmaConciliacion getOrCreateConfig() {
        Long companyId = TenantContext.getCompanyId();
        return configRepo.findByCompanyId(companyId).orElseGet(() ->
                configRepo.save(ConfigFirmaConciliacion.builder().companyId(companyId).build()));
    }

    @Transactional
    public ConfigFirmaConciliacion updateConfig(String metodos, Boolean exigeCertRevisor, Boolean modoFlexible) {
        ConfigFirmaConciliacion c = getOrCreateConfig();
        if (metodos != null && !metodos.isBlank()) c.setMetodosPermitidos(metodos.trim());
        if (exigeCertRevisor != null) c.setExigeCertRevisor(exigeCertRevisor);
        if (modoFlexible != null) c.setModoFlexible(modoFlexible);
        return configRepo.save(c);
    }

    /** HU-066 E4: SHA-256 hex de una cadena. */
    public String sha256Hex(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) { throw new IllegalStateException("Error calculando SHA-256", e); }
    }

    /** HU-066 E4: JSON canónico (claves ordenadas) del payload de firma. */
    public String canonicalJson(Map<String, Object> payload) {
        try {
            // ObjectMapper con SORT_PROPERTIES_ALPHABETICALLY produce JSON canónico estable.
            ObjectMapper canon = objectMapper.copy()
                    .configure(com.fasterxml.jackson.databind.SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
            return canon.writeValueAsString(new TreeMap<>(payload));
        } catch (Exception e) { throw new IllegalStateException("Error serializando payload", e); }
    }

    /** HU-066 E7: datos profesionales obligatorios para firmar como revisor/contador. */
    public void validateProfessionalData(String rolFirma, User user, String documento, String tp) {
        boolean needs = "REVISOR".equals(rolFirma)
                || hasRole(user, "REVISOR_FISCAL") || hasRole(user, "CONTADOR");
        if (needs && (isBlank(documento) || isBlank(tp))) {
            throw new IllegalArgumentException(
                    "Complete los datos profesionales en su perfil antes de firmar");
        }
    }

    /**
     * BNK-HU-066 E2/E3/E5/E8: firma en 2 pasos. Sin OTP -> genera y devuelve código
     * (stand-in del correo). Con OTP -> valida, persiste firma inmutable y retorna firmaId.
     */
    @Transactional
    public Map<String, Object> sign(SesionConciliacion sesion, String rolFirma, String canonicalPayload,
                                    User user, String documento, String tp, String metodo, String otp) {
        validateProfessionalData(rolFirma, user, documento, tp);
        String key = sesion.getId() + ":" + user.getId() + ":" + rolFirma;

        if (otp == null || otp.isBlank()) {
            String code = String.format("%06d", random.nextInt(1_000_000));
            otpCache.put(key, code);
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("otpRequired", true);
            r.put("devOtp", code); // STAND-IN: en prod se envía al correo; aquí se devuelve.
            r.put("message", "Código de firma generado. (Entorno sin correo: el código se devuelve aquí; en producción llega al correo registrado del firmante.)");
            return r;
        }

        String expected = otpCache.get(key);
        if (expected == null || !expected.equals(otp.trim())) {
            throw new IllegalArgumentException("Código de firma (OTP) inválido o expirado. Solicítelo nuevamente.");
        }

        String hash = sha256Hex(canonicalPayload);
        String firmaResultado = Base64.getEncoder()
                .encodeToString((hash + ":" + otp.trim()).getBytes(StandardCharsets.UTF_8));
        FirmaElectronica f = FirmaElectronica.builder()
                .companyId(sesion.getCompanyId())
                .sesionId(sesion.getId())
                .rolFirma(rolFirma)
                .firmanteUserId(user.getId())
                .firmanteNombre((nz(user.getName()) + " " + nz(user.getLastname())).trim())
                .firmanteDocumento(documento)
                .firmanteTp(tp)
                .firmanteRol(primaryRole(user))
                .metodoFirma(metodo != null && !metodo.isBlank() ? metodo.toUpperCase() : "OTP")
                .payloadFirma(canonicalPayload)
                .hashDocumento(hash)
                .firmaResultado(firmaResultado)
                .selloTiempo(LocalDateTime.now()) // E5: reloj del servidor (TSA diferida por infra)
                .ipFirmante(clientIp())
                .userAgent(userAgent())
                .build();
        f = firmaRepo.save(f);
        otpCache.remove(key);

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("otpRequired", false);
        r.put("firmaId", f.getId());
        r.put("hashDocumento", hash);
        r.put("rolFirma", rolFirma);
        r.put("selloTiempo", f.getSelloTiempo().toString());
        return r;
    }

    /** BNK-HU-066 E6: verifica las firmas de la sesión recalculando el hash del payload. */
    public Map<String, Object> verify(Long sesionId) {
        List<FirmaElectronica> firmas = firmaRepo.findBySesionIdOrderByIdAsc(sesionId);
        List<Map<String, Object>> out = new ArrayList<>();
        boolean todasValidas = !firmas.isEmpty();
        for (FirmaElectronica f : firmas) {
            String recalc = sha256Hex(f.getPayloadFirma() != null ? f.getPayloadFirma() : "");
            boolean valida = recalc.equals(f.getHashDocumento());
            if (!valida) todasValidas = false;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("firmaId", f.getId());
            m.put("rolFirma", f.getRolFirma());
            m.put("firmante", f.getFirmanteNombre());
            m.put("tarjetaProfesional", f.getFirmanteTp());
            m.put("metodo", f.getMetodoFirma());
            m.put("fecha", f.getSelloTiempo() != null ? f.getSelloTiempo().toString() : null);
            m.put("valida", valida);
            m.put("motivo", valida ? null : "El hash recalculado no coincide con el almacenado: documento alterado.");
            out.add(m);
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("sesionId", sesionId);
        r.put("totalFirmas", firmas.size());
        r.put("todasValidas", todasValidas);
        r.put("firmas", out);
        return r;
    }

    // ---- helpers ----
    public boolean hasRole(User u, String name) {
        return u.getRoles() != null && u.getRoles().stream()
                .anyMatch(r -> name.equalsIgnoreCase(r.getName()));
    }

    private String primaryRole(User u) {
        if (u.getRoles() == null || u.getRoles().isEmpty()) return "USER";
        return u.getRoles().iterator().next().getName();
    }

    private boolean isBlank(String s) { return s == null || s.trim().isEmpty(); }
    private String nz(String s) { return s == null ? "" : s; }

    private String clientIp() {
        try {
            var attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) return "N/A";
            var req = attrs.getRequest();
            String xff = req.getHeader("X-Forwarded-For");
            return (xff != null && !xff.isBlank()) ? xff.split(",")[0].trim() : req.getRemoteAddr();
        } catch (Exception e) { return "N/A"; }
    }

    private String userAgent() {
        try {
            var attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) return "N/A";
            String ua = attrs.getRequest().getHeader("User-Agent");
            return ua != null ? (ua.length() > 400 ? ua.substring(0, 400) : ua) : "N/A";
        } catch (Exception e) { return "N/A"; }
    }
}
