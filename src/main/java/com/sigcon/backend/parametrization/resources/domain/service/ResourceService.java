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
import com.sigcon.backend.parametrization.resources.domain.model.Country;
import com.sigcon.backend.parametrization.resources.domain.model.Municipality;
import com.sigcon.backend.parametrization.resources.domain.model.PaymentTerms;
import com.sigcon.backend.parametrization.resources.domain.repository.CountryRepository;
import com.sigcon.backend.parametrization.resources.domain.repository.MunicipalityRepository;
import com.sigcon.backend.parametrization.resources.domain.repository.PaymentTermsRepository;
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

    private final DataTableSpecificationBuilder<PaymentTerms> paymentTermsSpecificationBuilder =
    new DataTableSpecificationBuilder<>();
    
    private final DataTableSpecificationBuilder<Municipality> municipalitySpecificationBuilder =
    new DataTableSpecificationBuilder<>();

    private final DataTableSpecificationBuilder<Country> countrySpecificationBuilder =
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

}
