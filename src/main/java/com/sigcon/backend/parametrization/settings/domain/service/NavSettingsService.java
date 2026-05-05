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

import java.util.ArrayList;
import java.util.List;

/**
 * HU-PA-NAV-01: configuracion del orden de los modulos en el sidebar.
 *
 * <p>El orden se persiste en {@code companies.module_order} como array JSON
 * de IDs de modulos. NULL = usa orden default del sistema (definido por
 * {@code modules.menu_order}).
 *
 * <p>El orden es global a la empresa. NO afecta los permisos: si un usuario
 * no tiene acceso al modulo, no lo vera aunque este primero en la lista
 * (HU-PA-NAV-01 E5).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NavSettingsService {

    private final CompanyRepository companyRepository;
    private final AuditPublisher auditPublisher;

    @Autowired(required = false)
    private ObjectMapper objectMapper;

    private ObjectMapper mapper() {
        return objectMapper != null ? objectMapper : new ObjectMapper();
    }

    /**
     * HU-PA-NAV-01 E1: leer el orden actual. Si la empresa no tiene orden
     * configurado (JSON NULL), devuelve lista vacia para que el frontend
     * use el orden default.
     */
    @Transactional(readOnly = true)
    public List<Long> getOrder(Long companyId) {
        Company c = companyRepository.findById(companyId)
                .orElseThrow(() -> new IllegalArgumentException("Empresa no encontrada"));
        if (c.getModuleOrder() == null || c.getModuleOrder().isBlank()) {
            return List.of();
        }
        try {
            return mapper().readValue(c.getModuleOrder(),
                    mapper().getTypeFactory().constructCollectionType(ArrayList.class, Long.class));
        } catch (JsonProcessingException ex) {
            log.warn("module_order JSON invalido en company {}, ignorando: {}", companyId, ex.getMessage());
            return List.of();
        }
    }

    /**
     * HU-PA-NAV-01 E3: persiste el orden. Valida que no haya duplicados ni
     * IDs negativos. Audita la accion con array antes/despues.
     */
    @Transactional
    public List<Long> saveOrder(List<Long> ordered) {
        Long tenant = TenantContext.getCompanyId();
        if (tenant == null) {
            throw new IllegalStateException(
                "No se puede guardar el orden: el usuario no pertenece a una empresa");
        }
        if (ordered == null) {
            throw new IllegalArgumentException("La lista de modulos es obligatoria");
        }
        // Validacion: sin duplicados
        long distinct = ordered.stream().filter(x -> x != null && x > 0).distinct().count();
        if (distinct != ordered.size() || ordered.contains(null)) {
            throw new IllegalArgumentException(
                "La lista contiene IDs duplicados o invalidos");
        }

        Company c = companyRepository.findById(tenant)
                .orElseThrow(() -> new IllegalStateException("Empresa actual no encontrada"));
        String previous = c.getModuleOrder();
        try {
            String json = mapper().writeValueAsString(ordered);
            c.setModuleOrder(json);
            companyRepository.save(c);
            auditPublisher.publishUpdate(AuditModule.PA, "Company.moduleOrder", tenant,
                    "Orden de modulos cambiado | antes=" + (previous == null ? "[]" : previous)
                            + " | despues=" + json);
            return ordered;
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Error serializando orden: " + ex.getMessage(), ex);
        }
    }

    /**
     * Reset al orden por defecto (NULL en BD). Frontend usa orden de modules.menu_order.
     */
    @Transactional
    public void resetOrder() {
        Long tenant = TenantContext.getCompanyId();
        if (tenant == null) {
            throw new IllegalStateException("No hay empresa activa");
        }
        Company c = companyRepository.findById(tenant)
                .orElseThrow(() -> new IllegalStateException("Empresa actual no encontrada"));
        String previous = c.getModuleOrder();
        c.setModuleOrder(null);
        companyRepository.save(c);
        auditPublisher.publishUpdate(AuditModule.PA, "Company.moduleOrder", tenant,
                "Orden de modulos reseteado a default | antes=" + (previous == null ? "[]" : previous));
    }
}
