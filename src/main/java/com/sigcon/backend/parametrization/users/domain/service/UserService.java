package com.sigcon.backend.parametrization.users.domain.service;

import com.sigcon.backend.general.storage.AvatarStorageService;
import com.sigcon.backend.parametrization.users.application.role.PermissionDTO;
import com.sigcon.backend.parametrization.users.application.user.UserDTO;
import com.sigcon.backend.parametrization.users.domain.model.Role;
import com.sigcon.backend.parametrization.users.domain.model.User;
import com.sigcon.backend.parametrization.users.domain.model.enums.Status;
import com.sigcon.backend.parametrization.users.domain.repository.PermissionRepository;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PermissionRepository permissionRepository;
    
    
    private final AvatarStorageService avatarStorageService;
    private final DataTableSpecificationBuilder<User> userSpecificationBuilder =
            new DataTableSpecificationBuilder<>();

    public ResponseEntity<?> getUsers(DataTableRequest request) {

        try {
            int start  = Math.max(0, request.getStart());
            int length = request.getLength();

            int safeLength = length <= 0 ? 10 : length;
            int page = start / safeLength;

            Pageable pageable = length == -1
                ? Pageable.unpaged()
                : PageRequest.of(page, safeLength);

            Specification<User> spec = userSpecificationBuilder.build(request)
                .and((root, query, cb) -> cb.isNull(root.get("deleted_at")));

            Page<User> users =  userRepository.findAll(spec, pageable);

            Page<UserDTO> data = users.map(user -> {
                UserDTO dto = new UserDTO();
                dto.setId(user.getId());
                dto.setName(user.getName());
                dto.setLastname(user.getLastname());
                dto.setEmail(user.getEmail());
                dto.setAvatar(user.getAvatar());
                dto.setStatus(user.getStatus());
                dto.setRoles(
                        user.getRoles()
                                .stream()
                                .map(Role::getName)
                                .collect(Collectors.toSet())
                );
                return dto;
            });

            DataTableResponse<UserDTO> response = DataTableResponse.from(data, request.getDraw());
            // response.setRecordsTotal(data.getTotalElements());
            // response.setRecordsFiltered(data.getTotalElements());
            
            return ResponseEntity.ok(
                response
            );

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondMessage(Optional.of("Error al obtener los usuarios")));
        }
    }
    private boolean noFilters(UserDTO dto) {
        return isBlank(dto.getName())
                && isBlank(dto.getLastname())
                && isBlank(dto.getEmail())
                && isBlank(dto.getRole())
                && dto.getStatus() == null;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public ResponseEntity<?> getUserInfo() {

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
        response.setRoles(
                user.getRoles()
                        .stream()
                        .map(Role::getName)
                        .collect(Collectors.toSet())
        );

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
                    null
                ))
                .collect(Collectors.toList())
        );

        return ResponseEntity.ok(
            SuccessRespondJson.getSuccessRespondMessage(Optional.of("Información del usuario obtenida correctamente"), Optional.of(response))
        );
    }

    public ResponseEntity<?> updateInfo(UserDTO request) {

        if (request == null) {
            return ResponseEntity.badRequest().body(
                ErrorRespondJson.getErrorRespondMessage(Optional.of("Datos inválidos"))
            );
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

        user.setUpdated_at(LocalDateTime.now());
        userRepository.save(user);

        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(Optional.of("Información actualizada correctamente"), Optional.of(user))
        );
    }
    public ResponseEntity<?> updateUser(Long id, UserDTO request){

        Optional<User> userOpt = userRepository.findById(id);

        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    ErrorRespondJson.getErrorRespondMessage(Optional.of("Usuario no encontrado"))
            );
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

        if (request.getAvatar() != null && !request.getAvatar().isBlank()) {
            user.setAvatar(resolveAvatarFilename(request.getAvatar(), user.getAvatar()));
        }        

        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }

        if (request.getStatus() != null) {
            user.setStatus(request.getStatus());
        }

        user.setUpdated_at(LocalDateTime.now());
        userRepository.save(user);

        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(Optional.of("Información del usuario actualizada correctamente"), Optional.of(user))
        );
    }

    public ResponseEntity<?> deleteUser(Long id){

        Optional<User> userOpt = userRepository.findById(id);

        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    ErrorRespondJson.getErrorRespondMessage(Optional.of("Usuario no encontrado"))
            );
        }

        User user = userOpt.get();
        user.setStatus(Status.INACTIVE);
        user.setUpdated_at(LocalDateTime.now());
        user.setDeleted_at(LocalDateTime.now());
        userRepository.save(user);

        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(Optional.of("Usuario eliminado correctamente"), Optional.of(user))
        );
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
