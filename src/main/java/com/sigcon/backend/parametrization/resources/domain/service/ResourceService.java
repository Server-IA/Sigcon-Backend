package com.sigcon.backend.parametrization.resources.domain.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.sigcon.backend.invoices.domain.model.PaymentForms;
import com.sigcon.backend.parametrization.resources.application.CountryDTO;
import com.sigcon.backend.parametrization.resources.application.MunicipalityDTO;
import com.sigcon.backend.parametrization.resources.application.PaymentFormsDTO;
import com.sigcon.backend.parametrization.resources.application.PaymentMethodDTO;
import com.sigcon.backend.parametrization.resources.application.PaymentTermsDTO;
import com.sigcon.backend.parametrization.resources.application.TypeOrganizationDTO;
import com.sigcon.backend.parametrization.resources.application.TypeRegimenDTO;
import com.sigcon.backend.parametrization.resources.application.WithholdingDTO;
import com.sigcon.backend.parametrization.resources.domain.model.Country;
import com.sigcon.backend.parametrization.resources.domain.model.Municipality;
import com.sigcon.backend.parametrization.resources.domain.model.PaymentTerms;
import com.sigcon.backend.parametrization.resources.domain.model.TypeOrganization;
import com.sigcon.backend.parametrization.resources.domain.model.TypeRegimen;
import com.sigcon.backend.parametrization.resources.domain.model.Withholding;
import com.sigcon.backend.parametrization.resources.domain.repository.CountryRepository;
import com.sigcon.backend.parametrization.resources.domain.repository.MunicipalityRepository;
import com.sigcon.backend.parametrization.resources.domain.repository.PaymentFormRepository;
import com.sigcon.backend.parametrization.resources.domain.repository.PaymentTermsRepository;
import com.sigcon.backend.parametrization.resources.domain.repository.TypeOrganizationRepository;
import com.sigcon.backend.parametrization.resources.domain.repository.TypeRegimenRepository;
import com.sigcon.backend.parametrization.resources.domain.repository.WithholdingRepository;
import com.sigcon.backend.utils.DataTableRequest;
import com.sigcon.backend.utils.DataTableResponse;
import com.sigcon.backend.utils.DataTableSpecificationBuilder;
import com.sigcon.backend.audit.domain.model.enums.AuditModule;
import com.sigcon.backend.audit.domain.service.AuditPublisher;

import lombok.RequiredArgsConstructor;


@Service
@RequiredArgsConstructor

public class ResourceService {

    private final CountryRepository countryRepository;
    private final MunicipalityRepository municipalityRepository;
    private final PaymentTermsRepository paymentTermsRepository;
    private final TypeRegimenRepository typesRegimeRepository;
    private final TypeOrganizationRepository typesOrganizationRepository;
    private final WithholdingRepository withholdingRepository;
    private final PaymentFormRepository paymentFormsRepository;
    private final AuditPublisher auditPublisher;

    private final DataTableSpecificationBuilder<PaymentTerms> paymentTermsSpecificationBuilder =
    new DataTableSpecificationBuilder<>();
    
    private final DataTableSpecificationBuilder<Municipality> municipalitySpecificationBuilder =
    new DataTableSpecificationBuilder<>();

    private final DataTableSpecificationBuilder<Country> countrySpecificationBuilder =
    new DataTableSpecificationBuilder<>();

    private final DataTableSpecificationBuilder<TypeRegimen> typesRegimeSpecificationBuilder =
    new DataTableSpecificationBuilder<>();

    private final DataTableSpecificationBuilder<TypeOrganization> typesOrganizationSpecificationBuilder =
    new DataTableSpecificationBuilder<>();

    private final DataTableSpecificationBuilder<Withholding> withholdingSpecificationBuilder =
    new DataTableSpecificationBuilder<>();

    private final DataTableSpecificationBuilder<PaymentForms> paymentFormSpecificationBuilder =
    new DataTableSpecificationBuilder<>();

    /**
     * Lista municipios con paginacion y filtros DataTable.
     * Incluye datos del pais asociado a cada municipio.
     *
     * @param dtRequest parametros de paginacion, busqueda y orden del DataTable
     * @return ResponseEntity con DataTableResponse de municipios
     */
    public ResponseEntity<?> getMunicipalities(DataTableRequest dtRequest) {
        try {

            int start = Math.max(0, dtRequest.getStart());
            int length = dtRequest.getLength();

            int safeLength = length <= 0 ? 10 : length;
            int page = start / safeLength;

            Pageable pageable = length == -1
                ? Pageable.unpaged()
                : PageRequest.of(page, safeLength);

            Specification<Municipality> specification = municipalitySpecificationBuilder.build(dtRequest);
            
            Page<Municipality> municipalities = municipalityRepository.findAll(specification, pageable);

            return ResponseEntity.ok(
                DataTableResponse.from(municipalities.map(this::toMunicipalityDTO), dtRequest.getDraw())
            );
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    /**
     * Lista paises con paginacion y filtros DataTable.
     * Incluye la lista de municipios asociados a cada pais.
     *
     * @param dtRequest parametros de paginacion, busqueda y orden del DataTable
     * @return ResponseEntity con DataTableResponse de paises con sus municipios
     */
    public ResponseEntity<?> getCountries(DataTableRequest dtRequest) {
        try {
            int start = Math.max(0, dtRequest.getStart());
            int length = dtRequest.getLength();

            int safeLength = length <= 0 ? 10 : length;
            int page = start / safeLength;

            Pageable pageable = length == -1
                ? Pageable.unpaged()
                : PageRequest.of(page, safeLength);

            Specification<Country> specification = countrySpecificationBuilder.build(dtRequest);

            Page<Country> countries = countryRepository.findAll(specification, pageable);

            return ResponseEntity.ok(
                DataTableResponse.from(countries.map(this::toCountryCompleteDTO), dtRequest.getDraw())
            );
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    /**
     * Lista terminos de pago con paginacion y filtros DataTable.
     * Los terminos de pago definen los dias de plazo para facturas (ej: 30, 60, 90 dias).
     *
     * @param dtRequest parametros de paginacion, busqueda y orden del DataTable
     * @return ResponseEntity con DataTableResponse de terminos de pago
     */
    public ResponseEntity<?> getAllPaymentTerms(DataTableRequest dtRequest) {
        try {
            int start = Math.max(0, dtRequest.getStart());
            int length = dtRequest.getLength();

            int safeLength = length <= 0 ? 10 : length > 100 ? 100 : length;
            int page = start / safeLength;

            Pageable pageable = length == -1
                ? Pageable.unpaged()
                : PageRequest.of(page, safeLength);

            Specification<PaymentTerms> specification = paymentTermsSpecificationBuilder.build(dtRequest);

            Page<PaymentTerms> paymentTerms = paymentTermsRepository.findAll(specification, pageable);

            return ResponseEntity.ok(
                DataTableResponse.from(paymentTerms.map(this::toPaymentTermsDTO), dtRequest.getDraw())
            );
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    /**
     * Lista tipos de regimen tributario con paginacion y filtros DataTable.
     * Regimenes segun normativa colombiana: comun, simplificado, gran contribuyente, etc.
     *
     * @param dtRequest parametros de paginacion, busqueda y orden del DataTable
     * @return ResponseEntity con DataTableResponse de regimenes tributarios
     */
    public ResponseEntity<?> getAllTypesRegimes(DataTableRequest dtRequest) {
        try {

            int start = Math.max(0, dtRequest.getStart());
            int length = dtRequest.getLength();

            int safeLength = length <= 0 ? 10 : length > 100 ? 100 : length;
            int page = start / safeLength;

            Pageable pageable = length == -1
                ? Pageable.unpaged()
                : PageRequest.of(page, safeLength);

            Specification<TypeRegimen> specification = typesRegimeSpecificationBuilder.build(dtRequest);

            Page<TypeRegimen> typesRegimes = typesRegimeRepository.findAll(specification, pageable);

            return ResponseEntity.ok(
                DataTableResponse.from(typesRegimes.map(this::toTypeRegimenDTO), dtRequest.getDraw())
            );

        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    /**
     * Lista tipos de organizacion con paginacion y filtros DataTable.
     * Clasificacion segun DIAN: persona natural, persona juridica, etc.
     *
     * @param dtRequest parametros de paginacion, busqueda y orden del DataTable
     * @return ResponseEntity con DataTableResponse de tipos de organizacion
     */
    public ResponseEntity<?> getAllTypesOrganizations(DataTableRequest dtRequest) {
        try {

            int start = Math.max(0, dtRequest.getStart());
            int length = dtRequest.getLength();

            int safeLength = length <= 0 ? 10 : length > 100 ? 100 : length;
            int page = start / safeLength;

            Pageable pageable = length == -1
                ? Pageable.unpaged()
                : PageRequest.of(page, safeLength);

            Specification<TypeOrganization> specification = typesOrganizationSpecificationBuilder.build(dtRequest);

            Page<TypeOrganization> typesOrganizations = typesOrganizationRepository.findAll(specification, pageable);

            return ResponseEntity.ok(
                DataTableResponse.from(typesOrganizations.map(this::toTypeOrganizationDTO), dtRequest.getDraw())
            );
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    /**
     * Lista retenciones disponibles con paginacion y filtros DataTable.
     * Catalogo de retenciones aplicables segun Estatuto Tributario colombiano.
     *
     * @param dtRequest parametros de paginacion, busqueda y orden del DataTable
     * @return ResponseEntity con DataTableResponse de retenciones
     */
    public ResponseEntity<?> getAllWithholdings(DataTableRequest dtRequest) {
        try {
            int start = Math.max(0, dtRequest.getStart());
            int length = dtRequest.getLength();

            int safeLength = length <= 0 ? 10 : length > 100 ? 100 : length;
            int page = start / safeLength;

            Pageable pageable = length == -1
                ? Pageable.unpaged()
                : PageRequest.of(page, safeLength);

            Specification<Withholding> specification = withholdingSpecificationBuilder.build(dtRequest);

            Page<Withholding> withholdings = withholdingRepository.findAll(specification, pageable);

            return ResponseEntity.ok(
                DataTableResponse.from(withholdings.map(this::toWithholdingDTO), dtRequest.getDraw())
            );
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    /**
     * Lista formas de pago con paginacion y filtros DataTable.
     * Incluye el indicador isContado que diferencia pagos de contado vs credito.
     *
     * @param dtRequest parametros de paginacion, busqueda y orden del DataTable
     * @return ResponseEntity con DataTableResponse de formas de pago
     */
    public ResponseEntity<?> getAllPaymentForms(DataTableRequest dtRequest) {
        try {
            int start = Math.max(0, dtRequest.getStart());
            int length = dtRequest.getLength();
            
            int safeLength = length <= 0 ? 10 : length > 100 ? 100 : length;
            int page = start / safeLength;

            Pageable pageable = length == -1
                ? Pageable.unpaged()
                : PageRequest.of(page, safeLength);

            Specification<PaymentForms> specification = paymentFormSpecificationBuilder.build(dtRequest);

            Page<PaymentForms> paymentForms = paymentFormsRepository.findAll(specification, pageable);

            return ResponseEntity.ok(
                DataTableResponse.from(paymentForms.map(this::toPaymentFormDTO), dtRequest.getDraw())
            );
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    // ===== CRUD PAÍSES (HU-PA-RF-50 a 53) =====

    /** HU-PA-RF-50: Crear país validando unicidad de código */
    public ResponseEntity<?> createCountry(CountryDTO request) {
        if (request.getName() == null || request.getName().trim().isEmpty())
            throw new IllegalArgumentException("El nombre del país es obligatorio");
        if (request.getCode() == null || request.getCode().trim().isEmpty())
            throw new IllegalArgumentException("El código del país es obligatorio");
        if (countryRepository.findByCodeIgnoreCase(request.getCode().trim()).isPresent())
            throw new IllegalArgumentException("Ya existe un país con el código ingresado");
        Country country = Country.builder().name(request.getName().trim()).code(request.getCode().trim().toUpperCase()).build();
        country = countryRepository.save(country);
        auditPublisher.publishCreate(AuditModule.PA, "Country", country.getId(),
                "Pais creado: " + country.getName() + " (" + country.getCode() + ")");
        return ResponseEntity.ok(new java.util.LinkedHashMap<String, Object>() {{ put("success", true); put("message", "País creado exitosamente"); }});
    }

    /** HU-PA-RF-52: Editar país */
    public ResponseEntity<?> updateCountry(Long id, CountryDTO request) {
        Country country = countryRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("País no encontrado"));
        var existing = countryRepository.findByCodeIgnoreCase(request.getCode().trim());
        if (existing.isPresent() && !existing.get().getId().equals(id))
            throw new IllegalArgumentException("Ya existe otro país con ese código");
        country.setName(request.getName().trim());
        country.setCode(request.getCode().trim().toUpperCase());
        countryRepository.save(country);
        auditPublisher.publishUpdate(AuditModule.PA, "Country", country.getId(),
                "Pais actualizado: " + country.getName() + " (" + country.getCode() + ")");
        return ResponseEntity.ok(new java.util.LinkedHashMap<String, Object>() {{ put("success", true); put("message", "País actualizado exitosamente"); }});
    }

    /** HU-PA-RF-53: Eliminar país validando municipios asociados */
    public ResponseEntity<?> deleteCountry(Long id) {
        Country country = countryRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("País no encontrado"));
        var municipios = municipalityRepository.findByCountryId(id);
        if (!municipios.isEmpty())
            throw new IllegalArgumentException("No se puede eliminar: tiene " + municipios.size() + " municipio(s) asociado(s)");
        country.setDeletedAt(java.time.LocalDateTime.now());
        countryRepository.save(country);
        auditPublisher.publishDelete(AuditModule.PA, "Country", country.getId(),
                "Pais eliminado: " + country.getName());
        return ResponseEntity.ok(new java.util.LinkedHashMap<String, Object>() {{ put("success", true); put("message", "País eliminado exitosamente"); }});
    }

    // ===== CRUD MUNICIPIOS (HU-PA-RF-54 a 57) =====

    /** HU-PA-RF-54: Crear municipio */
    public ResponseEntity<?> createMunicipality(MunicipalityDTO request) {
        if (request.getName() == null || request.getName().trim().isEmpty())
            throw new IllegalArgumentException("El nombre del municipio es obligatorio");
        if (request.getCode() == null || request.getCode().trim().isEmpty())
            throw new IllegalArgumentException("El código del municipio es obligatorio");
        if (request.getCountry() == null || request.getCountry().getId() == null)
            throw new IllegalArgumentException("Debe seleccionar un país");
        Country country = countryRepository.findById(request.getCountry().getId())
            .orElseThrow(() -> new IllegalArgumentException("País no encontrado"));
        if (municipalityRepository.findByCodeIgnoreCase(request.getCode().trim()).isPresent())
            throw new IllegalArgumentException("Ya existe un municipio con ese código");
        Municipality mun = Municipality.builder().name(request.getName().trim()).code(request.getCode().trim()).country(country).build();
        mun = municipalityRepository.save(mun);
        auditPublisher.publishCreate(AuditModule.PA, "Municipality", mun.getId(),
                "Municipio creado: " + mun.getName() + " (" + mun.getCode() + ")");
        return ResponseEntity.ok(new java.util.LinkedHashMap<String, Object>() {{ put("success", true); put("message", "Municipio creado exitosamente"); }});
    }

    /** HU-PA-RF-56: Editar municipio */
    public ResponseEntity<?> updateMunicipality(Long id, MunicipalityDTO request) {
        Municipality mun = municipalityRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Municipio no encontrado"));
        var existing = municipalityRepository.findByCodeIgnoreCase(request.getCode().trim());
        if (existing.isPresent() && !existing.get().getId().equals(id))
            throw new IllegalArgumentException("Ya existe otro municipio con ese código");
        if (request.getCountry() != null && request.getCountry().getId() != null) {
            Country country = countryRepository.findById(request.getCountry().getId())
                .orElseThrow(() -> new IllegalArgumentException("País no encontrado"));
            mun.setCountry(country);
        }
        mun.setName(request.getName().trim());
        mun.setCode(request.getCode().trim());
        municipalityRepository.save(mun);
        auditPublisher.publishUpdate(AuditModule.PA, "Municipality", mun.getId(),
                "Municipio actualizado: " + mun.getName() + " (" + mun.getCode() + ")");
        return ResponseEntity.ok(new java.util.LinkedHashMap<String, Object>() {{ put("success", true); put("message", "Municipio actualizado exitosamente"); }});
    }

    /** HU-PA-RF-57: Eliminar municipio */
    public ResponseEntity<?> deleteMunicipality(Long id) {
        Municipality mun = municipalityRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Municipio no encontrado"));
        mun.setDeletedAt(java.time.LocalDateTime.now());
        municipalityRepository.save(mun);
        auditPublisher.publishDelete(AuditModule.PA, "Municipality", mun.getId(),
                "Municipio eliminado: " + mun.getName());
        return ResponseEntity.ok(new java.util.LinkedHashMap<String, Object>() {{ put("success", true); put("message", "Municipio eliminado exitosamente"); }});
    }

    // Mappeadores

    private MunicipalityDTO toMunicipalityDTO(Municipality municipality) {
        return MunicipalityDTO.builder()
            .id(municipality.getId())
            .name(municipality.getName())
            .code(municipality.getCode())
            .country(toCountryDTO(municipality.getCountry()))
            .build();
    }
    
    private CountryDTO toCountryDTO(Country country) {
        return CountryDTO.builder()
            .id(country.getId())
            .name(country.getName())
            .code(country.getCode())
            .build();
    }

    private CountryDTO toCountryCompleteDTO(Country country) {

        List<Municipality> municipalities = municipalityRepository.findByCountryId(country.getId());

        return CountryDTO.builder()
            .id(country.getId())
            .name(country.getName())
            .code(country.getCode())
            .municipalities(municipalities.stream()
                .map(this::toMunicipalitySimpleDTO)
                .collect(Collectors.toList()))
            .build();
    }

    private MunicipalityDTO toMunicipalitySimpleDTO(Municipality municipality) {
        return MunicipalityDTO.builder()
            .id(municipality.getId())
            .name(municipality.getName())
            .code(municipality.getCode())
            .build();
    }

    private PaymentTermsDTO toPaymentTermsDTO(PaymentTerms paymentTerms) {
        return PaymentTermsDTO.builder()
            .id(paymentTerms.getId())
            .name(paymentTerms.getName())
            .days(paymentTerms.getDays())
            .createdAt(paymentTerms.getCreatedAt())
            .updatedAt(paymentTerms.getUpdatedAt())
            .deletedAt(paymentTerms.getDeletedAt())
            .build();
    }

    private TypeRegimenDTO toTypeRegimenDTO(TypeRegimen typeRegimen) {
        return TypeRegimenDTO.builder()
            .id(typeRegimen.getId())
            .name(typeRegimen.getName())
            .code(typeRegimen.getCode())
            .build();
    }

    private TypeOrganizationDTO toTypeOrganizationDTO(TypeOrganization typeOrganization) {
        return TypeOrganizationDTO.builder()
            .id(typeOrganization.getId())
            .name(typeOrganization.getName())
            .code(typeOrganization.getCode())
            .build();
    }

    private WithholdingDTO toWithholdingDTO(Withholding withholding) {
        return WithholdingDTO.builder()
            .id(withholding.getId())
            .name(withholding.getName())
            .code(withholding.getCode())
            .build();
    }

    private PaymentFormsDTO toPaymentFormDTO(PaymentForms paymentForm) {
        return PaymentFormsDTO.builder()
            .id(paymentForm.getId())
            .name(paymentForm.getName())
            .code(paymentForm.getCode())
            .isContado(paymentForm.getIsContado())
            .build();
    }

}
