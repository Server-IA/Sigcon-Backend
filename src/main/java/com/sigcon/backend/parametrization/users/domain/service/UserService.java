package com.sigcon.backend.parametrization.users.domain.service;

import com.sigcon.backend.general.storage.AvatarStorageService;
import com.sigcon.backend.parametrization.companies.application.CompanyDTO;
import com.sigcon.backend.parametrization.companies.domain.model.Company;
import com.sigcon.backend.parametrization.companies.domain.model.CompanyWithholdingAssignment;
import com.sigcon.backend.parametrization.companies.domain.repository.CompanyRepository;
import com.sigcon.backend.parametrization.companies.domain.repository.CompanyWithholdingAssignmentRepository;
import com.sigcon.backend.parametrization.parameters.application.ParameterDTO;
import com.sigcon.backend.parametrization.parameters.application.UserParameterDTO;
import com.sigcon.backend.parametrization.parameters.domain.repository.ParameterRepository;
import com.sigcon.backend.parametrization.parameters.domain.repository.UserParameterRepository;
import com.sigcon.backend.parametrization.resources.application.TypeOrganizationDTO;
import com.sigcon.backend.parametrization.resources.application.TypeRegimenDTO;
import com.sigcon.backend.parametrization.resources.application.WithholdingDTO;
import com.sigcon.backend.parametrization.resources.domain.model.Withholding;
import com.sigcon.backend.parametrization.resources.domain.repository.WithholdingRepository;
import com.sigcon.backend.parametrization.users.application.role.PermissionDTO;
import com.sigcon.backend.parametrization.users.application.user.UserDTO;
import com.sigcon.backend.parametrization.users.domain.model.Role;
import com.sigcon.backend.parametrization.users.domain.model.User;
import com.sigcon.backend.parametrization.users.domain.model.enums.Status;
import com.sigcon.backend.parametrization.users.domain.repository.PermissionRepository;
import com.sigcon.backend.parametrization.users.domain.repository.RoleRepository;
import com.sigcon.backend.parametrization.users.domain.repository.UserRepository;
import com.sigcon.backend.utils.DataTableRequest;
import com.sigcon.backend.utils.DataTableResponse;
import com.sigcon.backend.utils.DataTableSpecificationBuilder;
import com.sigcon.backend.utils.ErrorRespondJson;
import com.sigcon.backend.utils.SuccessRespondJson;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.validation.BindingResult;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PermissionRepository permissionRepository;
    private final ParameterRepository parameterRepository;
    private final UserParameterRepository userParameterRepository;
    private final RoleRepository roleRepository;
    private final CompanyRepository companyRepository;
    private final WithholdingRepository withholdingRepository;
    private final CompanyWithholdingAssignmentRepository companyWithholdingAssignmentRepository;

    private final AvatarStorageService avatarStorageService;
    private final DataTableSpecificationBuilder<User> userSpecificationBuilder = new DataTableSpecificationBuilder<>();

    public ResponseEntity<?> getUsers(DataTableRequest request) {

        try {
            int start = Math.max(0, request.getStart());
            int length = request.getLength();

            int safeLength = length <= 0 ? 10 : length;
            int page = start / safeLength;

            Pageable pageable = length == -1
                    ? Pageable.unpaged()
                    : PageRequest.of(page, safeLength);

            Specification<User> spec = userSpecificationBuilder.build(request)
                    .and((root, query, cb) -> cb.isNull(root.get("deletedAt")));

            Page<User> users = userRepository.findAll(spec, pageable);

            Page<UserDTO> data = users.map(user -> {
                UserDTO dto = new UserDTO();
                dto.setId(user.getId());
                dto.setName(user.getName());
                dto.setLastname(user.getLastname());
                dto.setEmail(user.getEmail());
                dto.setAvatar(user.getAvatar());
                dto.setStatus(user.getStatus());
                dto.setUsername(user.getUsername());
                dto.setRoles(
                        user.getRoles()
                                .stream()
                                .map(Role::getName)
                                .collect(Collectors.toSet()));
                return dto;
            });

            DataTableResponse<UserDTO> response = DataTableResponse.from(data, request.getDraw());
            // response.setRecordsTotal(data.getTotalElements());
            // response.setRecordsFiltered(data.getTotalElements());

            return ResponseEntity.ok(
                    response);

        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        }
    }

    private boolean noFilters(UserDTO dto) {
        return isBlank(dto.getName())
                && isBlank(dto.getLastname())
                && isBlank(dto.getEmail())
                && isBlank(dto.getUsername())
                && isBlank(dto.getRole())
                && dto.getStatus() == null;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public ResponseEntity<?> store(UserDTO request, BindingResult bindingResult) {
        // try{
        if (bindingResult.hasErrors()) {
            return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondJson(bindingResult));
        }

        if (userRepository.existsByEmailAndDeletedAtIsNull(request.getEmail())) {
            return ResponseEntity.badRequest().body(
                    ErrorRespondJson.getErrorRespondMessage(Optional.of("El correo electrónico ya está registrado")));

        }
        if (request.getCompanyId() == null) {
            return ResponseEntity.badRequest()
                    .body(ErrorRespondJson.getErrorRespondMessage(Optional.of("La empresa es obligatoria")));
        }

        Company company = companyRepository.findById(request.getCompanyId())
                .orElseThrow(() -> new RuntimeException("Empresa no encontrada"));

        User user = User.builder()
                .name(request.getName())
                .lastname(request.getLastname())
                .email(request.getEmail())
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .company(company)
                .status(Status.ACTIVE)
                .roles(request.getRoles().stream().map(roleRepository::findByName).filter(Optional::isPresent)
                        .map(Optional::get).collect(Collectors.toSet()))
                .build();
        userRepository.save(user);
        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(Optional.of("Usuario creado correctamente"),
                        Optional.empty()));

        // }catch(Exception e){
        // return
        // ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        // }
    }

    public ResponseEntity<?> getUserInfo() {

        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String email = auth.getName();

            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

            UserDTO response = new UserDTO();
            response.setId(user.getId());
            response.setName(user.getName());
            response.setLastname(user.getLastname());
            response.setEmail(user.getEmail());
            response.setAvatar(user.getAvatar());
            response.setStatus(user.getStatus());

            List<CompanyWithholdingAssignment> companyWithholdingAssignments =
            companyWithholdingAssignmentRepository.findByCompanyAndDeletedAtIsNull(user.getCompany());

            List<WithholdingDTO> companyWithholdingDTOs = companyWithholdingAssignments.stream()
                    .map(companyWithholdingAssignment -> new WithholdingDTO(
                            companyWithholdingAssignment.getWithholding().getId(),
                            companyWithholdingAssignment.getWithholding().getName(),
                            companyWithholdingAssignment.getWithholding().getCode(),
                            null,
                            null,
                            null
                    )).collect(Collectors.toList());

            response.setCompany(CompanyDTO.builder()
                    .id(user.getCompany().getId())
                    .name(user.getCompany().getName())
                    .nit(user.getCompany().getNit())
                    .typeOrganization(TypeOrganizationDTO.builder()
                            .id(user.getCompany().getTypeOrganization().getId())
                            .name(user.getCompany().getTypeOrganization().getName())
                            .code(user.getCompany().getTypeOrganization().getCode())
                            .build())
                    .typeRegimen(TypeRegimenDTO.builder()
                            .id(user.getCompany().getTypeRegimen().getId())
                            .name(user.getCompany().getTypeRegimen().getName())
                            .code(user.getCompany().getTypeRegimen().getCode())
                            .build())
                    .withholdings(companyWithholdingDTOs)
                    .build());

            response.setRoles(
                    user.getRoles()
                            .stream()
                            .map(Role::getName)
                            .collect(Collectors.toSet()));

            response.setPermissions(
                    permissionRepository.findByUserID(user.getId())
                            .stream()
                            .map(permission -> new PermissionDTO(
                                    null,
                                    permission.getName(),
                                    permission.getCode(),
                                    permission.getType(),
                                    null,
                                    null,
                                    permission.getDescription(),
                                    null))
                            .collect(Collectors.toList()));

            List<ParameterDTO> parameters = parameterRepository.findAll()
                    .stream()
                    .map(parameter -> new ParameterDTO(
                            null,
                            parameter.getName(),
                            parameter.getValue(),
                            userParameterRepository.findByUserAndParameter(user, parameter)
                                    .map(userParameter -> new UserParameterDTO(
                                            null,
                                            null,
                                            null,
                                            userParameter.getValue(),
                                            null,
                                            null,
                                            null,
                                            null))
                                    .orElse(null),
                            parameter.getCategory(),
                            parameter.getStatus(),
                            null,
                            null,
                            null))
                    .collect(Collectors.toList());

            response.setParameters(parameters);

            return ResponseEntity.ok(
                    SuccessRespondJson.getSuccessRespondMessage(
                            Optional.of("Información del usuario obtenida correctamente"), Optional.of(response)));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        }

    }

    public ResponseEntity<?> updateInfo(UserDTO request) {

        if (request == null) {
            return ResponseEntity.badRequest().body(
                    ErrorRespondJson.getErrorRespondMessage(Optional.of("Datos inválidos")));
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        if (request.getName() != null) {
            user.setName(request.getName());
        }

        if (request.getLastname() != null) {
            user.setLastname(request.getLastname());
        }

        if (request.getEmail() != null) {
            user.setEmail(request.getEmail());
        }

        if (request.getAvatar() != null && !request.getAvatar().isBlank()) {
            user.setAvatar(resolveAvatarFilename(request.getAvatar(), user.getAvatar()));
        }

        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }

        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        UserDTO userDTO = new UserDTO();
        userDTO.setId(user.getId());
        userDTO.setName(user.getName());
        userDTO.setLastname(user.getLastname());
        userDTO.setEmail(user.getEmail());
        userDTO.setAvatar(user.getAvatar());
        userDTO.setStatus(user.getStatus());
        userDTO.setRoles(user.getRoles().stream().map(Role::getName).collect(Collectors.toSet()));
        userDTO.setPermissions(permissionRepository.findByUserID(user.getId()).stream()
                .map(permission -> new PermissionDTO(null, permission.getName(), permission.getCode(),
                        permission.getType(), null, null, permission.getDescription(), null))
                .collect(Collectors.toList()));

        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(Optional.of("Información actualizada correctamente"),
                        Optional.of(userDTO)));
    }

    public ResponseEntity<?> updateUser(Long id, UserDTO request) {

        // try{

        Optional<User> userOpt = userRepository.findById(id);

        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    ErrorRespondJson.getErrorRespondMessage(Optional.of("Usuario no encontrado")));
        }

        User user = userOpt.get();

        if (request.getName() != null) {
            user.setName(request.getName());
        }

        if (request.getLastname() != null) {
            user.setLastname(request.getLastname());
        }

        if (request.getEmail() != null) {
            user.setEmail(request.getEmail());
        }

        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }

        if (request.getStatus() != null) {
            user.setStatus(request.getStatus());
        }

        if (request.getRoles() != null) {
            user.setRoles(request.getRoles().stream().map(roleRepository::findByName).filter(Optional::isPresent)
                    .map(Optional::get).collect(Collectors.toSet()));
        }

        if (request.getUsername() != null) {
            user.setUsername(request.getUsername());
        }

        if (request.getCompanyId() != null) {
            Company company = companyRepository.findById(request.getCompanyId())
                    .orElseThrow(() -> new RuntimeException("Empresa no encontrada"));

            user.setCompany(company);
        }

        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(
                        Optional.of("Información del usuario actualizada correctamente"), Optional.empty()));
        // }catch(Exception e){
        // return
        // ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        // }
    }

    public ResponseEntity<?> deleteUser(Long id) {

        // try{
        Optional<User> userOpt = userRepository.findById(id);

        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    ErrorRespondJson.getErrorRespondMessage(Optional.of("Usuario no encontrado")));
        } else if (userOpt.get().getDeletedAt() != null) {
            return ResponseEntity.badRequest().body(
                    ErrorRespondJson.getErrorRespondMessage(Optional.of("Usuario ya eliminado")));
        }

        User user = userOpt.get();
        // user.setStatus(Status.INACTIVE);
        // user.setUpdatedAt(LocalDateTime.now());
        user.setDeletedAt(LocalDateTime.now());
        userRepository.save(user);

        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(Optional.of("Usuario eliminado correctamente"),
                        Optional.empty()));

        // }catch(Exception e){
        // return
        // ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        // }
    }

    private String resolveAvatarFilename(String avatarValue, String previousAvatarFilename) {
        if (looksLikeBase64Image(avatarValue)) {
            return avatarStorageService.saveBase64Avatar(avatarValue, previousAvatarFilename);
        }
        return avatarValue;
    }

    private boolean looksLikeBase64Image(String avatarValue) {
        String normalized = avatarValue.trim().toLowerCase();
        return normalized.startsWith("data:image/") || normalized.length() > 255;
    }
}
