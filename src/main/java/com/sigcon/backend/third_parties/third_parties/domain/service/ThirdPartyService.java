package com.sigcon.backend.third_parties.third_parties.domain.service;

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
import com.sigcon.backend.third_parties.third_parties.application.BulkThirdPartyUploadRequest;
import com.sigcon.backend.third_parties.third_parties.application.BulkThirdPartyUploadResponse;
import com.sigcon.backend.third_parties.third_parties.application.ThirdPartyDTO;
import com.sigcon.backend.third_parties.third_parties.application.ThirdPartyDetailDTO;
import com.sigcon.backend.third_parties.third_parties.application.ThirdPartyRoleCatalogDTO;
import com.sigcon.backend.third_parties.third_parties.application.ThirdPartyStatusCatalogDTO;
import com.sigcon.backend.third_parties.third_parties.application.ThirdContactDTO;
import com.sigcon.backend.third_parties.third_parties.application.UpdateThirdPartyRolesStatusRequest;
import com.sigcon.backend.third_parties.third_parties.domain.model.ThirdContact;
import com.sigcon.backend.third_parties.third_parties.domain.model.ThirdParty;
import com.sigcon.backend.third_parties.third_parties.domain.model.ThirdPartyRoleCatalog;
import com.sigcon.backend.third_parties.third_parties.domain.model.ThirdPartyStatusCatalog;
import com.sigcon.backend.third_parties.third_parties.domain.model.ThirdPartyWithholdingAssignment;
import com.sigcon.backend.third_parties.third_parties.domain.repository.ThirdPartyRepository;
import com.sigcon.backend.third_parties.third_parties.domain.repository.ThirdPartyRoleCatalogRepository;
import com.sigcon.backend.third_parties.third_parties.domain.repository.ThirdPartyStatusCatalogRepository;

import com.sigcon.backend.utils.DataTableRequest;
import com.sigcon.backend.utils.DataTableResponse;
import com.sigcon.backend.utils.DataTableSpecificationBuilder;
import com.sigcon.backend.utils.ErrorRespondJson;
import com.sigcon.backend.utils.SuccessRespondJson;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.BindingResult;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.xml.parsers.DocumentBuilderFactory;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ThirdPartyService {

    private static final int MAX_BULK_ROWS = 10_000;

    private final ThirdPartyRepository thirdPartyRepository;
    private final ThirdPartyRoleCatalogRepository roleCatalogRepository;
    private final ThirdPartyStatusCatalogRepository statusCatalogRepository;
    private final MunicipalityRepository municipalityRepository;
    private final TypeOrganizationRepository typeOrganizationRepository;
    private final TypeRegimenRepository typeRegimenRepository;
    private final WithholdingRepository withholdingRepository;
    private final DataTableSpecificationBuilder<ThirdParty> dataTableSpecificationBuilder = new DataTableSpecificationBuilder<>();

    public ResponseEntity<?> create(ThirdPartyDTO request, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondJson(bindingResult));
        }

        validateRequiredFields(request);
        validateNitAndDvFormat(request.getNit(), request.getDv());

        if (thirdPartyRepository.existsByNitAndDvAndDeletedAtIsNull(request.getNit(), request.getDv())) {
            throw new IllegalArgumentException("TERC_011: El NIT + DV ya existe en el sistema.");
        }

        Set<ThirdPartyRoleCatalog> roles = resolveRoles(request.getRoleIds());
        ThirdPartyStatusCatalog status = resolveStatus(request.getStatusId());
        Municipality municipality = resolveMunicipality(request.getMunicipalityId());
        TypeOrganization typeOrganization = resolveTypeOrganization(request.getTypeOrganizationId());
        TypeRegimen typeRegimen = resolveTypeRegimen(request.getTypeRegimenId());
        List<Withholding> withholdings = resolveWithholdings(request.getWithholdingIds());
        validateBlockingReason(status, request.getBlockingReason());

        ThirdParty thirdParty = ThirdParty.builder()
                .thirdPartyCode(generateThirdPartyCode())
                .nit(request.getNit().trim())
                .dv(request.getDv().trim())
                .businessName(request.getBusinessName().trim())
                .roles(roles)
                .status(status)
                .blockingReason(resolveBlockingReasonForPersist(status, request.getBlockingReason()))
                .municipality(municipality)
                .typeOrganization(typeOrganization)
                .typeRegimen(typeRegimen)
                .creditLimit(request.getCreditLimit())
                .paymentTerms(emptyToNull(request.getPaymentTerms()))
                .marketSegment(emptyToNull(request.getMarketSegment()))
                .build();

        thirdParty.setContacts(toContactEntities(request.getContacts(), thirdParty));
        thirdParty.setWithholdingAssignments(toWithholdingAssignments(withholdings, thirdParty));

        thirdPartyRepository.save(thirdParty);

        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(
                        Optional.of("Tercero registrado exitosamente."),
                        Optional.of(toDto(thirdParty))));
    }

    @Transactional
    public ResponseEntity<?> bulkStore(BulkThirdPartyUploadRequest request, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondJson(bindingResult));
        }
        if (request == null || request.getFileBase64() == null || request.getFileBase64().isBlank()) {
            throw new IllegalArgumentException(
                    "BULK_001: Formato de archivo invalido: columnas obligatorias faltantes.");
        }

        byte[] fileBytes = decodeBase64Payload(request.getFileBase64());
        String extension = resolveExtension(request.getFileName(), request.getFileBase64());
        char delimiter = resolveDelimiter(request.getDelimiter());
        boolean overwrite = isOverwriteEnabled(request.getOverwrite());

        List<BulkThirdPartyRow> rows = switch (extension) {
            case "csv" -> parseCsvRows(fileBytes, delimiter);
            case "xlsx" -> parseXlsxRows(fileBytes);
            default -> throw new IllegalArgumentException(
                    "BULK_001: Formato de archivo invalido: columnas obligatorias faltantes.");
        };

        if (rows.isEmpty()) {
            throw new IllegalArgumentException(
                    "BULK_001: Formato de archivo invalido: columnas obligatorias faltantes.");
        }
        if (rows.size() > MAX_BULK_ROWS) {
            throw new IllegalArgumentException("BULK_003: Archivo excede limite maximo (10,000 registros).");
        }

        ensureDefaultStatusCatalog();
        Map<String, ThirdPartyStatusCatalog> statusesByName = loadStatusesByName();
        Map<String, ThirdPartyRoleCatalog> rolesByName = loadRolesByName();

        Set<String> seenNitsInFile = new HashSet<>();
        List<ThirdParty> toCreate = new ArrayList<>();
        List<ThirdParty> toUpdate = new ArrayList<>();
        long codeSequence = thirdPartyRepository.count() + 1;

        for (BulkThirdPartyRow row : rows) {
            validateBulkRow(row);
            String normalizedNit = row.nit().trim();
            if (!seenNitsInFile.add(normalizedNit)) {
                throw new IllegalArgumentException(
                        "BULK_002: Linea " + row.line() + ": NIT duplicado en archivo/sistema.");
            }

            List<ThirdParty> existingByNit = thirdPartyRepository.findByNitAndDeletedAtIsNull(normalizedNit);
            if (!existingByNit.isEmpty() && !overwrite) {
                throw new IllegalArgumentException(
                        "BULK_002: Linea " + row.line() + ": NIT duplicado en archivo/sistema.");
            }
            if (existingByNit.size() > 1) {
                throw new IllegalArgumentException(
                        "BULK_004: Error en linea " + row.line() + ": existen multiples terceros con el mismo NIT.");
            }

            ThirdPartyStatusCatalog status = resolveStatusByName(row.status(), statusesByName, row.line());
            validateAllowedBulkStatus(status, row.line());
            Set<ThirdPartyRoleCatalog> roles = resolveRolesByNames(row.thirdPartyType(), rolesByName, row.line());
            Municipality municipality = resolveMunicipalityForBulk(row.municipality(), row.line());

            if (existingByNit.isEmpty()) {
                ThirdParty entity = ThirdParty.builder()
                        .thirdPartyCode(String.format("TER%d%06d", LocalDate.now().getYear(), codeSequence++))
                        .nit(normalizedNit)
                        .dv(resolveBulkDv(row.dv(), row.line()))
                        .businessName(row.businessName().trim())
                        .roles(roles)
                        .status(status)
                        .municipality(municipality)
                        .build();
                toCreate.add(entity);
            } else {
                ThirdParty entity = existingByNit.get(0);
                entity.setBusinessName(row.businessName().trim());
                entity.setMunicipality(municipality);
                entity.setStatus(status);
                entity.setRoles(roles);
                toUpdate.add(entity);
            }
        }

        if (!toCreate.isEmpty()) {
            thirdPartyRepository.saveAll(toCreate);
        }
        if (!toUpdate.isEmpty()) {
            thirdPartyRepository.saveAll(toUpdate);
        }

        BulkThirdPartyUploadResponse response = BulkThirdPartyUploadResponse.builder()
                .totalProcessed(rows.size())
                .created(toCreate.size())
                .updated(toUpdate.size())
                .build();

        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(
                        Optional.of("Carga masiva procesada exitosamente."),
                        Optional.of(response)));
    }

    public ResponseEntity<?> findAllPaged(DataTableRequest request) {
        DataTableRequest safeRequest = normalizeDataTableRequest(request);

        int start = Math.max(0, safeRequest.getStart());
        int length = safeRequest.getLength();
        int safeLength = length <= 0 ? 20 : length > 100 ? 100 : length;
        int page = start / safeLength;

        Pageable pageable = length == -1
                ? Pageable.unpaged()
                : PageRequest.of(page, safeLength);

        Specification<ThirdParty> spec = dataTableSpecificationBuilder.build(safeRequest)
                .and((root, query, cb) -> cb.isNull(root.get("deletedAt")));

        Page<ThirdParty> thirdParties = thirdPartyRepository.findAll(spec, pageable);

        if (thirdParties.isEmpty()) {
            throw new IllegalArgumentException(
                    "TERC_001: No se encontraron terceros con los criterios de busqueda especificados.");
        }

        return ResponseEntity.ok(DataTableResponse.from(thirdParties.map(this::toDto), safeRequest.getDraw()));
    }

    public ResponseEntity<?> getDetail(Long id) {
        ThirdParty thirdParty = getThirdPartyOrThrow(id);

        ThirdPartyDetailDTO detail = ThirdPartyDetailDTO.builder()
                .general(ThirdPartyDetailDTO.GeneralTab.builder()
                        .id(thirdParty.getId())
                        .thirdPartyCode(thirdParty.getThirdPartyCode())
                        .nit(thirdParty.getNit())
                        .dv(thirdParty.getDv())
                        .businessName(thirdParty.getBusinessName())
                        .roles(toRoleCatalogDtoList(thirdParty.getRoles()))
                        .status(toStatusCatalogDto(thirdParty.getStatus()))
                        .blockingReason(thirdParty.getBlockingReason())
                        .municipality(toMunicipalityDto(thirdParty.getMunicipality()))
                        .typeOrganization(toTypeOrganizationDto(thirdParty.getTypeOrganization()))
                        .contacts(toContactDtoList(thirdParty.getContacts()))
                        .createdAt(thirdParty.getCreatedAt())
                        .updatedAt(thirdParty.getUpdatedAt())
                        .build())
                .fiscal(ThirdPartyDetailDTO.FiscalTab.builder()
                        .typeRegimen(toTypeRegimenDto(thirdParty.getTypeRegimen()))
                        .withholdings(toWithholdingDtoList(thirdParty.getWithholdingAssignments()))
                        .build())
                .commercial(ThirdPartyDetailDTO.CommercialTab.builder()
                        .creditLimit(thirdParty.getCreditLimit())
                        .paymentTerms(thirdParty.getPaymentTerms())
                        .marketSegment(thirdParty.getMarketSegment())
                        .build())
                .build();

        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(
                        Optional.of("Informacion detallada del tercero obtenida correctamente."),
                        Optional.of(detail)));
    }

    public ResponseEntity<?> update(Long id, ThirdPartyDTO request, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondJson(bindingResult));
        }

        ThirdParty thirdParty = getThirdPartyOrThrow(id);

        String targetNit = request.getNit() != null ? request.getNit().trim() : thirdParty.getNit();
        String targetDv = request.getDv() != null ? request.getDv().trim() : thirdParty.getDv();
        validateNitAndDvFormat(targetNit, targetDv);
        if (thirdPartyRepository.existsByNitAndDvAndIdNotAndDeletedAtIsNull(targetNit, targetDv, id)) {
            throw new IllegalArgumentException("TERC_011: El NIT + DV ya existe en el sistema.");
        }

        if (request.getBusinessName() != null) {
            validateBusinessName(request.getBusinessName());
            thirdParty.setBusinessName(request.getBusinessName().trim());
        }
        if (request.getRoleIds() != null && !request.getRoleIds().isEmpty()) {
            thirdParty.setRoles(resolveRoles(request.getRoleIds()));
        }
        if (request.getMunicipalityId() != null) {
            thirdParty.setMunicipality(resolveMunicipality(request.getMunicipalityId()));
        }
        if (request.getTypeOrganizationId() != null) {
            TypeOrganization typeOrganization = resolveTypeOrganization(request.getTypeOrganizationId());
            thirdParty.setTypeOrganization(typeOrganization);
        }
        if (request.getTypeRegimenId() != null) {
            TypeRegimen typeRegimen = resolveTypeRegimen(request.getTypeRegimenId());
            thirdParty.setTypeRegimen(typeRegimen);
        }
        if (request.getWithholdingIds() != null) {
            List<Withholding> withholdings = resolveWithholdings(request.getWithholdingIds());
            thirdParty.getWithholdingAssignments().clear();
            thirdParty.getWithholdingAssignments().addAll(toWithholdingAssignments(withholdings, thirdParty));
        }
        if (request.getStatusId() != null) {
            ThirdPartyStatusCatalog targetStatus = resolveStatus(request.getStatusId());
            validateBlockingReason(targetStatus, request.getBlockingReason());
            thirdParty.setStatus(targetStatus);
            thirdParty.setBlockingReason(resolveBlockingReasonForPersist(targetStatus, request.getBlockingReason()));
        } else if (request.getBlockingReason() != null) {
            validateBlockingReason(thirdParty.getStatus(), request.getBlockingReason());
            thirdParty.setBlockingReason(
                    resolveBlockingReasonForPersist(thirdParty.getStatus(), request.getBlockingReason()));
        }

        thirdParty.setNit(targetNit);
        thirdParty.setDv(targetDv);
        if (request.getCreditLimit() != null) {
            thirdParty.setCreditLimit(request.getCreditLimit());
        }
        if (request.getPaymentTerms() != null) {
            thirdParty.setPaymentTerms(emptyToNull(request.getPaymentTerms()));
        }
        if (request.getMarketSegment() != null) {
            thirdParty.setMarketSegment(emptyToNull(request.getMarketSegment()));
        }
        if (request.getContacts() != null) {
            thirdParty.getContacts().clear();
            thirdParty.getContacts().addAll(toContactEntities(request.getContacts(), thirdParty));
        }

        thirdPartyRepository.save(thirdParty);
        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(
                        Optional.of("Tercero actualizado exitosamente."),
                        Optional.of(toDto(thirdParty))));
    }

    public ResponseEntity<?> updateRolesAndStatus(Long id, UpdateThirdPartyRolesStatusRequest request,
            BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondJson(bindingResult));
        }

        ThirdParty thirdParty = getThirdPartyOrThrow(id);
        thirdParty.setRoles(resolveRoles(request.getRoleIds()));
        ThirdPartyStatusCatalog targetStatus = resolveStatus(request.getStatusId());
        validateBlockingReason(targetStatus, request.getBlockingReason());
        thirdParty.setStatus(targetStatus);
        thirdParty.setBlockingReason(resolveBlockingReasonForPersist(targetStatus, request.getBlockingReason()));
        thirdPartyRepository.save(thirdParty);

        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(
                        Optional.of("Roles y estado del tercero actualizados correctamente."),
                        Optional.of(toDto(thirdParty))));
    }

    public ResponseEntity<?> delete(Long id) {
        ThirdParty thirdParty = getThirdPartyOrThrow(id);
        thirdPartyRepository.delete(thirdParty);
        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(
                        Optional.of("Tercero eliminado exitosamente."),
                        Optional.empty()));
    }

    private byte[] decodeBase64Payload(String fileBase64) {
        String payload = fileBase64.trim();
        int comma = payload.indexOf(',');
        if (comma >= 0) {
            payload = payload.substring(comma + 1);
        }
        try {
            return Base64.getDecoder().decode(payload);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "BULK_001: Formato de archivo invalido: columnas obligatorias faltantes.");
        }
    }

    private String resolveExtension(String fileName, String fileBase64) {
        if (fileName != null && fileName.contains(".")) {
            String extension = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
            if ("csv".equals(extension) || "xlsx".equals(extension)) {
                return extension;
            }
        }

        String payload = fileBase64 == null ? "" : fileBase64.toLowerCase(Locale.ROOT);
        if (payload.startsWith("data:text/csv")) {
            return "csv";
        }
        if (payload.startsWith("data:application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) {
            return "xlsx";
        }
        return "";
    }

    private char resolveDelimiter(String delimiter) {
        if (delimiter == null || delimiter.isBlank()) {
            return ',';
        }
        return delimiter.charAt(0);
    }

    private boolean isOverwriteEnabled(String overwrite) {
        if (overwrite == null || overwrite.isBlank()) {
            return false;
        }
        String normalized = normalizeToken(overwrite);
        return Set.of("S", "SI", "TRUE", "Y", "YES").contains(normalized);
    }

    private List<BulkThirdPartyRow> parseCsvRows(byte[] fileBytes, char delimiter) {
        String content = new String(fileBytes, StandardCharsets.UTF_8);
        String[] lines = content.split("\\r?\\n");
        if (lines.length == 0) {
            return List.of();
        }

        String headerLine = removeBom(lines[0]);
        char effectiveDelimiter = detectDelimiter(headerLine, delimiter);
        List<String> headers = parseCsvLine(headerLine, effectiveDelimiter);
        Map<String, Integer> canonicalHeaderIndexes = mapCanonicalHeaderIndexes(headers);
        validateRequiredHeaders(canonicalHeaderIndexes.keySet());

        List<BulkThirdPartyRow> rows = new ArrayList<>();
        for (int i = 1; i < lines.length; i++) {
            if (lines[i] == null || lines[i].trim().isEmpty()) {
                continue;
            }
            List<String> values = parseCsvLine(lines[i], effectiveDelimiter);
            rows.add(new BulkThirdPartyRow(
                    i + 1,
                    getRowValue(values, canonicalHeaderIndexes, "nit"),
                    getRowValue(values, canonicalHeaderIndexes, "business_name"),
                    getRowValue(values, canonicalHeaderIndexes, "municipality"),
                    getRowValue(values, canonicalHeaderIndexes, "status"),
                    getRowValue(values, canonicalHeaderIndexes, "third_party_type"),
                    getRowValue(values, canonicalHeaderIndexes, "dv")));
        }
        return rows;
    }

    private String removeBom(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        if (value.charAt(0) == '\uFEFF') {
            return value.substring(1);
        }
        return value;
    }

    private char detectDelimiter(String headerLine, char configuredDelimiter) {
        if (headerLine == null || headerLine.isBlank()) {
            return configuredDelimiter;
        }

        int commas = countChar(headerLine, ',');
        int semicolons = countChar(headerLine, ';');
        int pipes = countChar(headerLine, '|');
        int tabs = countChar(headerLine, '\t');

        if (configuredDelimiter == ',' && semicolons > commas)
            return ';';
        if (configuredDelimiter == ',' && pipes > commas)
            return '|';
        if (configuredDelimiter == ',' && tabs > commas)
            return '\t';
        if (configuredDelimiter == ';' && commas > semicolons)
            return ',';

        return configuredDelimiter;
    }

    private int countChar(String text, char needle) {
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == needle) {
                count++;
            }
        }
        return count;
    }

    private List<BulkThirdPartyRow> parseXlsxRows(byte[] fileBytes) {
        try {
            Map<String, byte[]> entries = extractZipEntries(fileBytes);
            List<String> sharedStrings = readSharedStrings(entries.get("xl/sharedStrings.xml"));
            List<Map<Integer, String>> sheetRows = readXlsxRows(entries.get("xl/worksheets/sheet1.xml"), sharedStrings);
            if (sheetRows.isEmpty()) {
                return List.of();
            }

            Map<Integer, String> headersByIndex = sheetRows.get(0);
            Map<String, Integer> canonicalHeaderIndexes = mapCanonicalHeaderIndexesByColumn(headersByIndex);
            validateRequiredHeaders(canonicalHeaderIndexes.keySet());

            List<BulkThirdPartyRow> rows = new ArrayList<>();
            for (int i = 1; i < sheetRows.size(); i++) {
                Map<Integer, String> rowMap = sheetRows.get(i);
                if (rowMap.values().stream().allMatch(v -> v == null || v.trim().isEmpty())) {
                    continue;
                }
                rows.add(new BulkThirdPartyRow(
                        i + 1,
                        getRowValue(rowMap, canonicalHeaderIndexes, "nit"),
                        getRowValue(rowMap, canonicalHeaderIndexes, "business_name"),
                        getRowValue(rowMap, canonicalHeaderIndexes, "municipality"),
                        getRowValue(rowMap, canonicalHeaderIndexes, "status"),
                        getRowValue(rowMap, canonicalHeaderIndexes, "third_party_type"),
                        getRowValue(rowMap, canonicalHeaderIndexes, "dv")));
            }
            return rows;
        } catch (Exception ex) {
            throw new IllegalArgumentException(
                    "BULK_001: Formato de archivo invalido: columnas obligatorias faltantes.");
        }
    }

    private Map<String, Integer> mapCanonicalHeaderIndexes(List<String> headers) {
        Map<String, Integer> canonicalToIndex = new HashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            String header = headers.get(i);
            String canonical = resolveCanonicalHeader(header);
            if (canonical != null) {
                canonicalToIndex.put(canonical, i);
            }
        }
        return canonicalToIndex;
    }

    private Map<String, Integer> mapCanonicalHeaderIndexesByColumn(Map<Integer, String> headersByColumn) {
        Map<String, Integer> canonicalToIndex = new HashMap<>();
        for (Map.Entry<Integer, String> entry : headersByColumn.entrySet()) {
            String canonical = resolveCanonicalHeader(entry.getValue());
            if (canonical != null) {
                canonicalToIndex.put(canonical, entry.getKey());
            }
        }
        return canonicalToIndex;
    }

    private String resolveCanonicalHeader(String header) {
        if (header == null) {
            return null;
        }
        String normalized = normalizeHeader(header);
        if ("nit".equals(normalized))
            return "nit";
        if (Set.of("nombre_razon_social", "razon_social", "nombre").contains(normalized))
            return "business_name";
        if (Set.of("municipio", "municipality", "municipio_codigo", "municipality_code").contains(normalized))
            return "municipality";
        if ("direccion".equals(normalized))
            return "address";
        if ("email".equals(normalized) || "correo".equals(normalized))
            return "email";
        if (Set.of("estado", "estado_tercero").contains(normalized))
            return "status";
        if (Set.of("tipo_tercero", "roles", "rol").contains(normalized))
            return "third_party_type";
        if (Set.of("dv", "digito_verificacion", "digito_de_verificacion").contains(normalized))
            return "dv";
        return null;
    }

    private void validateRequiredHeaders(Set<String> foundHeaders) {
        List<String> required = List.of("nit", "business_name", "municipality", "status",
                "third_party_type");
        for (String key : required) {
            if (!foundHeaders.contains(key)) {
                throw new IllegalArgumentException(
                        "BULK_001: Formato de archivo invalido: columnas obligatorias faltantes.");
            }
        }
    }

    private String getRowValue(List<String> values, Map<String, Integer> headerIndexes, String key) {
        Integer index = headerIndexes.get(key);
        if (index == null || index < 0 || index >= values.size()) {
            return null;
        }
        return values.get(index);
    }

    private String getRowValue(Map<Integer, String> values, Map<String, Integer> headerIndexes, String key) {
        Integer index = headerIndexes.get(key);
        return index == null ? null : values.get(index);
    }

    private List<String> parseCsvLine(String line, char delimiter) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (ch == delimiter && !inQuotes) {
                values.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        values.add(current.toString().trim());
        return values;
    }

    private Map<String, byte[]> extractZipEntries(byte[] fileBytes) throws IOException {
        Map<String, byte[]> entries = new HashMap<>();
        try (ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(fileBytes))) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                entries.put(entry.getName(), zipInputStream.readAllBytes());
            }
        }
        return entries;
    }

    private List<String> readSharedStrings(byte[] sharedStringsBytes) throws Exception {
        if (sharedStringsBytes == null || sharedStringsBytes.length == 0) {
            return List.of();
        }

        Document document = parseXml(sharedStringsBytes);
        NodeList textNodes = document.getElementsByTagNameNS("*", "t");
        List<String> sharedStrings = new ArrayList<>();
        for (int i = 0; i < textNodes.getLength(); i++) {
            sharedStrings.add(textNodes.item(i).getTextContent());
        }
        return sharedStrings;
    }

    private List<Map<Integer, String>> readXlsxRows(byte[] sheetBytes, List<String> sharedStrings) throws Exception {
        if (sheetBytes == null || sheetBytes.length == 0) {
            return List.of();
        }

        Document document = parseXml(sheetBytes);
        NodeList rowNodes = document.getElementsByTagNameNS("*", "row");
        List<Map<Integer, String>> rows = new ArrayList<>();

        for (int i = 0; i < rowNodes.getLength(); i++) {
            Node rowNode = rowNodes.item(i);
            NodeList cellNodes = rowNode.getChildNodes();
            Map<Integer, String> rowValues = new HashMap<>();
            for (int j = 0; j < cellNodes.getLength(); j++) {
                Node cellNode = cellNodes.item(j);
                if (!"c".equals(cellNode.getLocalName())) {
                    continue;
                }
                Node refNode = cellNode.getAttributes() != null ? cellNode.getAttributes().getNamedItem("r") : null;
                int columnIndex = refNode == null ? -1
                        : columnNameToIndex(refNode.getTextContent().replaceAll("\\d", ""));
                if (columnIndex < 0) {
                    continue;
                }

                String cellType = "";
                Node typeNode = cellNode.getAttributes() != null ? cellNode.getAttributes().getNamedItem("t") : null;
                if (typeNode != null) {
                    cellType = typeNode.getTextContent();
                }

                String value = "";
                NodeList cellChildren = cellNode.getChildNodes();
                for (int k = 0; k < cellChildren.getLength(); k++) {
                    Node child = cellChildren.item(k);
                    if ("v".equals(child.getLocalName())) {
                        value = child.getTextContent();
                        break;
                    }
                }

                if ("s".equals(cellType) && !value.isBlank()) {
                    int sharedIndex = Integer.parseInt(value);
                    if (sharedIndex >= 0 && sharedIndex < sharedStrings.size()) {
                        value = sharedStrings.get(sharedIndex);
                    }
                }
                rowValues.put(columnIndex, value == null ? null : value.trim());
            }
            rows.add(rowValues);
        }

        return rows;
    }

    private Document parseXml(byte[] xmlBytes) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(xmlBytes));
    }

    private int columnNameToIndex(String columnName) {
        if (columnName == null || columnName.isBlank()) {
            return -1;
        }
        int result = 0;
        for (int i = 0; i < columnName.length(); i++) {
            char ch = Character.toUpperCase(columnName.charAt(i));
            if (ch < 'A' || ch > 'Z') {
                return -1;
            }
            result = result * 26 + (ch - 'A' + 1);
        }
        return result - 1;
    }

    private void validateBulkRow(BulkThirdPartyRow row) {
        if (row.nit() == null || !row.nit().trim().matches("^\\d{10,15}$")) {
            throw new IllegalArgumentException("BULK_004: Error en linea " + row.line() + ": NIT invalido.");
        }
        if (row.businessName() == null || row.businessName().trim().length() < 3
                || row.businessName().trim().length() > 255) {
            throw new IllegalArgumentException("BULK_004: Error en linea " + row.line() + ": razon social invalida.");
        }
        if (row.municipality() == null || row.municipality().trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "BULK_004: Error en linea " + row.line() + ": municipio es obligatorio.");
        }
        if (row.status() == null || row.status().trim().isEmpty()) {
            throw new IllegalArgumentException("BULK_004: Error en linea " + row.line() + ": estado es obligatorio.");
        }
        if (row.thirdPartyType() == null || row.thirdPartyType().trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "BULK_004: Error en linea " + row.line() + ": tipo_tercero es obligatorio.");
        }
    }

    private String resolveBulkDv(String dv, int line) {
        if (dv == null || dv.trim().isEmpty()) {
            return "0";
        }
        String cleanDv = dv.trim();
        if (!cleanDv.matches("^\\d{1,2}$")) {
            throw new IllegalArgumentException("BULK_004: Error en linea " + line + ": DV invalido.");
        }
        return cleanDv;
    }

    private ThirdPartyStatusCatalog resolveStatusByName(
            String statusName,
            Map<String, ThirdPartyStatusCatalog> statusesByName,
            int line) {
        ThirdPartyStatusCatalog status = null;
        for (String candidate : getStatusCandidates(statusName)) {
            status = statusesByName.get(candidate);
            if (status != null) {
                break;
            }
        }
        if (status == null) {
            status = findStatusBySemanticMatch(statusName, statusesByName);
        }
        if (status == null) {
            throw new IllegalArgumentException(
                    "BULK_004: Error en linea " + line + ": estado no valido (" + safe(statusName) + ").");
        }
        return status;
    }

    private List<String> getStatusCandidates(String statusName) {
        String normalized = compactToken(statusName);
        if (normalized.isBlank()) {
            return List.of("");
        }
        if (Set.of("ACTIVO", "ACTIVE").contains(normalized)) {
            return List.of("ACTIVO", "ACTIVE");
        }
        if (Set.of("INACTIVO", "INACTIVE").contains(normalized)) {
            return List.of("INACTIVO", "INACTIVE");
        }
        if (Set.of("BLOQUEADO", "BLOCKED").contains(normalized)) {
            return List.of("BLOQUEADO", "BLOCKED");
        }
        return List.of(normalized);
    }

    private ThirdPartyStatusCatalog findStatusBySemanticMatch(
            String statusName,
            Map<String, ThirdPartyStatusCatalog> statusesByName) {
        String normalized = compactToken(statusName);
        if (normalized.contains("BLOQ") || normalized.contains("BLOCK")) {
            for (Map.Entry<String, ThirdPartyStatusCatalog> entry : statusesByName.entrySet()) {
                String key = entry.getKey();
                if (key.contains("BLOQ") || key.contains("BLOCK")) {
                    return entry.getValue();
                }
            }
        }
        if (normalized.contains("ACTIV") || normalized.equals("ACTIVE")) {
            for (Map.Entry<String, ThirdPartyStatusCatalog> entry : statusesByName.entrySet()) {
                String key = entry.getKey();
                if (key.contains("ACTIV")) {
                    return entry.getValue();
                }
            }
        }
        if (normalized.contains("INACT") || normalized.equals("INACTIVE")) {
            for (Map.Entry<String, ThirdPartyStatusCatalog> entry : statusesByName.entrySet()) {
                String key = entry.getKey();
                if (key.contains("INACT")) {
                    return entry.getValue();
                }
            }
        }
        return null;
    }

    private Set<ThirdPartyRoleCatalog> resolveRolesByNames(
            String roleNames,
            Map<String, ThirdPartyRoleCatalog> rolesByName,
            int line) {
        String[] rawRoles = roleNames.split("[,;|]");
        Set<ThirdPartyRoleCatalog> roles = new LinkedHashSet<>();
        for (String rawRole : rawRoles) {
            String normalized = normalizeToken(rawRole);
            if (normalized.isBlank()) {
                continue;
            }
            ThirdPartyRoleCatalog role = rolesByName.get(normalized);
            if (role == null) {
                throw new IllegalArgumentException("BULK_004: Error en linea " + line + ": rol no valido.");
            }
            roles.add(role);
        }
        if (roles.isEmpty()) {
            throw new IllegalArgumentException("BULK_004: Error en linea " + line + ": tipo_tercero no valido.");
        }
        return roles;
    }

    private Map<String, ThirdPartyStatusCatalog> loadStatusesByName() {
        return statusCatalogRepository.findAll().stream()
                .collect(Collectors.toMap(status -> compactToken(status.getName()), status -> status, (a, b) -> a));
    }

    private void ensureDefaultStatusCatalog() {
        ensureStatusExists("ACTIVO");
        ensureStatusExists("BLOQUEADO");
        ensureStatusExists("INACTIVO");
    }

    private void ensureStatusExists(String statusName) {
        statusCatalogRepository.findByNameIgnoreCase(statusName)
                .orElseGet(() -> statusCatalogRepository.save(
                        ThirdPartyStatusCatalog.builder().name(statusName).build()));
    }

    private Map<String, ThirdPartyRoleCatalog> loadRolesByName() {
        return roleCatalogRepository.findAll().stream()
                .collect(Collectors.toMap(role -> normalizeToken(role.getName()), role -> role));
    }

    private String normalizeHeader(String value) {
        return normalizeToken(value)
                .replace(" ", "_")
                .replace("/", "_")
                .replace("-", "_")
                .toLowerCase(Locale.ROOT);
    }

    private String normalizeToken(String value) {
        if (value == null) {
            return "";
        }
        String normalized = Normalizer.normalize(value.trim(), Normalizer.Form.NFD);
        normalized = normalized.replaceAll("\\p{M}", "");
        return normalized.toUpperCase(Locale.ROOT);
    }

    private String compactToken(String value) {
        return normalizeToken(value).replaceAll("[^A-Z0-9]", "");
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private void validateRequiredFields(ThirdPartyDTO request) {
        validateBusinessName(request.getBusinessName());
        if (request.getTypeOrganizationId() == null) {
            throw new IllegalArgumentException("El tipo de organizacion es obligatorio.");
        }
        if (request.getTypeRegimenId() == null) {
            throw new IllegalArgumentException("El tipo de regimen es obligatorio.");
        }
        if (request.getMunicipalityId() == null) {
            throw new IllegalArgumentException("El municipio es obligatorio.");
        }
        if (request.getRoleIds() == null || request.getRoleIds().isEmpty()) {
            throw new IllegalArgumentException("Debe seleccionar al menos un rol para el tercero.");
        }
        if (request.getStatusId() == null) {
            throw new IllegalArgumentException("El estado es obligatorio.");
        }
    }

    private void validateBusinessName(String businessName) {
        if (businessName == null || businessName.trim().length() < 3 || businessName.trim().length() > 255) {
            throw new IllegalArgumentException("TERC_013: Razon Social es obligatoria (3-255 caracteres).");
        }
    }

    private void validateBlockingReason(ThirdPartyStatusCatalog status, String blockingReason) {
        if (status == null || !"BLOQUEADO".equalsIgnoreCase(status.getName())) {
            return;
        }
        if (blockingReason == null || blockingReason.trim().length() < 20) {
            throw new IllegalArgumentException(
                    "TERC_031: El tercero no puede ser bloqueado sin motivo registrado (min. 20 caracteres).");
        }
    }

    private String resolveBlockingReasonForPersist(ThirdPartyStatusCatalog status, String blockingReason) {
        if (status == null || !"BLOQUEADO".equalsIgnoreCase(status.getName())) {
            return null;
        }
        return emptyToNull(blockingReason);
    }

    private void validateAllowedBulkStatus(ThirdPartyStatusCatalog status, int line) {
        if (status != null && "BLOQUEADO".equalsIgnoreCase(status.getName())) {
            throw new IllegalArgumentException("BULK_004: Error en linea " + line
                    + ": no se permite crear terceros en estado BLOQUEADO en carga masiva.");
        }
    }

    private Set<ThirdPartyRoleCatalog> resolveRoles(List<Long> roleIds) {
        List<Long> cleanRoleIds = roleIds == null ? List.of()
                : roleIds.stream()
                        .filter(id -> id != null && id > 0)
                        .distinct()
                        .toList();
        if (cleanRoleIds.isEmpty()) {
            throw new IllegalArgumentException("Debe seleccionar al menos un rol para el tercero.");
        }

        List<ThirdPartyRoleCatalog> roles = roleCatalogRepository.findAllById(cleanRoleIds);
        if (roles.size() != cleanRoleIds.size()) {
            throw new IllegalArgumentException("Uno o mas roles no existen en el catalogo.");
        }
        return new LinkedHashSet<>(roles);
    }

    private ThirdPartyStatusCatalog resolveStatus(Long statusId) {
        if (statusId == null) {
            throw new IllegalArgumentException("El estado es obligatorio.");
        }
        return statusCatalogRepository.findById(statusId)
                .orElseThrow(() -> new IllegalArgumentException("El estado no existe en el catalogo."));
    }

    private TypeOrganization resolveTypeOrganization(Long typeOrganizationId) {
        if (typeOrganizationId == null) {
            return null;
        }
        return typeOrganizationRepository.findById(typeOrganizationId)
                .orElseThrow(() -> new IllegalArgumentException("El tipo de organizacion no existe en el catalogo."));
    }

    private TypeRegimen resolveTypeRegimen(Long typeRegimenId) {
        if (typeRegimenId == null) {
            return null;
        }
        return typeRegimenRepository.findById(typeRegimenId)
                .orElseThrow(() -> new IllegalArgumentException("El tipo de regimen no existe en el catalogo."));
    }

    private List<Withholding> resolveWithholdings(List<Long> withholdingIds) {
        if (withholdingIds == null || withholdingIds.isEmpty()) {
            return List.of();
        }
        List<Long> cleanIds = withholdingIds.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
        if (cleanIds.isEmpty()) {
            return List.of();
        }
        List<Withholding> withholdings = withholdingRepository.findAllById(cleanIds);
        if (withholdings.size() != cleanIds.size()) {
            throw new IllegalArgumentException("Una o mas retenciones no existen en el catalogo.");
        }
        return withholdings;
    }

    private Municipality resolveMunicipality(Long municipalityId) {
        if (municipalityId == null) {
            throw new IllegalArgumentException("El municipio es obligatorio.");
        }
        return municipalityRepository.findById(municipalityId)
                .orElseThrow(() -> new IllegalArgumentException("El municipio no existe en el catalogo."));
    }

    private Municipality resolveMunicipalityForBulk(String municipalityValue, int line) {
        String cleanValue = municipalityValue == null ? "" : municipalityValue.trim();
        if (cleanValue.isEmpty()) {
            throw new IllegalArgumentException("BULK_004: Error en linea " + line + ": municipio es obligatorio.");
        }

        return municipalityRepository.findByCodeIgnoreCase(cleanValue)
                .or(() -> municipalityRepository.findByNameIgnoreCase(cleanValue))
                .orElseThrow(() -> new IllegalArgumentException(
                        "BULK_004: Error en linea " + line + ": municipio no valido."));
    }

    private ThirdParty getThirdPartyOrThrow(Long id) {
        return thirdPartyRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("TERC_021: El tercero no existe o fue eliminado."));
    }

    private void validateNitAndDvFormat(String nit, String dv) {
        if (nit == null || !nit.matches("^\\d{10,15}$")) {
            throw new IllegalArgumentException("TERC_012: Formato de NIT invalido. Solo numeros, 10-15 caracteres.");
        }
        if (dv == null || !dv.matches("^\\d{1,2}$")) {
            throw new IllegalArgumentException("TERC_012: Formato de DV invalido. Solo numeros, 1-2 caracteres.");
        }
    }

    private String generateThirdPartyCode() {
        long sequence = thirdPartyRepository.count() + 1;
        int year = LocalDate.now().getYear();
        return String.format("TER%d%06d", year, sequence);
    }

    private ThirdPartyDTO toDto(ThirdParty entity) {
        List<Long> roleIds = entity.getRoles() == null ? List.of()
                : entity.getRoles().stream().map(ThirdPartyRoleCatalog::getId).toList();

        return ThirdPartyDTO.builder()
                .id(entity.getId())
                .thirdPartyCode(entity.getThirdPartyCode())
                .nit(entity.getNit())
                .dv(entity.getDv())
                .businessName(entity.getBusinessName())
                .roles(toRoleCatalogDtoList(entity.getRoles()))
                .roleIds(roleIds)
                .status(toStatusCatalogDto(entity.getStatus()))
                .statusId(entity.getStatus() != null ? entity.getStatus().getId() : null)
                .blockingReason(entity.getBlockingReason())
                .municipality(toMunicipalityDto(entity.getMunicipality()))
                .municipalityId(entity.getMunicipality() != null ? entity.getMunicipality().getId() : null)
                .typeOrganization(toTypeOrganizationDto(entity.getTypeOrganization()))
                .typeOrganizationId(entity.getTypeOrganization() != null ? entity.getTypeOrganization().getId() : null)
                .typeRegimen(toTypeRegimenDto(entity.getTypeRegimen()))
                .typeRegimenId(entity.getTypeRegimen() != null ? entity.getTypeRegimen().getId() : null)
                .withholdings(toWithholdingDtoList(entity.getWithholdingAssignments()))
                .withholdingIds(toWithholdingIdList(entity.getWithholdingAssignments()))
                .creditLimit(entity.getCreditLimit())
                .paymentTerms(entity.getPaymentTerms())
                .marketSegment(entity.getMarketSegment())
                .contacts(toContactDtoList(entity.getContacts()))
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private List<ThirdPartyRoleCatalogDTO> toRoleCatalogDtoList(Set<ThirdPartyRoleCatalog> roles) {
        if (roles == null || roles.isEmpty()) {
            return List.of();
        }
        return roles.stream()
                .map(role -> ThirdPartyRoleCatalogDTO.builder()
                        .id(role.getId())
                        .name(role.getName())
                        .build())
                .toList();
    }

    private ThirdPartyStatusCatalogDTO toStatusCatalogDto(ThirdPartyStatusCatalog status) {
        if (status == null) {
            return null;
        }
        return ThirdPartyStatusCatalogDTO.builder()
                .id(status.getId())
                .name(status.getName())
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

    private List<Long> toWithholdingIdList(List<ThirdPartyWithholdingAssignment> assignments) {
        if (assignments == null || assignments.isEmpty()) {
            return List.of();
        }
        return assignments.stream()
                .map(ThirdPartyWithholdingAssignment::getWithholding)
                .filter(w -> w != null && w.getId() != null)
                .map(Withholding::getId)
                .distinct()
                .toList();
    }

    private List<WithholdingDTO> toWithholdingDtoList(List<ThirdPartyWithholdingAssignment> assignments) {
        if (assignments == null || assignments.isEmpty()) {
            return List.of();
        }
        return assignments.stream()
                .map(ThirdPartyWithholdingAssignment::getWithholding)
                .filter(w -> w != null && w.getId() != null)
                .collect(Collectors.toMap(Withholding::getId, w -> w, (a, b) -> a, java.util.LinkedHashMap::new))
                .values().stream()
                .map(this::toWithholdingDto)
                .toList();
    }

    private List<ThirdPartyWithholdingAssignment> toWithholdingAssignments(
            List<Withholding> withholdings,
            ThirdParty thirdParty) {
        if (withholdings == null || withholdings.isEmpty()) {
            return new ArrayList<>();
        }
        return withholdings.stream()
                .map(withholding -> ThirdPartyWithholdingAssignment.builder()
                        .thirdParty(thirdParty)
                        .withholding(withholding)
                        .build())
                .toList();
    }

    private List<ThirdContactDTO> toContactDtoList(List<ThirdContact> contacts) {
        if (contacts == null || contacts.isEmpty()) {
            return List.of();
        }
        return contacts.stream()
                .map(contact -> ThirdContactDTO.builder()
                        .id(contact.getId())
                        .position(contact.getPosition())
                        .phone(contact.getPhone())
                        .email(contact.getEmail())
                        .contactPerson(contact.getContactPerson())
                        .createdAt(contact.getCreatedAt())
                        .updatedAt(contact.getUpdatedAt())
                        .deletedAt(contact.getDeletedAt())
                        .build())
                .toList();
    }

    private List<ThirdContact> toContactEntities(List<ThirdContactDTO> contactDTOs, ThirdParty thirdParty) {
        if (contactDTOs == null || contactDTOs.isEmpty()) {
            return new ArrayList<>();
        }
        return contactDTOs.stream()
                .map(dto -> ThirdContact.builder()
                        .id(dto.getId())
                        .thirdParty(thirdParty)
                        .position(emptyToNull(dto.getPosition()))
                        .phone(emptyToNull(dto.getPhone()))
                        .email(emptyToNull(dto.getEmail()))
                        .contactPerson(emptyToNull(dto.getContactPerson()))
                        .build())
                .toList();
    }

    private String emptyToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
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

        return safe;
    }

    private record BulkThirdPartyRow(
            int line,
            String nit,
            String businessName,
            String municipality,
            String status,
            String thirdPartyType,
            String dv) {
    }
}

