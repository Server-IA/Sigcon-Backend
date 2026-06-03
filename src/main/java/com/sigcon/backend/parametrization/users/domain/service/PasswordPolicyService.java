package com.sigcon.backend.parametrization.users.domain.service;

import com.sigcon.backend.parametrization.users.domain.model.PasswordHistory;
import com.sigcon.backend.parametrization.users.domain.repository.PasswordHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * PA-RF-01 / PA-RF-02 v3.0 (Control de Cambios PA, 2026-05-29): politica de
 * contrasenas unificada del sistema.
 *
 * <p>Reglas de complejidad (PA-RF-01 punto 3):
 * <ul>
 *   <li>minimo 8 caracteres</li>
 *   <li>al menos 1 letra mayuscula</li>
 *   <li>al menos 1 numero</li>
 *   <li>al menos 1 simbolo</li>
 *   <li>no reutilizar la contrasena actual ni las ultimas 5</li>
 * </ul>
 *
 * <p>Se aplica en TODOS los puntos donde se establece una contrasena:
 * restablecimiento por token (PA-RF-02), creacion de empresa + primer admin
 * (PA-RF-PLAT-01), gestion de PLATFORM_ADMIN (PA-RF-PLAT-07) y creacion/edicion
 * de usuarios de empresa (PA-RF-08/09).
 */
@Service
@RequiredArgsConstructor
public class PasswordPolicyService {

    public static final int MIN_LENGTH = 8;
    public static final int MAX_LENGTH = 72; // limite de BCrypt (PA-RF-01 H-01)
    public static final int HISTORY_SIZE = 5;

    private final PasswordHistoryRepository historyRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Valida solo la complejidad (sin historial). Lanza
     * {@link IllegalArgumentException} con un mensaje claro si no cumple.
     */
    public void validateComplexity(String password) {
        if (password == null || password.length() < MIN_LENGTH) {
            throw new IllegalArgumentException(
                    "La contrasena debe tener al menos " + MIN_LENGTH + " caracteres.");
        }
        if (password.length() > MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "La contrasena no puede superar " + MAX_LENGTH + " caracteres.");
        }
        if (!password.matches(".*[A-Z].*")) {
            throw new IllegalArgumentException("La contrasena debe incluir al menos una letra mayuscula.");
        }
        if (!password.matches(".*[0-9].*")) {
            throw new IllegalArgumentException("La contrasena debe incluir al menos un numero.");
        }
        if (!password.matches(".*[^A-Za-z0-9].*")) {
            throw new IllegalArgumentException(
                    "La contrasena debe incluir al menos un simbolo (por ejemplo: !@#$%).");
        }
    }

    /**
     * Valida complejidad + que la nueva contrasena no sea igual a la actual ni
     * a ninguna de las ultimas {@link #HISTORY_SIZE}. Llamar ANTES de codificar
     * y guardar la nueva contrasena.
     *
     * @param userId      id del usuario (null en creacion: solo valida complejidad)
     * @param currentHash hash BCrypt actual del usuario (puede ser null en creacion)
     * @param rawPassword contrasena en texto plano que se quiere establecer
     */
    public void assertAllowedForUser(Long userId, String currentHash, String rawPassword) {
        validateComplexity(rawPassword);
        if (currentHash != null && passwordEncoder.matches(rawPassword, currentHash)) {
            throw new IllegalArgumentException("La nueva contrasena no puede ser igual a la contrasena actual.");
        }
        if (userId != null) {
            List<PasswordHistory> last = historyRepository.findTop5ByUserIdOrderByCreatedAtDesc(userId);
            for (PasswordHistory h : last) {
                if (passwordEncoder.matches(rawPassword, h.getPasswordHash())) {
                    throw new IllegalArgumentException(
                            "No puede reutilizar ninguna de sus ultimas " + HISTORY_SIZE + " contrasenas.");
                }
            }
        }
    }

    /**
     * Registra el hash BCrypt en el historial del usuario. Llamar DESPUES de
     * guardar la nueva contrasena en el usuario.
     */
    public void record(Long userId, String encodedHash) {
        if (userId == null || encodedHash == null) return;
        historyRepository.save(PasswordHistory.builder()
                .userId(userId)
                .passwordHash(encodedHash)
                .createdAt(LocalDateTime.now())
                .build());
    }
}
