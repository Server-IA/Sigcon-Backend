package com.sigcon.backend.users.domain.service;

import com.sigcon.backend.users.application.user.UserDTO;
import com.sigcon.backend.users.domain.model.Role;
import com.sigcon.backend.users.domain.model.User;
import com.sigcon.backend.users.domain.model.enums.Status;
import com.sigcon.backend.users.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public ResponseEntity<?> getUsers(UserDTO request, Pageable pageable) {

        try {
            Page<User> users;


            if (request == null || noFilters(request)) {
                users = userRepository.findAll(pageable);
            } else {
                users = userRepository.searchUsers(
                        request.getName(),
                        request.getLastname(),
                        request.getEmail(),
                        request.getRole(),
                        request.getStatus(),
                        pageable
                );
            }

            if (users.isEmpty()) {
                return ResponseEntity.ok("No se encontraron usuarios");
            }


            Page<UserDTO> response = users.map(user -> {
                UserDTO dto = new UserDTO();
                dto.setId(user.getId());
                dto.setName(user.getName());
                dto.setLastname(user.getLastname());
                dto.setEmail(user.getEmail());
                dto.setStatus(user.getStatus());
                dto.setRoles(
                        user.getRoles()
                                .stream()
                                .map(Role::getName)
                                .collect(Collectors.toSet())
                );
                return dto;
            });

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error al cargar usuarios, intente nuevamente");
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
        response.setRole(user.getRoles().toString());
        response.setStatus(user.getStatus());
        response.setRoles(
                user.getRoles()
                        .stream()
                        .map(Role::getName)
                        .collect(Collectors.toSet())
        );

        return ResponseEntity.ok(response);
    }

    public ResponseEntity<?> updateInfo(UserDTO request) {

        if (request == null) {
            return ResponseEntity.badRequest().body("Datos inválidos");
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

        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }
        user.setLastUpdateDate( LocalDateTime.now());
        userRepository.save(user);

        return ResponseEntity.ok("Información actualizada correctamente");
    }
    public ResponseEntity<?> updateUser(Long id, UserDTO request){

        Optional<User> userOpt = userRepository.findById(id);

        if (userOpt.isEmpty()) {
            return ResponseEntity.status(404).body("Usuario no encontrado");
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

        user.setLastUpdateDate( LocalDateTime.now());
        userRepository.save(user);

        return ResponseEntity.ok("Información del usuario actualizada correctamente");

    }

    public ResponseEntity<?> deleteUser(Long id){

        Optional<User> userOpt = userRepository.findById(id);

        if (userOpt.isEmpty()) {
            return ResponseEntity.status(404).body("Usuario no encontrado");
        }

        User user = userOpt.get();
        user.setStatus(Status.INACTIVE);
        user.setLastUpdateDate( LocalDateTime.now());
        userRepository.save(user);
        return ResponseEntity.ok("Usuario eliminado correctamente");

    }







}
