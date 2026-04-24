package com.sigcon.backend.parametrization.users.domain.service;

import com.sigcon.backend.general.config.EmailService;
import com.sigcon.backend.general.security.JwtService;
import com.sigcon.backend.general.storage.AvatarStorageService;
import com.sigcon.backend.parametrization.users.application.auth.AuthRequest;
import com.sigcon.backend.parametrization.users.application.auth.ResetPasswordRequest;
import com.sigcon.backend.parametrization.users.domain.model.BlackListedToken;
import com.sigcon.backend.parametrization.users.domain.model.PasswordResetToken;
import com.sigcon.backend.parametrization.users.domain.model.Role;
import com.sigcon.backend.parametrization.users.domain.model.User;
import com.sigcon.backend.parametrization.users.domain.model.enums.Status;
import com.sigcon.backend.parametrization.users.domain.repository.BlackListedTokenRepository;
import com.sigcon.backend.parametrization.users.domain.repository.PasswordResetTokenRepository;
import com.sigcon.backend.parametrization.users.domain.repository.RoleRepository;
import com.sigcon.backend.parametrization.users.domain.repository.UserRepository;
import com.sigcon.backend.platform.companies.domain.repository.CompanyRepository;
import com.sigcon.backend.utils.ErrorRespondJson;
import com.sigcon.backend.utils.SuccessRespondJson;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService implements UserDetailsService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final PasswordResetTokenRepository tokenRepository;
    private final EmailService emailService;
    private final BlackListedTokenRepository blackListedTokenRepository;
    private final RoleRepository roleRepository;
    private final AvatarStorageService avatarStorageService;
    private final CompanyRepository companyRepository;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    /**
     * Registra un nuevo usuario en el sistema con rol USER por defecto.
     * Valida campos obligatorios, unicidad de email y genera token JWT tras el registro.
     *
     * @param request datos del usuario (nombre, apellido, email, password, avatar opcional)
     * @return ResponseEntity con token JWT si el registro es exitoso, o mensaje de error
     */
    public ResponseEntity<?> register(AuthRequest request) {


        if (request.getName().isEmpty() ||
                request.getLastname().isEmpty() ||
                request.getEmail().isEmpty() ||
                request.getPassword().isEmpty()) {

            return ResponseEntity.badRequest().body(
                    Map.of("success", false,
                            "message", "Todos los campos son obligatorios(nombre, apellido, correo electrónico y contraseña)."));
        }


        String email = request.getEmail().trim();


        if (userRepository.existsByEmail(email)) {
            return ResponseEntity.badRequest().body(
                    Map.of("success", false,
                            "message", "El correo electrónico ya está registrado. Por favor, utiliza otro correo."));
        }

        // Multi-tenant: el self-registration publico queda deshabilitado porque no
        // puede decidir a que empresa pertenece el nuevo usuario. Violaria el constraint
        // ck_users_tenant_or_platform si se inserta sin company_id ni platform_role.
        // Los usuarios se crean desde Parametrizacion > Usuarios (tenant admin) o desde
        // el modulo Plataforma (PLATFORM_ADMIN). Si en el futuro se requiere self-registration,
        // debe agregarse seleccion de empresa + validacion por invitacion.
        return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN).body(
                Map.of("success", false,
                        "message", "El registro publico esta deshabilitado. Contacte al administrador "
                                 + "de su empresa para que le cree una cuenta."));
    }

    /**
     * Autentica al usuario y genera un token JWT.
     * Implementa contador de intentos fallidos: tras 5 intentos, la cuenta se bloquea por 15 minutos.
     */
    public ResponseEntity<?> login(AuthRequest request){
        // Verificar si la cuenta esta bloqueada
        Optional<User> userOpt = userRepository.findByUsernameOrEmail(
                request.getUsernameOrEmail(), request.getUsernameOrEmail());

        if (userOpt.isPresent()) {
            User foundUser = userOpt.get();
            if (foundUser.getLockedUntil() != null && foundUser.getLockedUntil().isAfter(LocalDateTime.now())) {
                return ResponseEntity.badRequest().body(
                        ErrorRespondJson.getErrorRespondMessage(
                                Optional.of("Cuenta bloqueada temporalmente por multiples intentos fallidos. Intente de nuevo mas tarde."))
                );
            }
        }

        try {
            Authentication auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsernameOrEmail(), request.getPassword())
            );

            User user = (User) auth.getPrincipal();

            // HU-PLAT-05 E3: si la empresa del usuario esta INACTIVE, bloquear login.
            // PLATFORM_ADMIN (sin company_id) no aplica.
            if (user.getCompanyId() != null) {
                var company = companyRepository.findById(user.getCompanyId()).orElse(null);
                if (company == null
                        || company.getStatus() != com.sigcon.backend.platform.companies.domain.model.Company.CompanyStatus.ACTIVE) {
                    return ResponseEntity.badRequest().body(
                            ErrorRespondJson.getErrorRespondMessage(Optional.of(
                                    "La empresa a la que pertenece esta cuenta esta desactivada. "
                                  + "Contacte al administrador de plataforma.")));
                }
            }

            // Resetear contador de intentos fallidos
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
            userRepository.save(user);

            String token = jwtService.generateToken(user);

            Map<String, Object> response = new HashMap<String, Object>();
            response.put("token", token);
            // Bloque F: expone tenant info para que el frontend mantenga Redux actualizado
            // sin necesidad de decodificar el JWT.
            response.put("userId", user.getId());
            response.put("username", user.getUsername());
            response.put("email", user.getEmail());
            response.put("platformRole", user.getPlatformRole()); // "PLATFORM_ADMIN" o null
            response.put("companyId", user.getCompanyId());       // null si PLATFORM_ADMIN
            response.put("companyName",
                    user.getCompanyId() == null ? null :
                    companyRepository.findById(user.getCompanyId())
                            .map(c -> c.getBusinessName()).orElse(null));

            return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(Optional.of("Inicio de sesion exitoso."), Optional.of(response))
            );

        } catch (AuthenticationException e) {

            // Incrementar contador de intentos fallidos
            if (userOpt.isPresent()) {
                User foundUser = userOpt.get();
                int attempts = (foundUser.getFailedLoginAttempts() != null ? foundUser.getFailedLoginAttempts() : 0) + 1;
                foundUser.setFailedLoginAttempts(attempts);
                if (attempts >= 5) {
                    foundUser.setLockedUntil(LocalDateTime.now().plusMinutes(15));
                }
                userRepository.save(foundUser);
            }

            return ResponseEntity.badRequest()
                    .body(
                        ErrorRespondJson.getErrorRespondMessage(Optional.of("Credenciales inválidas. Por favor, verifica tu correo electrónico y contraseña."))
                    );
        }
    }

    /**
     * Genera un token de restablecimiento de contrasena y envia el enlace por correo electronico.
     * El token tiene una vigencia de 10 minutos por seguridad.
     *
     * @param request contiene el email del usuario que solicita el restablecimiento
     * @throws RuntimeException si el email no esta registrado o falla el envio del correo
     */
    public void sendResetPasswordLink(AuthRequest request){
        try {
            User user = userRepository.findByEmail(request.getEmail())
                    .orElseThrow(() -> new RuntimeException("No se encontró un usuario con el correo proporcionado."));

            String token = UUID.randomUUID().toString();

            PasswordResetToken resetToken = PasswordResetToken.builder()
                    .token(token)
                    .user(user)
                    .expiryDate(LocalDateTime.now().plusMinutes(10))
                    .used(false)
                    .build();
            tokenRepository.save(resetToken);

            String resetLink = frontendUrl + "/reset-password/" + token; //Aqui toca poner un redireccionamiento en el front para que el usuario pueda cambiar la contraseña

            String subject = "Restablecimiento de contraseña - SIGCON";
            String message = """
                <!DOCTYPE html>
                    <html>
                    <head>
                        <meta charset="UTF-8">
                        <title>Restablecimiento de contraseña</title>
                    </head>
                    <body style="font-family: Arial, sans-serif; background-color: #f4f4f4; padding: 20px;">
                        <div style="max-width: 600px; margin: auto; background: #ffffff; padding: 30px; border-radius: 8px;">
                            
                            <h2 style="color: #333;">Hola %s,</h2>

                            <p>
                                Recibimos una solicitud para restablecer tu contraseña.
                            </p>

                            <p>
                                Haz clic en el siguiente botón para continuar:
                            </p>

                            <p style="text-align: center;">
                                <a href="%s" target="_blank"
                                style="display: inline-block; padding: 12px 20px; background-color: #007bff; 
                                color: #ffffff; text-decoration: none; border-radius: 5px;">
                                Restablecer contraseña
                                </a>
                            </p>

                            <p style="margin-top: 20px; font-size: 14px; color: #666;">
                                Si no solicitaste este cambio, puedes ignorar este mensaje.
                            </p>

                            <hr style="margin: 30px 0;">

                            <p style="font-size: 12px; color: #999;">
                                Este enlace expirará en 10 minutos por razones de seguridad.
                            </p>

                            <p style="font-size: 12px; color: #999;">
                                Equipo SIGCON
                            </p>

                        </div>
                    </body>
                </html>
            """.formatted(user.getName(), resetLink);
            emailService.sendEmail(user.getEmail(), subject, message);
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }

    }

    /**
     * Restablece la contrasena del usuario usando un token de un solo uso.
     * Valida que el token no haya expirado ni sido usado, y aplica reglas de complejidad
     * (minimo 6 caracteres, mayuscula, minuscula, numero, diferente a la anterior).
     *
     * @param request contiene el token de restablecimiento y la nueva contrasena
     * @throws RuntimeException si el token es invalido, expirado o la contrasena no cumple requisitos
     */
    public void resetPassword(ResetPasswordRequest request){
        PasswordResetToken resetToken = tokenRepository.findByTokenAndUsedFalse(request.getToken())
                .orElseThrow(() -> new RuntimeException("El token es inválido o ya ha sido utilizado."));

        if (resetToken.isExpired()){
            throw new RuntimeException("El token ha expirado. Por favor, solicita un nuevo restablecimiento de contraseña.");
        }

        User user = resetToken.getUser();

        if (request.getNewPassword().isEmpty() || request.getNewPassword().length() < 6) {
            throw new RuntimeException("La contraseña debe tener al menos 6 caracteres.");
        }
        if (!request.getNewPassword().matches(".*[A-Z].*")) {
            throw new RuntimeException("La contraseña debe tener una letra mayúscula.");
        }
        if (!request.getNewPassword().matches(".*[a-z].*")) {
            throw new RuntimeException("La contraseña debe tener una letra minúscula.");
        }
        if (!request.getNewPassword().matches(".*[0-9].*")) {
            throw new RuntimeException("La contraseña debe tener un número.");
        }
        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
            throw new RuntimeException("La contraseña no puede ser la misma que la anterior.");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        resetToken.setUsed(true);
        tokenRepository.save(resetToken);
    }

    /**
     * Cierra la sesion del usuario invalidando su token JWT.
     * Agrega el token a la lista negra para impedir su reutilizacion.
     *
     * @param token token JWT a invalidar
     * @return ResponseEntity con mensaje de exito o error si el token ya fue invalidado
     */
    public ResponseEntity<?> logout(String token){
        if (token == null || token.isEmpty()) {
            return ResponseEntity.badRequest().body(
                    ErrorRespondJson.getErrorRespondMessage(Optional.of("Token no proporcionado para cerrar sesión."))
            );
        }

        if (!blackListedTokenRepository.existsByToken(token)) {
            BlackListedToken blackListedToken = BlackListedToken.builder().token(token).build();
            blackListedTokenRepository.save(blackListedToken);
            return ResponseEntity.ok(
                    SuccessRespondJson.getSuccessRespondMessage(Optional.of("Cierre de sesión exitoso."), Optional.empty())
            );
        } else {
            return ResponseEntity.badRequest().body(
                    ErrorRespondJson.getErrorRespondMessage(Optional.of("El token ya ha sido invalidado."))
            );
        }
    }

    private String resolveAvatarFilename(String avatarValue) {
        String normalized = avatarValue.trim().toLowerCase();
        if (normalized.startsWith("data:image/") || normalized.length() > 255) {
            return avatarStorageService.saveBase64Avatar(avatarValue, null);
        }
        return avatarValue;
    }

    /**
     * Carga un usuario por su nombre de usuario o email para la autenticacion de Spring Security.
     *
     * @param usernameOrEmail nombre de usuario o correo electronico
     * @return entidad User que implementa UserDetails
     * @throws RuntimeException si no se encuentra el usuario
     */
    @Override
    public User loadUserByUsername(String usernameOrEmail) throws RuntimeException {
        return userRepository.findByUsernameOrEmail(usernameOrEmail, usernameOrEmail)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
    }

}
