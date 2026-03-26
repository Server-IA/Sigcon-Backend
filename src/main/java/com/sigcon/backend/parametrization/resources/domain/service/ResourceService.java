package com.sigcon.backend.parametrization.resources.domain.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.sigcon.backend.parametrization.resources.application.CountryDTO;
import com.sigcon.backend.parametrization.resources.application.MunicipalityDTO;
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
import com.sigcon.backend.parametrization.resources.domain.repository.PaymentTermsRepository;
import com.sigcon.backend.parametrization.resources.domain.repository.TypeOrganizationRepository;
import com.sigcon.backend.parametrization.resources.domain.repository.TypeRegimenRepository;
import com.sigcon.backend.parametrization.resources.domain.repository.WithholdingRepository;
import com.sigcon.backend.utils.DataTableRequest;
import com.sigcon.backend.utils.DataTableResponse;
import com.sigcon.backend.utils.DataTableSpecificationBuilder;

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

}
