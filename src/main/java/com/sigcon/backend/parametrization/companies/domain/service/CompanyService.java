package com.sigcon.backend.parametrization.companies.domain.service;

import com.sigcon.backend.general.storage.AvatarStorageService;
import com.sigcon.backend.parametrization.companies.application.CompanyDTO;
import com.sigcon.backend.parametrization.companies.application.CompanyLocationDTO;
import com.sigcon.backend.parametrization.companies.application.CreateCompanyLocationRequest;
import com.sigcon.backend.parametrization.companies.application.CreateCompanyRequest;
import com.sigcon.backend.parametrization.companies.application.LogoCompany;
import com.sigcon.backend.parametrization.companies.application.UpdateCompanyLocationRequest;
import com.sigcon.backend.parametrization.companies.application.UpdateCompanyRequest;
import com.sigcon.backend.parametrization.companies.domain.model.Company;
import com.sigcon.backend.parametrization.companies.domain.model.CompanyLocation;
import com.sigcon.backend.parametrization.companies.domain.model.CompanyStatus;
import com.sigcon.backend.parametrization.companies.domain.model.CompanyWithholdingAssignment;
import com.sigcon.backend.parametrization.companies.domain.repository.CompanyLocationRepository;
import com.sigcon.backend.parametrization.companies.domain.repository.CompanyRepository;
import com.sigcon.backend.parametrization.companies.domain.repository.CompanyWithholdingAssignmentRepository;
import com.sigcon.backend.parametrization.resources.application.CountryDTO;
import com.sigcon.backend.parametrization.resources.application.MunicipalityDTO;
import com.sigcon.backend.parametrization.resources.application.TypeOrganizationDTO;
import com.sigcon.backend.parametrization.resources.application.TypeRegimenDTO;
import com.sigcon.backend.parametrization.resources.application.WithholdingDTO;
import com.sigcon.backend.parametrization.resources.domain.model.Country;
import com.sigcon.backend.parametrization.resources.domain.model.Municipality;
import com.sigcon.backend.parametrization.resources.domain.model.TypeOrganization;
import com.sigcon.backend.parametrization.resources.domain.model.TypeRegimen;
import com.sigcon.backend.parametrization.resources.domain.model.Withholding;
import com.sigcon.backend.parametrization.resources.domain.repository.MunicipalityRepository;
import com.sigcon.backend.parametrization.resources.domain.repository.TypeOrganizationRepository;
import com.sigcon.backend.parametrization.resources.domain.repository.TypeRegimenRepository;
import com.sigcon.backend.parametrization.resources.domain.repository.WithholdingRepository;
import com.sigcon.backend.utils.DataTableRequest;
import com.sigcon.backend.utils.DataTableResponse;
import com.sigcon.backend.utils.DataTableSpecificationBuilder;
import com.sigcon.backend.utils.ErrorRespondJson;
import com.sigcon.backend.utils.SuccessRespondJson;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CompanyService {

    private static final int MAX_PAGE_SIZE = 100;

    private final CompanyRepository companyRepository;
    private final CompanyLocationRepository companyLocationRepository;
    private final CompanyWithholdingAssignmentRepository companyWithholdingAssignmentRepository;
    private final TypeRegimenRepository typeRegimenRepository;
    private final TypeOrganizationRepository typeOrganizationRepository;
    private final MunicipalityRepository municipalityRepository;
    private final WithholdingRepository withholdingRepository;

    private final AvatarStorageService avatarStorageService;

    private final DataTableSpecificationBuilder<Company> dataTableSpecificationBuilder =
            new DataTableSpecificationBuilder<>();

    @Transactional
    public ResponseEntity<?> create(CreateCompanyRequest request, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondJson(bindingResult));
        }

        validateMandatoryFields(request);
        validateNitAndDvFormat(request.getNit(), request.getDv());

        // Validar name único
        if (companyRepository.existsByNameAndDeletedAtIsNull(request.getName().trim())) {
            return ResponseEntity.badRequest().body(
                    ErrorRespondJson.getErrorRespondMessage(
                            Optional.of("El nombre de la compañía ya existe. Debe ser unico.")));
        }

        // Validar NIT + DV único
        if (companyRepository.existsByNitAndDvAndDeletedAtIsNull(request.getNit(), request.getDv())) {
            return ResponseEntity.badRequest().body(
                    ErrorRespondJson.getErrorRespondMessage(
                            Optional.of("La combinacion NIT + DV ya existe para otra compañía.")));
        }

        // Validar que venga al menos una sede
        if (request.getLocations() == null) {
            return ResponseEntity.badRequest().body(
                    ErrorRespondJson.getErrorRespondMessage(
                            Optional.of("Debe proporcionar al menos una sede para la compañía (sede principal).")));
        }

        TypeRegimen typeRegimen = resolveTypeRegimen(request.getTypeRegimeId());
        TypeOrganization typeOrganization = resolveTypeOrganization(request.getTypeOrganizationId());

        String logoName = null;
        if(request.getLogo() != null) {
            LogoCompany logo = request.getLogo();
            String base64 = logo.getBase64();
            String name = logo.getName();
            if(!base64.isEmpty() && !name.isEmpty()) {
                String logoPath = avatarStorageService.saveBase64Avatar(base64, name);
                logoName = logoPath;
            }
        }

        Company company = Company.builder()
                .name(request.getName().trim())
                .nit(request.getNit().trim())
                .dv(request.getDv().trim())
                .legalRepresentative(emptyToNull(request.getLegalRepresentative()))
                .email(emptyToNull(request.getEmail()))
                .size(emptyToNull(request.getSize()))
                .phone(emptyToNull(request.getPhone()))
                .logo(emptyToNull(logoName))
                .status(request.getStatus() != null ? request.getStatus() : CompanyStatus.ACTIVE)
                .typeRegimen(typeRegimen)
                .typeOrganization(typeOrganization)
                .build();

        companyRepository.save(company);

        // Crear la primera sede como principal (is_main = true)
        CompanyLocation locations = createInitialLocations(company, request.getLocations());

        if(request.getWithholdings() != null && !request.getWithholdings().isEmpty()) {
            for(Long withholdingId : request.getWithholdings()) {
                Withholding withholding = withholdingRepository.findById(withholdingId)
                        .orElseThrow(() -> new IllegalArgumentException("La retencion no existe en el catalogo."));
                CompanyWithholdingAssignment assignment = CompanyWithholdingAssignment.builder()
                        .company(company)
                        .withholding(withholding)
                        .build();
                companyWithholdingAssignmentRepository.save(assignment);
            }
        }

        List<CompanyWithholdingAssignment> withholdings = companyWithholdingAssignmentRepository.findByCompanyAndDeletedAtIsNull(company);

        CompanyDTO dto = toDto(company, Arrays.asList(locations), withholdings);

        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(
                        Optional.of("Compania creada correctamente."),
                        Optional.of(dto)
                )
        );
    }

    public ResponseEntity<?> findAllPaged(DataTableRequest safeRequest) {
        // DataTableRequest safeRequest = normalizeDataTableRequest(request);
        // validateDataTableRequest(safeRequest);

        int start = Math.max(0, safeRequest.getStart());
        int length = safeRequest.getLength();
        int safeLength = length <= 0 ? 20 : length;
        int page = start / safeLength;

        Pageable pageable = length == -1
                ? Pageable.unpaged()
                : PageRequest.of(page, safeLength);

        Specification<Company> spec = dataTableSpecificationBuilder.build(safeRequest)
                .and((root, query, cb) -> cb.isNull(root.get("deletedAt")));

        Page<Company> companies = companyRepository.findAll(spec, pageable);

        if (companies.isEmpty()) {
            return ResponseEntity.badRequest().body(
                    ErrorRespondJson.getErrorRespondMessage(
                            Optional.of("No se encontraron compañías con los criterios de busqueda especificados.")));
        }

        Page<CompanyDTO> mapped = companies.map(company -> {
            List<CompanyLocation> locations = companyLocationRepository.findByCompanyAndDeletedAtIsNull(company);
            List<CompanyWithholdingAssignment> withholdings = companyWithholdingAssignmentRepository.findByCompanyAndDeletedAtIsNull(company);
            return toDto(company, locations, withholdings);
        });

        DataTableResponse<CompanyDTO> response = DataTableResponse.from(mapped, safeRequest.getDraw());
        return ResponseEntity.ok(response);
    }

    public ResponseEntity<?> getDetail(Long id) {
        Company company = getCompanyOrThrow(id);
        List<CompanyLocation> locations = companyLocationRepository.findByCompanyAndDeletedAtIsNull(company);
        List<CompanyWithholdingAssignment> withholdings = companyWithholdingAssignmentRepository.findByCompanyAndDeletedAtIsNull(company);

        CompanyDTO dto = toDto(company, locations, withholdings);

        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(
                        Optional.of("Informacion detallada de la compañía obtenida correctamente."),
                        Optional.of(dto)
                )
        );
    }

    @Transactional
    public ResponseEntity<?> update(Long id, UpdateCompanyRequest request, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondJson(bindingResult));
        }

        Company company = getCompanyOrThrow(id);

        String targetNit = request.getNit() != null ? request.getNit().trim() : company.getNit();
        String targetDv = request.getDv() != null ? request.getDv().trim() : company.getDv();

        validateNitAndDvFormat(targetNit, targetDv);
        if (companyRepository.existsByNitAndDvAndIdNotAndDeletedAtIsNull(targetNit, targetDv, id)) {
            return ResponseEntity.badRequest().body(
                    ErrorRespondJson.getErrorRespondMessage(
                            Optional.of("La combinacion NIT + DV ya existe para otra compañía.")));
        }

        if (StringUtils.hasText(request.getName())) {
            String newName = request.getName().trim();
            // Validar name único si cambió
            if (!newName.equalsIgnoreCase(company.getName()) && 
                companyRepository.existsByNameAndIdNotAndDeletedAtIsNull(newName, id)) {
                return ResponseEntity.badRequest().body(
                    ErrorRespondJson.getErrorRespondMessage(
                            Optional.of("El nombre de la compañía ya existe. Debe ser unico.")));
            }
            company.setName(newName);
        }

        String logoName = null;
        if(request.getLogo() != null) {
            LogoCompany logo = request.getLogo();
            logoName = logo.getName();
            if(!logoName.isEmpty() && logo.getBase64() != null) {
                String base64 = logo.getBase64();
                String logoPath = avatarStorageService.saveBase64Avatar(base64, logoName);
                logoName = logoPath;
            }
        }

        company.setNit(targetNit);
        company.setDv(targetDv);
        company.setLegalRepresentative(emptyToNull(request.getLegalRepresentative()));
        company.setEmail(emptyToNull(request.getEmail()));
        company.setSize(emptyToNull(request.getSize()));
        company.setPhone(emptyToNull(request.getPhone()));
        company.setLogo(emptyToNull(logoName));

        if (request.getStatus() != null) {
            company.setStatus(request.getStatus());
        }

        if (request.getTypeRegimeId() != null) {
            TypeRegimen typeRegimen = resolveTypeRegimen(request.getTypeRegimeId());
            company.setTypeRegimen(typeRegimen);
        }

        if (request.getTypeOrganizationId() != null) {
            TypeOrganization typeOrganization = resolveTypeOrganization(request.getTypeOrganizationId());
            company.setTypeOrganization(typeOrganization);
        }

        companyRepository.save(company);

        // Sincronizar retenciones si viene el campo (si viene null, no se toca)
        syncWithholdingsIfProvided(company, request.getWithholdings());

        List<CompanyLocation> locations = companyLocationRepository.findByCompanyAndDeletedAtIsNull(company);
        List<CompanyWithholdingAssignment> withholdings = companyWithholdingAssignmentRepository.findByCompanyAndDeletedAtIsNull(company);

        CompanyDTO dto = toDto(company, locations, withholdings);

        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(
                        Optional.of("Compania actualizada correctamente."),
                        Optional.of(dto)
                )
        );
    }

    @Transactional
    public ResponseEntity<?> delete(Long id) {
        Company company = getCompanyOrThrow(id);
        if (company.getDeletedAt() != null) {
            return ResponseEntity.badRequest().body(
                    ErrorRespondJson.getErrorRespondMessage(
                            Optional.of("La compañía seleccionada no existe o ya fue eliminada.")));
        }

        company.setDeletedAt(LocalDateTime.now());
        companyRepository.save(company);

        List<CompanyLocation> locations = companyLocationRepository.findByCompanyAndDeletedAtIsNull(company);
        List<CompanyWithholdingAssignment> withholdings = companyWithholdingAssignmentRepository.findByCompanyAndDeletedAtIsNull(company);
        LocalDateTime now = LocalDateTime.now();
        
        for (CompanyLocation location : locations) {
            location.setDeletedAt(now);
        }
        if (!locations.isEmpty()) {
            companyLocationRepository.saveAll(locations);
        }
        
        for (CompanyWithholdingAssignment assignment : withholdings) {
            assignment.setDeletedAt(now);
        }
        if (!withholdings.isEmpty()) {
            companyWithholdingAssignmentRepository.saveAll(withholdings);
        }

        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(
                        Optional.of("Compania eliminada correctamente."),
                        Optional.empty()
                )
        );
    }

    @Transactional
    public ResponseEntity<?> deleteLocation(Long id) {
        CompanyLocation location = companyLocationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("La sede seleccionada no existe."));

        if (location.getDeletedAt() != null) {
            return ResponseEntity.badRequest().body(
                    ErrorRespondJson.getErrorRespondMessage(
                            Optional.of("La sede seleccionada no existe o ya fue eliminada.")));
        }

        Company company = location.getCompany();
        List<CompanyLocation> activeLocations = companyLocationRepository.findByCompanyAndDeletedAtIsNull(company);

        // No permitir que la compañía se quede sin sedes
        if (activeLocations.size() <= 1) {
            return ResponseEntity.badRequest().body(
                    ErrorRespondJson.getErrorRespondMessage(
                            Optional.of("No es posible eliminar la unica sede activa de la compañía.")));
        }

        boolean isMain = Boolean.TRUE.equals(location.getIsMain());

        // Eliminacion logica de la sede seleccionada (manteniendo la misma estrategia de delete)
        location.setDeletedAt(LocalDateTime.now());
        companyLocationRepository.save(location);

        // Si se elimina la sede principal, reasignar una nueva sede principal si existe otra
        if (isMain) {
            // Buscar otra sede activa distinta a la eliminada, priorizando la mas antigua
            activeLocations.stream()
                    .filter(loc -> !loc.getId().equals(location.getId()))
                    .sorted((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()))
                    .findFirst()
                    .ifPresent(newMain -> {
                        newMain.setIsMain(true);
                        companyLocationRepository.save(newMain);
                    });
        }

        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(
                        Optional.of("Sede eliminada correctamente."),
                        Optional.empty()
                )
        );
    }

    @Transactional
    public ResponseEntity<?> createLocation(Long companyId, CreateCompanyLocationRequest request, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondJson(bindingResult));
        }

        Company company = getCompanyOrThrow(companyId);
        Municipality municipality = resolveMunicipality(request.getMunicipalityId());

        List<CompanyLocation> current = companyLocationRepository.findByCompanyAndDeletedAtIsNull(company);

        // Las sedes adicionales siempre son sucursales (is_main = false)
        // Solo la primera sede creada al crear la compañía puede ser principal
        boolean isMain = false;
        
        // Si se intenta marcar como principal, desmarcar la actual principal
        if (Boolean.TRUE.equals(request.getIsMain())) {
            for (CompanyLocation loc : current) {
                loc.setIsMain(false);
            }
            isMain = true;
        }

        CompanyLocation location = CompanyLocation.builder()
                .company(company)
                .municipality(municipality)
                .name(request.getName())
                .description(emptyToNull(request.getDescription()))
                .address(request.getAddress())
                .status(request.getStatus() != null ? request.getStatus() : CompanyStatus.ACTIVE)
                .isMain(isMain)
                .build();

        current.add(location);
        List<CompanyLocation> saved = companyLocationRepository.saveAll(current);

        List<CompanyLocation> activeLocations = saved.stream()
                .filter(loc -> loc.getDeletedAt() == null)
                .collect(Collectors.toList());

        List<CompanyWithholdingAssignment> withholdings = companyWithholdingAssignmentRepository.findByCompanyAndDeletedAtIsNull(company);

        CompanyDTO dto = toDto(company, activeLocations, withholdings);

        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(
                        Optional.of("Sede creada correctamente."),
                        Optional.of(dto)
                )
        );
    }

    @Transactional
    public ResponseEntity<?> updateLocation(Long id, UpdateCompanyLocationRequest request, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondJson(bindingResult));
        }

        CompanyLocation location = companyLocationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("La sede seleccionada no existe."));

        if (location.getDeletedAt() != null) {
            return ResponseEntity.badRequest().body(
                    ErrorRespondJson.getErrorRespondMessage(
                            Optional.of("La sede seleccionada no existe o ya fue eliminada.")));
        }

        if (StringUtils.hasText(request.getName())) {
            location.setName(request.getName());
        }
        if (request.getDescription() != null) {
            location.setDescription(emptyToNull(request.getDescription()));
        }
        if (StringUtils.hasText(request.getAddress())) {
            location.setAddress(request.getAddress());
        }
        if (request.getStatus() != null) {
            location.setStatus(request.getStatus());
        }
        if (request.getMunicipalityId() != null) {
            Municipality municipality = resolveMunicipality(request.getMunicipalityId());
            location.setMunicipality(municipality);
        }

        if (request.getIsMain() != null) {
            Company company = location.getCompany();
            List<CompanyLocation> companyLocations = companyLocationRepository.findByCompanyAndDeletedAtIsNull(company);
            if (Boolean.TRUE.equals(request.getIsMain())) {
                for (CompanyLocation loc : companyLocations) {
                    loc.setIsMain(false);
                }
                location.setIsMain(true);
            } else {
                location.setIsMain(false);
            }
            companyLocationRepository.saveAll(companyLocations);
        } else {
            companyLocationRepository.save(location);
        }

        Company company = location.getCompany();
        List<CompanyLocation> activeLocations = companyLocationRepository.findByCompanyAndDeletedAtIsNull(company);
        List<CompanyWithholdingAssignment> withholdings = companyWithholdingAssignmentRepository.findByCompanyAndDeletedAtIsNull(company);

        CompanyDTO dto = toDto(company, activeLocations, withholdings);

        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(
                        Optional.of("Sede actualizada correctamente."),
                        Optional.of(dto)
                )
        );
    }

    // ===== Helpers =====

    private void validateMandatoryFields(CreateCompanyRequest request) {
        if (!StringUtils.hasText(request.getName())
                || !StringUtils.hasText(request.getNit())
                || !StringUtils.hasText(request.getDv())
                || request.getTypeRegimeId() == null
                || request.getTypeOrganizationId() == null) {
            throw new IllegalArgumentException("Por favor diligencie todos los campos obligatorios de la compañía.");
        }
    }

    private void validateNitAndDvFormat(String nit, String dv) {
        if (nit == null || !nit.matches("^\\d{5,15}$")) {
            throw new IllegalArgumentException("Formato de NIT invalido. Solo numeros, entre 5 y 15 caracteres.");
        }
        if (dv == null || !dv.matches("^\\d{1}$")) {
            throw new IllegalArgumentException("Formato de DV invalido. Solo un digito numerico.");
        }
    }

    private String emptyToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private void syncWithholdingsIfProvided(Company company, List<Long> incomingWithholdingIds) {
        if (incomingWithholdingIds == null) {
            return; // no tocar
        }

        List<CompanyWithholdingAssignment> current =
                companyWithholdingAssignmentRepository.findByCompanyAndDeletedAtIsNull(company);

        // Si viene lista vacía: borrar (lógico) todas
        if (incomingWithholdingIds.isEmpty()) {
            if (!current.isEmpty()) {
                LocalDateTime now = LocalDateTime.now();
                for (CompanyWithholdingAssignment a : current) {
                    a.setDeletedAt(now);
                }
                companyWithholdingAssignmentRepository.saveAll(current);
            }
            return;
        }

        // Validar y normalizar IDs (sin duplicados)
        Set<Long> incomingSet = new HashSet<>();
        for (Long id : incomingWithholdingIds) {
            if (id == null || id <= 0) {
                throw new IllegalArgumentException("Id de retencion invalido.");
            }
            incomingSet.add(id);
        }

        // Mapear actuales por withholdingId
        Map<Long, CompanyWithholdingAssignment> currentByWithholdingId = current.stream()
                .filter(a -> a.getWithholding() != null && a.getWithholding().getId() != null)
                .collect(Collectors.toMap(a -> a.getWithholding().getId(), a -> a, (a, b) -> a));

        LocalDateTime now = LocalDateTime.now();

        // Eliminar (lógico) las que ya no vienen
        for (CompanyWithholdingAssignment a : current) {
            Long wid = a.getWithholding() != null ? a.getWithholding().getId() : null;
            if (wid != null && !incomingSet.contains(wid)) {
                a.setDeletedAt(now);
            }
        }
        if (!current.isEmpty()) {
            companyWithholdingAssignmentRepository.saveAll(current);
        }

        // Crear las nuevas que faltan
        for (Long withholdingId : incomingSet) {
            if (currentByWithholdingId.containsKey(withholdingId)) {
                continue;
            }
            Withholding withholding = withholdingRepository.findById(withholdingId)
                    .orElseThrow(() -> new IllegalArgumentException("La retencion no existe en el catalogo."));
            CompanyWithholdingAssignment assignment = CompanyWithholdingAssignment.builder()
                    .company(company)
                    .withholding(withholding)
                    .build();
            companyWithholdingAssignmentRepository.save(assignment);
        }
    }

    private TypeRegimen resolveTypeRegimen(Long typeRegimenId) {
        if (typeRegimenId == null) {
            throw new IllegalArgumentException("El tipo de regimen es obligatorio.");
        }
        return typeRegimenRepository.findById(typeRegimenId)
                .orElseThrow(() -> new IllegalArgumentException("El tipo de regimen no existe en el catalogo."));
    }

    private TypeOrganization resolveTypeOrganization(Long typeOrganizationId) {
        if (typeOrganizationId == null) {
            throw new IllegalArgumentException("El tipo de organizacion es obligatorio.");
        }
        return typeOrganizationRepository.findById(typeOrganizationId)
                .orElseThrow(() -> new IllegalArgumentException("El tipo de organizacion no existe en el catalogo."));
    }

    private Municipality resolveMunicipality(Long municipalityId) {
        if (municipalityId == null) {
            throw new IllegalArgumentException("El municipio es obligatorio para la sede.");
        }
        return municipalityRepository.findById(municipalityId)
                .orElseThrow(() -> new IllegalArgumentException("El municipio no existe en el catalogo."));
    }

    private Company getCompanyOrThrow(Long id) {
        return companyRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("La compañía no existe o fue eliminada."));
    }

    private List<CompanyLocation> upsertLocationsForCompany(Company company, List<CompanyLocationDTO> locationDTOs) {
        List<CompanyLocation> current = companyLocationRepository.findByCompanyAndDeletedAtIsNull(company);

        if (locationDTOs == null) {
            return current;
        }

        // Marcar las que ya no vienen en el request como eliminadas logicamente
        Set<Long> incomingIds = locationDTOs.stream()
                .map(CompanyLocationDTO::getId)
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toSet());

        LocalDateTime now = LocalDateTime.now();
        for (CompanyLocation existing : current) {
            if (existing.getId() != null && !incomingIds.contains(existing.getId())) {
                existing.setDeletedAt(now);
            }
        }

        // Mapear por id para actualizacion rapida
        var byId = current.stream()
                .filter(loc -> loc.getId() != null)
                .collect(Collectors.toMap(CompanyLocation::getId, loc -> loc));

        List<CompanyLocation> toPersist = new ArrayList<>(current);

        boolean hasMain = false;

        for (CompanyLocationDTO dto : locationDTOs) {
            if (dto.getIsMain() != null && dto.getIsMain()) {
                hasMain = true;
                break;
            }
        }

        for (CompanyLocationDTO dto : locationDTOs) {
            Municipality municipality = resolveMunicipality(dto.getMunicipalityId());

            boolean isMain = Boolean.TRUE.equals(dto.getIsMain());
            // Si ninguna sede vino marcada como principal, se toma la primera como principal
            if (!hasMain) {
                isMain = locationDTOs.indexOf(dto) == 0;
            }

            if (dto.getId() != null && byId.containsKey(dto.getId())) {
                CompanyLocation existing = byId.get(dto.getId());
                existing.setMunicipality(municipality);
                existing.setName(dto.getName());
                existing.setDescription(emptyToNull(dto.getDescription()));
                existing.setAddress(dto.getAddress());
                existing.setStatus(dto.getStatus() != null ? dto.getStatus() : CompanyStatus.ACTIVE);
                existing.setIsMain(isMain);
            } else {
                CompanyLocation created = CompanyLocation.builder()
                        .company(company)
                        .municipality(municipality)
                        .name(dto.getName())
                        .description(emptyToNull(dto.getDescription()))
                        .address(dto.getAddress())
                        .status(dto.getStatus() != null ? dto.getStatus() : CompanyStatus.ACTIVE)
                        .isMain(isMain)
                        .build();
                toPersist.add(created);
            }
        }

        List<CompanyLocation> saved = companyLocationRepository.saveAll(toPersist);

        return saved.stream()
                .filter(loc -> loc.getDeletedAt() == null)
                .collect(Collectors.toList());
    }

    private CompanyLocation createInitialLocations(Company company, CreateCompanyLocationRequest req) {
        
        Municipality municipality = resolveMunicipality(req.getMunicipalityId());
            
            CompanyLocation location = CompanyLocation.builder()
                    .company(company)
                    .municipality(municipality)
                    .name(req.getName())
                    .description(emptyToNull(req.getDescription()))
                    .address(req.getAddress())
                    .status(req.getStatus() != null ? req.getStatus() : CompanyStatus.ACTIVE)
                    .isMain(true)
                    .build();
        return companyLocationRepository.save(location);
    }

    private CompanyDTO toDto(Company company, List<CompanyLocation> locations, List<CompanyWithholdingAssignment> withholdings) {
        CompanyLocation mainLocation = locations == null ? null :
                locations.stream()
                        .filter(loc -> Boolean.TRUE.equals(loc.getIsMain()))
                        .findFirst()
                        .orElse(null);

        List<CompanyLocationDTO> locationDTOs = locations == null ? List.of()
                : locations.stream()
                .map(this::toLocationDto)
                .collect(Collectors.toList());

        List<WithholdingDTO> withholdingDTOs = withholdings == null ? List.of()
                : withholdings.stream()
                .map(assignment -> toWithholdingDto(assignment.getWithholding()))
                .collect(Collectors.toList());

        return CompanyDTO.builder()
                .id(company.getId())
                .name(company.getName())
                .nit(company.getNit())
                .dv(company.getDv())
                .legalRepresentative(company.getLegalRepresentative())
                .email(company.getEmail())
                .size(company.getSize())
                .phone(company.getPhone())
                .logo(company.getLogo())
                .status(company.getStatus())
                .typeRegimeId(company.getTypeRegimen() != null ? company.getTypeRegimen().getId() : null)
                .typeRegimeName(company.getTypeRegimen() != null ? company.getTypeRegimen().getName() : null)
                .typeRegimeCode(company.getTypeRegimen() != null ? company.getTypeRegimen().getCode() : null)
                .typeRegimen(company.getTypeRegimen() != null ? toTypeRegimenDto(company.getTypeRegimen()) : null)
                .typeOrganizationId(company.getTypeOrganization() != null ? company.getTypeOrganization().getId() : null)
                .typeOrganizationName(company.getTypeOrganization() != null ? company.getTypeOrganization().getName() : null)
                .typeOrganizationCode(company.getTypeOrganization() != null ? company.getTypeOrganization().getCode() : null)
                .typeOrganization(company.getTypeOrganization() != null ? toTypeOrganizationDto(company.getTypeOrganization()) : null)
                .mainLocationId(mainLocation != null ? mainLocation.getId() : null)
                .mainAddress(mainLocation != null ? mainLocation.getAddress() : null)
                .locations(locationDTOs)
                .withholdings(withholdingDTOs)
                .createdAt(company.getCreatedAt())
                .updatedAt(company.getUpdatedAt())
                .deletedAt(company.getDeletedAt())
                .build();
    }

    private WithholdingDTO toWithholdingDto(Withholding withholding) {
        if (withholding == null) {
            return null;
        }
        return WithholdingDTO.builder()
                .id(withholding.getId())
                .name(withholding.getName())
                .code(withholding.getCode())
                .createdAt(withholding.getCreatedAt())
                .updatedAt(withholding.getUpdatedAt())
                .deletedAt(withholding.getDeletedAt())
                .build();
    }

    private TypeRegimenDTO toTypeRegimenDto(TypeRegimen typeRegimen) {
        if (typeRegimen == null) {
            return null;
        }
        return TypeRegimenDTO.builder()
                .id(typeRegimen.getId())
                .name(typeRegimen.getName())
                .code(typeRegimen.getCode())
                .createdAt(typeRegimen.getCreatedAt())
                .updatedAt(typeRegimen.getUpdatedAt())
                .deletedAt(typeRegimen.getDeletedAt())
                .build();
    }

    private TypeOrganizationDTO toTypeOrganizationDto(TypeOrganization typeOrganization) {
        if (typeOrganization == null) {
            return null;
        }
        return TypeOrganizationDTO.builder()
                .id(typeOrganization.getId())
                .name(typeOrganization.getName())
                .code(typeOrganization.getCode())
                .createdAt(typeOrganization.getCreatedAt())
                .updatedAt(typeOrganization.getUpdatedAt())
                .deletedAt(typeOrganization.getDeletedAt())
                .build();
    }

    private CompanyLocationDTO toLocationDto(CompanyLocation location) {
        Municipality municipality = location.getMunicipality();
        Country country = municipality != null ? municipality.getCountry() : null;

        return CompanyLocationDTO.builder()
                .id(location.getId())
                .name(location.getName())
                .description(location.getDescription())
                .address(location.getAddress())
                .status(location.getStatus())
                .isMain(location.getIsMain())
                .municipalityId(municipality != null ? municipality.getId() : null)
                .municipality(toMunicipalityDto(municipality))
                .country(toCountryDto(country))
                .createdAt(location.getCreatedAt())
                .updatedAt(location.getUpdatedAt())
                .deletedAt(location.getDeletedAt())
                .build();
    }

    private MunicipalityDTO toMunicipalityDto(Municipality municipality) {
        if (municipality == null) {
            return null;
        }
        return MunicipalityDTO.builder()
                .id(municipality.getId())
                .name(municipality.getName())
                .code(municipality.getCode())
                .country(toCountryDto(municipality.getCountry()))
                .createdAt(municipality.getCreatedAt())
                .updatedAt(municipality.getUpdatedAt())
                .deletedAt(municipality.getDeletedAt())
                .build();
    }

    private CountryDTO toCountryDto(Country country) {
        if (country == null) {
            return null;
        }
        return CountryDTO.builder()
                .id(country.getId())
                .name(country.getName())
                .code(country.getCode())
                .createdAt(country.getCreatedAt())
                .updatedAt(country.getUpdatedAt())
                .deletedAt(country.getDeletedAt())
                .build();
    }

    private String mapDataTableColumn(String columnName) {
        return switch (columnName) {
            // Campos del DTO (lo que manda el frontend) -> rutas del entity (lo que espera JPA)
            case "legalRepresentative" -> "legalRepresentative";

            case "typeRegimeName" -> "typeRegimen.name";
            case "typeRegimeCode" -> "typeRegimen.code";
            case "typeRegimeId" -> "typeRegimen.id";

            case "typeOrganizationName" -> "typeOrganization.name";
            case "typeOrganizationCode" -> "typeOrganization.code";
            case "typeOrganizationId" -> "typeOrganization.id";

            default -> columnName;
        };
    }

    private DataTableRequest normalizeDataTableRequest(DataTableRequest request) {
        DataTableRequest safe = request != null ? request : new DataTableRequest();

        if (safe.getLength() == 0) {
            safe.setLength(20);
        }

        if (safe.getColumns() == null) {
            safe.setColumns(new ArrayList<>());
        }

        if (safe.getSearch() == null) {
            safe.setSearch(new DataTableRequest.DataTableSearch("", false));
        }

        // Normalizar nombres de columnas para que coincidan con los campos del entity
        List<DataTableRequest.DataTableColumn> normalizedColumns = safe.getColumns().stream()
                .map(column -> {
                    if (column == null || !StringUtils.hasText(column.getData())) {
                        return column;
                    }
                    column.setData(mapDataTableColumn(column.getData().trim()));
                    return column;
                })
                .toList();
        safe.setColumns(normalizedColumns);

        return safe;
    }

    private void validateDataTableRequest(DataTableRequest request) {
        if (request.getLength() > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException(
                    "Parametrizacion invalida de paginacion. Limite maximo: 100 registros.");
        }

        Set<String> allowedFields = Set.of(
                "id", "name", "nit", "dv",
                "legalRepresentative", "email", "phone", "logo", "size",
                "status",
                "typeRegimen.id", "typeRegimen.name", "typeRegimen.code",
                "typeOrganization.id", "typeOrganization.name", "typeOrganization.code",
                "createdAt", "updatedAt"
        );

        for (DataTableRequest.DataTableColumn column : request.getColumns()) {
            if (column == null || column.getData() == null || column.getData().isBlank()) {
                continue;
            }
            if (!allowedFields.contains(column.getData())) {
                throw new IllegalArgumentException("Campo de ordenamiento no valido.");
            }
        }
    }

    // ===== CRUD de Withholdings =====

    @Transactional
    public ResponseEntity<?> assignWithholding(Long companyId, Long withholdingId) {
        Company company = getCompanyOrThrow(companyId);
        Withholding withholding = withholdingRepository.findById(withholdingId)
                .orElseThrow(() -> new IllegalArgumentException("La retencion no existe en el catalogo."));

        if (companyWithholdingAssignmentRepository.existsByCompanyAndWithholdingAndDeletedAtIsNull(company, withholding)) {
            return ResponseEntity.badRequest().body(
                    ErrorRespondJson.getErrorRespondMessage(
                            Optional.of("La retencion ya esta asignada a esta compañía.")));
        }

        CompanyWithholdingAssignment assignment = CompanyWithholdingAssignment.builder()
                .company(company)
                .withholding(withholding)
                .build();

        companyWithholdingAssignmentRepository.save(assignment);

        List<CompanyLocation> locations = companyLocationRepository.findByCompanyAndDeletedAtIsNull(company);
        List<CompanyWithholdingAssignment> withholdings = companyWithholdingAssignmentRepository.findByCompanyAndDeletedAtIsNull(company);
        CompanyDTO dto = toDto(company, locations, withholdings);

        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(
                        Optional.of("Retencion asignada correctamente."),
                        Optional.of(dto)
                )
        );
    }

    @Transactional
    public ResponseEntity<?> removeWithholding(Long companyId, Long withholdingId) {
        Company company = getCompanyOrThrow(companyId);
        
        // Validar que la retención existe
        if (!withholdingRepository.existsById(withholdingId)) {
            throw new IllegalArgumentException("La retencion no existe en el catalogo.");
        }

        CompanyWithholdingAssignment assignment = companyWithholdingAssignmentRepository
                .findByCompanyAndDeletedAtIsNull(company)
                .stream()
                .filter(a -> a.getWithholding().getId().equals(withholdingId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("La retencion no esta asignada a esta compañía."));

        assignment.setDeletedAt(LocalDateTime.now());
        companyWithholdingAssignmentRepository.save(assignment);

        List<CompanyLocation> locations = companyLocationRepository.findByCompanyAndDeletedAtIsNull(company);
        List<CompanyWithholdingAssignment> withholdings = companyWithholdingAssignmentRepository.findByCompanyAndDeletedAtIsNull(company);
        CompanyDTO dto = toDto(company, locations, withholdings);

        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(
                        Optional.of("Retencion eliminada correctamente."),
                        Optional.of(dto)
                )
        );
    }
}

