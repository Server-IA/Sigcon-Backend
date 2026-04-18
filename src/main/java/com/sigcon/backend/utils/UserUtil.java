package com.sigcon.backend.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.sigcon.backend.parametrization.users.domain.model.User;
import com.sigcon.backend.parametrization.users.domain.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor

public class UserUtil {

    @Autowired

    private final UserRepository userRepository;

    /**
     * Retorna el usuario autenticado actualmente. Si no hay contexto de
     * seguridad (flujos async/scheduler como recepcion AAEF, scheduler de
     * alertas, @Async en general), devuelve el usuario del sistema como
     * fallback, para que la trazabilidad apunte a un usuario valido en lugar
     * de fallar. Se busca por username='superadmin' o email conocido.
     */
    public User getUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null || "anonymousUser".equals(auth.getName())) {
            return userRepository.findByUsernameOrEmail("superadmin", "superadmin@gmail.com")
                    .orElseThrow(() -> new RuntimeException(
                            "Usuario de sistema no encontrado. No se puede procesar "
                            + "operacion sin contexto de seguridad."));
        }
        String email = auth.getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
    }

}
