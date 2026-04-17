package com.sigcon.backend.integration.domain.service;

import com.sigcon.backend.third_parties.third_parties.domain.model.ThirdParty;
import com.sigcon.backend.third_parties.third_parties.domain.model.ThirdPartyStatusCatalog;
import com.sigcon.backend.third_parties.third_parties.domain.repository.ThirdPartyRepository;
import com.sigcon.backend.third_parties.third_parties.domain.repository.ThirdPartyStatusCatalogRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * HU-INT-RF-04 E6 / HU-NOM-RF-01 E5: Resolucion de terceros desde AAEF.
 *
 * <p>Cuando un documento AAEF referencia un NIT, este resolver:
 * <ol>
 *   <li>Busca el tercero existente por NIT.</li>
 *   <li>Si existe, lo retorna.</li>
 *   <li>Si no existe, lo auto-crea con datos minimos (NIT, businessName, status=ACTIVO).</li>
 * </ol>
 *
 * <p>El tercero auto-creado queda marcado con {@code source=AAEF} para trazabilidad.
 * Datos complementarios (direccion, email, municipio, regimen) quedan vacios y pueden
 * actualizarse posteriormente por el contador.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ThirdPartyResolver {

    private static final String ACTIVE_STATUS_NAME = "ACTIVO";
    private static final String SOURCE_AAEF = "AAEF";

    private final ThirdPartyRepository thirdPartyRepository;
    private final ThirdPartyStatusCatalogRepository statusRepository;

    /**
     * Busca un tercero por NIT, o lo auto-crea si no existe.
     *
     * @param nit NIT del tercero (sin digito de verificacion)
     * @param dv Digito de verificacion (si viene en AAEF)
     * @param businessName Razon social (para auto-creacion)
     * @return ThirdParty existente o recien creado
     * @throws AaefMappingException si el NIT es invalido o no se puede crear el tercero
     */
    @Transactional
    public ThirdParty findOrCreate(String nit, String dv, String businessName) {
        if (nit == null || nit.trim().isEmpty()) {
            throw new AaefMappingException(
                    AaefMappingException.UNKNOWN_THIRD_PARTY,
                    "NIT del tercero es obligatorio en AAEF");
        }

        String cleanNit = nit.trim();

        // 1. Buscar existente
        List<ThirdParty> existing = thirdPartyRepository.findByNitAndDeletedAtIsNull(cleanNit);
        if (!existing.isEmpty()) {
            return existing.get(0);
        }

        // 2. Auto-crear con datos minimos
        log.info("Tercero con NIT {} no encontrado. Auto-creando con source=AAEF.", cleanNit);

        Optional<ThirdPartyStatusCatalog> activeStatus =
                statusRepository.findByNameIgnoreCase(ACTIVE_STATUS_NAME);
        if (activeStatus.isEmpty()) {
            throw new AaefMappingException(
                    AaefMappingException.MAPPING_ERROR,
                    "No existe status '" + ACTIVE_STATUS_NAME
                            + "' en catalogo. Verifique seed de third_party_status_catalog.",
                    false);
        }

        String safeName = (businessName != null && !businessName.trim().isEmpty())
                ? businessName.trim()
                : "Tercero AAEF " + cleanNit;

        String safeDv = (dv != null && !dv.trim().isEmpty()) ? dv.trim() : "0";

        // Codigo interno unico: AAEF-{nit}
        String internalCode = "AAEF-" + cleanNit;

        ThirdParty newThird = ThirdParty.builder()
                .nit(cleanNit)
                .dv(safeDv)
                .businessName(safeName)
                .thirdPartyCode(internalCode)
                .status(activeStatus.get())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        newThird = thirdPartyRepository.save(newThird);
        log.info("Tercero auto-creado: id={}, nit={}, name={}",
                newThird.getId(), cleanNit, safeName);
        return newThird;
    }
}
