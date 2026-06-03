package com.sigcon.backend.parametrization.users.domain.service;

import com.sigcon.backend.general.config.EmailService;
import com.sigcon.backend.general.security.JwtService;
import com.sigcon.backend.general.storage.AvatarStorageService;
import com.sigcon.backend.parametrization.users.application.auth.AuthRequest;
import com.sigcon.backend.parametrization.users.application.auth.RefreshTokenRequest;
import com.sigcon.backend.parametrization.users.application.auth.ResetPasswordRequest;
import com.sigcon.backend.parametrization.users.application.auth.TooManyRequestsException;
import com.sigcon.backend.parametrization.users.domain.model.UserSession;
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
import com.sigcon.backend.audit.domain.model.enums.AuditAction;
import com.sigcon.backend.audit.domain.model.enums.AuditModule;
import com.sigcon.backend.audit.domain.model.enums.AuditSeverity;
import com.sigcon.backend.audit.domain.service.AuditPublisher;
import com.sigcon.backend.parametrization.temporary_permissions.domain.service.TemporaryPermissionService;
import com.sigcon.backend.platform.tenant.TenantContext;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

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
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
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
    private final AuditPublisher auditPublisher;
    private final TemporaryPermissionService temporaryPermissionService;
    private final PasswordPolicyService passwordPolicyService;
    private final SessionService sessionService;
    private final PasswordResetRateLimiter passwordResetRateLimiter;
    // PA-RNF-10 (Pendientes PA): notificar al usuario tras el 3er intento fallido.
    private final com.sigcon.backend.parametrization.notifications.domain.service.NotificationService notificationService;

    /**
     * PA-RNF-10 (Pendientes PA) punto 4: umbrales de fuerza bruta como constantes
     * (antes hardcodeados inline). Primer paso hacia su parametrizacion por empresa
     * (tabla parameters category='SECURITY'), que queda como mejora futura.
     */
    private static final int MAX_FAILED_ATTEMPTS = 5;   // bloqueo de cuenta
    private static final int LOCK_MINUTES = 15;          // duracion del bloqueo
    private static final int CAPTCHA_THRESHOLD = 3;      // a partir de aqui: notif + captcha

    /**
     * Publica un audit log en el contexto del tenant del usuario afectado.
     * Si el usuario es PLATFORM_ADMIN (sin empresa) el log se publica sin
     * company_id (cae en contexto del ejecutor actual, usualmente null).
     */
    private void auditUserEvent(User target, AuditAction action, AuditSeverity severity, String description) {
        Runnable publish = () -> auditPublisher.publish(action, AuditModule.PA, severity,
                "User", target.getId(), description, null, null, null);
        if (target.getCompanyId() != null) {
            TenantContext.runAs(target.getCompanyId(), false, publish);
        } else {
            publish.run();
        }
    }

    /**
     * QA Bloque AT (HU-PA-13 E6/E7 + HU-PA-12 E3, 2026-05-13): construye el set
     * de permisos efectivos del usuario combinando los permisos de TODOS sus
     * roles activos con los permisos temporales ACTIVE dentro de su ventana
     * (delegados por un admin). El set retornado SIN prefijo {@code PERM_} —
     * el frontend lo usa directamente con codigos planos
     * (ej. {@code PAR.PERMISOS_TEMPORALES.VER}).
     *
     * <p>PLATFORM_ADMIN y ADMIN tienen bypass total en el frontend
     * ({@code usePermissions}), pero igual incluimos sus codigos por
     * trazabilidad y consistencia.
     */
    private Set<String> buildEffectivePermissions(User user) {
        Set<String> codes = new LinkedHashSet<>();
        if (user.getRoles() != null) {
            user.getRoles().stream()
                    .filter(r -> r != null && r.getPermissions() != null)
                    .flatMap(r -> r.getPermissions().stream())
                    .filter(p -> p != null && p.getCode() != null)
                    .map(p -> p.getCode().startsWith("PERM_") ? p.getCode().substring(5) : p.getCode())
                    .forEach(codes::add);
        }
        try {
            Set<String> temporary = temporaryPermissionService.computeEffectiveCodes(user.getId());
            if (temporary != null) codes.addAll(temporary);
        } catch (Exception ex) {
            // defensivo: si el calculo de temporales falla por cualquier razon, NO
            // bloqueamos el login (el rol siempre alcanza para entrar a su tablero
            // base). Se loguea para diagnostico.
            log.warn("HU-PA-13 buildEffectivePermissions fallo para userId={}: {}",
                    user.getId(), ex.getMessage());
        }
        return codes;
    }

    /**
     * QA Bloque AT (HU-PA-13, 2026-05-13): refresh runtime del set efectivo.
     * El frontend {@code usePermissions} lo invoca despues de operaciones que
     * pueden alterar los permisos (asignar/revocar temporal, cambio de rol),
     * sin requerir re-login.
     */
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public Set<String> getMyEffectivePermissions(String email) {
        if (email == null || email.isBlank()) return new LinkedHashSet<>();
        return userRepository.findByEmail(email)
                .map(this::buildEffectivePermissions)
                .orElse(new LinkedHashSet<>());
    }

    /**
     * QA Bloque AV (HU-PA-13 E7 regla #11, 2026-05-14): devuelve los permisos
     * efectivos del usuario distinguiendo SOURCE (rol vs temporal).
     *
     * <p>El frontend lo usa para implementar la regla #11: el boton "Asignar
     * permiso temporal" solo se muestra si el usuario tiene
     * {@code PAR.PERMISOS_TEMPORALES.ASIGNAR} via su ROL (no via temporal).
     * Asi se evita la escalada recursiva: a un usuario al que se le delego
     * temporalmente la facultad de asignar, no se le habilita la UI para
     * asignar a otros.
     *
     * <p>Estructura:
     * <pre>{@code
     * {
     *   "rolePermissions": ["PERMISO_A", "PERMISO_B", ...],
     *   "temporaryPermissions": ["PERMISO_C", ...],
     *   "effectivePermissions": [...]  // union (rolePermissions + temporaryPermissions)
     * }
     * }</pre>
     */
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public Map<String, Object> getMyEffectivePermissionsSplit(String email) {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        if (email == null || email.isBlank()) {
            result.put("rolePermissions", new LinkedHashSet<>());
            result.put("temporaryPermissions", new LinkedHashSet<>());
            result.put("effectivePermissions", new LinkedHashSet<>());
            return result;
        }
        return userRepository.findByEmail(email).map(user -> {
            Set<String> roleCodes = new LinkedHashSet<>();
            if (user.getRoles() != null) {
                user.getRoles().stream()
                        .filter(r -> r != null && r.getPermissions() != null)
                        .flatMap(r -> r.getPermissions().stream())
                        .filter(p -> p != null && p.getCode() != null)
                        .map(p -> p.getCode().startsWith("PERM_") ? p.getCode().substring(5) : p.getCode())
                        .forEach(roleCodes::add);
            }
            Set<String> tempCodes = new LinkedHashSet<>();
            try {
                Set<String> t = temporaryPermissionService.computeEffectiveCodes(user.getId());
                if (t != null) tempCodes.addAll(t);
            } catch (Exception ex) {
                log.warn("HU-PA-13 getMyEffectivePermissionsSplit: error temporal codes userId={}: {}",
                        user.getId(), ex.getMessage());
            }
            Set<String> union = new LinkedHashSet<>(roleCodes);
            union.addAll(tempCodes);
            Map<String, Object> r = new java.util.LinkedHashMap<>();
            r.put("rolePermissions", roleCodes);
            r.put("temporaryPermissions", tempCodes);
            r.put("effectivePermissions", union);
            return r;
        }).orElseGet(() -> {
            Map<String, Object> empty = new java.util.LinkedHashMap<>();
            empty.put("rolePermissions", new LinkedHashSet<>());
            empty.put("temporaryPermissions", new LinkedHashSet<>());
            empty.put("effectivePermissions", new LinkedHashSet<>());
            return empty;
        });
    }

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
    public ResponseEntity<?> login(AuthRequest request) {
        return login(request, null, null);
    }

    /**
     * PA-RF-01 v3.0 (Control de Cambios PA, 2026-05-29): autentica al usuario,
     * registra una sesion activa (con limite FIFO de 3, metadata de dispositivo
     * e IP) y emite access token (JWT) + refresh token + sessionId.
     *
     * @param ipAddress       IP del cliente (la extrae el controller)
     * @param userAgentHeader User-Agent del header (fallback si el body no lo trae)
     */
    public ResponseEntity<?> login(AuthRequest request, String ipAddress, String userAgentHeader){
        // H-09 (PA-RNF, Pendientes PA 2026-05-30): contexto de auditoria con IP +
        // User-Agent para TODOS los eventos de login (exitoso, bloqueado, empresa
        // inactiva, intento fallido). El controller extrae la IP (X-Forwarded-For).
        String userAgent = (request.getUserAgent() != null && !request.getUserAgent().isBlank())
                ? request.getUserAgent() : userAgentHeader;
        String auditCtx = " | ip=" + (ipAddress == null ? "-" : ipAddress)
                + " | ua=" + (userAgent == null ? "-" : userAgent);
        // Verificar si la cuenta esta bloqueada
        Optional<User> userOpt = userRepository.findByUsernameOrEmail(
                request.getUsernameOrEmail(), request.getUsernameOrEmail());

        if (userOpt.isPresent()) {
            User foundUser = userOpt.get();
            if (foundUser.getLockedUntil() != null && foundUser.getLockedUntil().isAfter(LocalDateTime.now())) {
                // PA-RF-01 punto 5 (v3.0): cuenta bloqueada -> HTTP 423 Locked
                // (antes 400). Codigo semantico para que el frontend distinga
                // "credenciales malas" de "cuenta bloqueada".
                return ResponseEntity.status(org.springframework.http.HttpStatus.LOCKED).body(
                        ErrorRespondJson.getErrorRespondMessage(
                                Optional.of("Cuenta bloqueada temporalmente por multiples intentos fallidos. Intente de nuevo mas tarde."))
                );
            }
            // QA Bloque PA Bug 15 (HU-PA-07 E3, 2026-05-09): bloquear pre-authenticate
            // si el usuario tiene status=BLOCKED. Si no, Spring Security responderia
            // con BadCredentialsException porque isEnabled()=false (mensaje generico).
            if (foundUser.getStatus() == com.sigcon.backend.parametrization.users.domain.model.enums.Status.BLOCKED) {
                auditUserEvent(foundUser, AuditAction.LOGIN, AuditSeverity.HIGH,
                        "USER_BLOCKED: intento de login bloqueado, usuario con status=BLOCKED" + auditCtx);
                return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN).body(
                        ErrorRespondJson.getErrorRespondMessage(Optional.of(
                                "Tu cuenta esta bloqueada por el administrador. Contacta al administrador para mas informacion.")));
            }
        }

        try {
            Authentication auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsernameOrEmail(), request.getPassword())
            );

            User user = (User) auth.getPrincipal();

            // HU-PA-07 E3 (QA Bloque PA Bug 15, 2026-05-09): bloquear login si
            // el usuario tiene status=BLOCKED (estado administrativo distinto
            // de INACTIVE soft-delete y de lockedUntil temporal).
            if (user.getStatus() == com.sigcon.backend.parametrization.users.domain.model.enums.Status.BLOCKED) {
                auditUserEvent(user, AuditAction.LOGIN, AuditSeverity.HIGH,
                        "USER_BLOCKED: intento de login bloqueado, usuario con status=BLOCKED" + auditCtx);
                return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN).body(
                        ErrorRespondJson.getErrorRespondMessage(Optional.of(
                                "Tu cuenta esta bloqueada por el administrador. Contacta al administrador para mas informacion.")));
            }

            // HU-PA-01 E3 / HU-PLAT-05 E3 (QA Bloque PA Bug 1, 2026-05-09):
            // si la empresa del usuario esta INACTIVE, bloquear login con
            // HTTP 403 (no 400) y mensaje literal de la HU. Tambien registrar
            // intento en auditoria con motivo COMPANY_INACTIVE.
            // PLATFORM_ADMIN (sin company_id) no aplica.
            if (user.getCompanyId() != null) {
                var company = companyRepository.findById(user.getCompanyId()).orElse(null);
                if (company == null
                        || company.getStatus() != com.sigcon.backend.platform.companies.domain.model.Company.CompanyStatus.ACTIVE) {
                    auditUserEvent(user, AuditAction.LOGIN, AuditSeverity.HIGH,
                            "COMPANY_INACTIVE: intento de login bloqueado, empresa desactivada (companyId="
                              + user.getCompanyId() + ")" + auditCtx);
                    return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN).body(
                            ErrorRespondJson.getErrorRespondMessage(Optional.of(
                                    "La empresa a la que pertenece esta cuenta está desactivada. "
                                  + "Contacte al administrador de plataforma.")));
                }
            }

            // Resetear contador de intentos fallidos
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
            userRepository.save(user);

            // HU-AU-01: registrar login exitoso con severidad LOW (H-09: + ip/ua)
            auditUserEvent(user, AuditAction.LOGIN, AuditSeverity.LOW,
                    "Login exitoso: " + user.getUsername() + " (" + user.getEmail() + ")" + auditCtx);

            // PA-RF-01 v3.0: crear la sesion activa (limite FIFO de 3) con metadata
            // de dispositivo + IP, y emitir refresh token + sessionId. El access
            // token (JWT) lleva el claim sessionId para permitir logout por sesion.
            SessionService.IssuedSession session = sessionService.createSession(
                    user.getId(), request.getDeviceId(), userAgent, ipAddress);

            String token = jwtService.generateToken(user, java.util.Map.of("sessionId", session.sessionId()));

            Map<String, Object> response = new HashMap<String, Object>();
            response.put("token", token);
            // PA-RF-01 puntos 1/6: refresh token + sessionId persistidos como parte
            // de la sesion activa registrada.
            response.put("refreshToken", session.refreshToken());
            response.put("sessionId", session.sessionId());
            response.put("refreshTokenExpiresAt", session.expiresAt().toString());
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

            // QA Bloque AT (HU-PA-13 E6/E7 + HU-PA-12 E3, 2026-05-13): construye
            // el set de permisos efectivos = permisos del rol + permisos temporales
            // ACTIVE dentro de su ventana. Sin esto, el frontend recibe
            // effectivePermissions=[] y usePermissions().has() siempre falla, lo
            // que provoca que: (a) los permisos temporales asignados no den
            // acceso real al modulo, (b) el menu/sidebar no se filtre por
            // permisos y muestre items que el usuario no puede operar.
            response.put("effectivePermissions", buildEffectivePermissions(user));

            return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(Optional.of("Inicio de sesion exitoso."), Optional.of(response))
            );

        } catch (AuthenticationException e) {

            boolean requireCaptcha = false;
            // Incrementar contador de intentos fallidos
            if (userOpt.isPresent()) {
                User foundUser = userOpt.get();
                int attempts = (foundUser.getFailedLoginAttempts() != null ? foundUser.getFailedLoginAttempts() : 0) + 1;
                foundUser.setFailedLoginAttempts(attempts);
                boolean locked = false;
                if (attempts >= MAX_FAILED_ATTEMPTS) {
                    foundUser.setLockedUntil(LocalDateTime.now().plusMinutes(LOCK_MINUTES));
                    locked = true;
                }
                userRepository.save(foundUser);
                // HU-AU-01 forense: registrar intento fallido (severidad MEDIUM) o bloqueo (HIGH)
                auditUserEvent(foundUser, AuditAction.LOGIN,
                        locked ? AuditSeverity.HIGH : AuditSeverity.MEDIUM,
                        (locked
                            ? "Cuenta bloqueada tras " + attempts + " intentos fallidos: " + foundUser.getUsername()
                            : "Intento de login fallido (" + attempts + "/" + MAX_FAILED_ATTEMPTS + ") para: "
                                + foundUser.getUsername()) + auditCtx);
                // PA-RNF-10 punto 3: a partir del umbral, pedir CAPTCHA al frontend.
                requireCaptcha = attempts >= CAPTCHA_THRESHOLD;
                // PA-RNF-10 punto 2: notificar al usuario en el 3er intento (solo tenant
                // users; PLATFORM_ADMIN no tiene company_id para la notificacion in-app).
                if (attempts == CAPTCHA_THRESHOLD && foundUser.getCompanyId() != null) {
                    notifyFailedLoginAttempts(foundUser);
                }
            }

            // PA-RNF-10 punto 3: cuerpo de error con flag requireCaptcha, conservando
            // el shape que lee el frontend (message/msg). El flag se incluye siempre
            // (false antes del umbral) para que el cliente lo evalue de forma uniforme.
            String msg = "Credenciales inválidas. Por favor, verifica tu correo electrónico y contraseña.";
            java.util.Map<String, Object> body = new java.util.HashMap<>();
            body.put("success", false);
            body.put("code", 400);
            body.put("error", "Error en la operación");
            body.put("message", msg);
            body.put("msg", msg);
            body.put("requireCaptcha", requireCaptcha);
            return ResponseEntity.badRequest().body(body);
        }
    }

    /**
     * PA-RNF-10 punto 2: notifica al usuario (in-app) cuando alcanza el umbral de
     * intentos fallidos. NUNCA rompe el login: cualquier fallo se traga con log.
     * Sin actionUrl para evitar navegacion a una ruta que pudiera no existir; el
     * cuerpo del mensaje indica la accion (cambiar contrasena).
     */
    private void notifyFailedLoginAttempts(User u) {
        try {
            notificationService.publishToUser(u.getId(),
                    com.sigcon.backend.parametrization.notifications.application.PublishEventRequest.builder()
                            .companyId(u.getCompanyId())
                            .eventKey("LOGIN_FAILED_ALERT")
                            .title("Intentos fallidos de inicio de sesion")
                            .body("Se detectaron " + CAPTCHA_THRESHOLD + " intentos fallidos de inicio de sesion en "
                                + "tu cuenta. Si no fuiste tu, cambia tu contrasena cuanto antes.")
                            .build());
        } catch (Exception ex) {
            log.warn("PA-RNF-10: no se pudo notificar intentos fallidos a userId={}: {}",
                    u.getId(), ex.getMessage());
        }
    }

    /**
     * Genera un token de restablecimiento de contrasena y envia el enlace por correo electronico.
     * El token tiene una vigencia de 10 minutos por seguridad.
     *
     * @param request contiene el email del usuario que solicita el restablecimiento
     * @throws RuntimeException si el email no esta registrado o falla el envio del correo
     */
    public void sendResetPasswordLink(AuthRequest request) {
        sendResetPasswordLink(request, null);
    }

    /**
     * PA-RF-02 v3.0 (Control de Cambios PA, 2026-05-29): genera y envia el enlace
     * de recuperacion endurecido:
     * <ul>
     *   <li>Anti-enumeracion: si el email no existe, sale en silencio.</li>
     *   <li>Rate limiting por email + IP.</li>
     *   <li>Token unico: invalida los tokens previos no usados.</li>
     *   <li>Trazabilidad: guarda IP y deviceId en el token.</li>
     *   <li>Auditoria: registra el evento FORGOT_PASSWORD_REQUESTED.</li>
     * </ul>
     */
    public void sendResetPasswordLink(AuthRequest request, String ipAddress){
        String email = request.getEmail() == null ? null : request.getEmail().trim();

        // PA-RF-02 punto 2: rate limiting anti-abuso (por email + IP).
        if (!passwordResetRateLimiter.allow(email, ipAddress)) {
            throw new TooManyRequestsException(
                    "Demasiadas solicitudes de recuperacion. Intente de nuevo mas tarde.");
        }

        Optional<User> userOpt = (email == null || email.isBlank())
                ? Optional.empty() : userRepository.findByEmail(email);

        // PA-RF-02 punto 1: ANTI-ENUMERACION. Si el email no existe, NO revelamos
        // nada; salimos en silencio y el controller responde el mismo mensaje
        // generico que en el caso exitoso.
        if (userOpt.isEmpty()) {
            log.info("FORGOT_PASSWORD_REQUESTED: email no registrado (anti-enumeracion) ip={}", ipAddress);
            return;
        }

        User user = userOpt.get();

        // PA-RF-02 punto 3: token unico por usuario. Invalidar tokens previos
        // no usados antes de emitir el nuevo.
        java.util.List<PasswordResetToken> previous = tokenRepository.findByUser_IdAndUsedFalse(user.getId());
        if (previous != null && !previous.isEmpty()) {
            previous.forEach(t -> t.setUsed(true));
            tokenRepository.saveAll(previous);
        }

        String token = UUID.randomUUID().toString();
        PasswordResetToken resetToken = PasswordResetToken.builder()
                .token(token)
                .user(user)
                .expiryDate(LocalDateTime.now().plusMinutes(10))
                .used(false)
                .ipAddress(ipAddress)              // PA-RF-02 punto 6
                .deviceId(request.getDeviceId())   // PA-RF-02 punto 6
                .build();
        tokenRepository.save(resetToken);

        // PA-RF-02 punto 5: auditoria forense del evento.
        auditUserEvent(user, AuditAction.UPDATE, AuditSeverity.MEDIUM,
                "FORGOT_PASSWORD_REQUESTED: solicitud de recuperacion para "
                        + user.getUsername() + " (ip=" + ipAddress + ")");

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

        // PA-RF-02 punto 1: si el envio del correo falla, NO propagamos el error
        // (anti-enumeracion + no romper el flujo). Se loguea para diagnostico.
        try {
            emailService.sendEmail(user.getEmail(), subject, message);
        } catch (Exception mailEx) {
            log.warn("FORGOT_PASSWORD: fallo el envio de correo para userId={}: {}",
                    user.getId(), mailEx.getMessage());
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

        User user = resetToken.getUser();

        if (resetToken.isExpired()){
            // PA-RF-02 punto 5: PASSWORD_RESET_FAILED_EXPIRED_TOKEN
            auditUserEvent(user, AuditAction.UPDATE, AuditSeverity.MEDIUM,
                    "PASSWORD_RESET_FAILED_EXPIRED_TOKEN: intento con token expirado para " + user.getUsername());
            throw new RuntimeException("El token ha expirado. Por favor, solicita un nuevo restablecimiento de contraseña.");
        }

        // PA-RF-01 punto 3 / PA-RF-02 punto 7 (v3.0): politica unificada de
        // contrasenas (>=8, mayuscula, numero, simbolo) + no reutilizar la
        // contrasena actual ni las ultimas 5. Reemplaza la validacion inline
        // de 6 caracteres del original.
        passwordPolicyService.assertAllowedForUser(
                user.getId(), user.getPassword(), request.getNewPassword());

        String encodedPassword = passwordEncoder.encode(request.getNewPassword());
        user.setPassword(encodedPassword);
        // PA-RF-02 punto 4: invalidar access tokens vivos marcando el corte; el
        // SessionInvalidationFilter expulsa los JWT emitidos antes de este instante.
        user.setSessionInvalidatedAt(LocalDateTime.now());
        userRepository.save(user);
        passwordPolicyService.record(user.getId(), encodedPassword);

        // PA-RF-02 punto 4: revocar TODOS los refresh tokens / sessionIds activos.
        int revokedSessions = sessionService.revokeAllForUser(user.getId());

        resetToken.setUsed(true);
        tokenRepository.save(resetToken);

        // PA-RF-02 punto 5 / HU-AU-01: PASSWORD_RESET_SUCCESS (severidad HIGH, sensible)
        auditUserEvent(user, AuditAction.UPDATE, AuditSeverity.HIGH,
                "PASSWORD_RESET_SUCCESS: contrasena restablecida via token para "
                        + user.getUsername() + " (" + revokedSessions + " sesiones revocadas)");
    }

    /**
     * Cierra la sesion del usuario invalidando su token JWT.
     * Agrega el token a la lista negra para impedir su reutilizacion.
     *
     * @param token token JWT a invalidar
     * @return ResponseEntity con mensaje de exito o error si el token ya fue invalidado
     */
    public ResponseEntity<?> logout(String token){
        return logout(token, null);
    }

    /**
     * PA-RF-27 (Pendientes PA): cierra la sesion invalidando el token. Ademas del
     * token completo (que valida el BlackListFilter), persiste el jti y la
     * expiracion para que {@code BlacklistCleanupScheduler} purgue las entradas
     * vencidas. El audit del LOGOUT incluye la IP del cliente (punto 5).
     */
    public ResponseEntity<?> logout(String token, String ipAddress){
        if (token == null || token.isEmpty()) {
            return ResponseEntity.badRequest().body(
                    ErrorRespondJson.getErrorRespondMessage(Optional.of("Token no proporcionado para cerrar sesión."))
            );
        }

        if (!blackListedTokenRepository.existsByToken(token)) {
            // PA-RF-27: guardar jti + expiracion (claim exp) ademas del token.
            String jti = jwtService.getJti(token);
            java.util.Date exp = jwtService.getExpiration(token);
            java.time.LocalDateTime expLdt = (exp == null) ? null
                    : java.time.LocalDateTime.ofInstant(exp.toInstant(), java.time.ZoneId.systemDefault());
            BlackListedToken blackListedToken = BlackListedToken.builder()
                    .token(token).jti(jti).expiresAt(expLdt).build();
            blackListedTokenRepository.save(blackListedToken);
            // PA-RF-01 v3.0: revocar la sesion activa asociada al token (por su
            // claim sessionId) para que su refresh token deje de funcionar.
            try {
                String sid = jwtService.getSessionId(token);
                if (sid != null) sessionService.revokeBySessionId(sid);
            } catch (Exception ignored) { /* no bloquear logout por esto */ }
            // HU-AU-01: registrar LOGOUT resolviendo el usuario desde el JWT.
            // PA-RF-27 punto 5 + H-09: incluir la IP en el description del audit.
            String auditCtx = " | ip=" + (ipAddress == null ? "-" : ipAddress);
            try {
                String username = jwtService.getUsername(token);
                userRepository.findByUsernameOrEmail(username, username).ifPresent(u ->
                        auditUserEvent(u, AuditAction.LOGOUT, AuditSeverity.LOW,
                                "Logout de " + u.getUsername() + auditCtx));
            } catch (Exception ignored) {
                // Token invalido o malformado: no bloquear el logout por esto
            }
            return ResponseEntity.ok(
                    SuccessRespondJson.getSuccessRespondMessage(Optional.of("Cierre de sesión exitoso."), Optional.empty())
            );
        } else {
            return ResponseEntity.badRequest().body(
                    ErrorRespondJson.getErrorRespondMessage(Optional.of("El token ya ha sido invalidado."))
            );
        }
    }

    /**
     * PA-RF-01 v3.0 (Control de Cambios PA, 2026-05-29): renueva el access token
     * a partir de un refresh token valido (no revocado, no expirado). Valida que
     * el usuario siga ACTIVE y su empresa ACTIVE. Actualiza last_used_at.
     */
    public ResponseEntity<?> refresh(RefreshTokenRequest request) {
        if (request == null || request.getRefreshToken() == null || request.getRefreshToken().isBlank()) {
            return ResponseEntity.badRequest().body(
                    ErrorRespondJson.getErrorRespondMessage(Optional.of("Refresh token no proporcionado.")));
        }
        Optional<UserSession> sessionOpt = sessionService.validateRefreshToken(request.getRefreshToken());
        if (sessionOpt.isEmpty()) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.UNAUTHORIZED).body(
                    ErrorRespondJson.getErrorRespondMessage(Optional.of(
                            "Refresh token invalido o expirado. Inicie sesion nuevamente.")));
        }
        UserSession session = sessionOpt.get();
        User user = userRepository.findById(session.getUserId()).orElse(null);
        if (user == null
                || user.getStatus() != com.sigcon.backend.parametrization.users.domain.model.enums.Status.ACTIVE) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.UNAUTHORIZED).body(
                    ErrorRespondJson.getErrorRespondMessage(Optional.of(
                            "La cuenta no esta disponible. Inicie sesion nuevamente.")));
        }
        // Empresa activa (PA-RF-01 punto 5 / HU-PA-01 E3)
        if (user.getCompanyId() != null) {
            var company = companyRepository.findById(user.getCompanyId()).orElse(null);
            if (company == null
                    || company.getStatus() != com.sigcon.backend.platform.companies.domain.model.Company.CompanyStatus.ACTIVE) {
                return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN).body(
                        ErrorRespondJson.getErrorRespondMessage(Optional.of(
                                "La empresa a la que pertenece esta cuenta está desactivada.")));
            }
        }
        sessionService.touch(session);
        String token = jwtService.generateToken(user,
                java.util.Map.of("sessionId", session.getSessionId()));
        Map<String, Object> response = new HashMap<>();
        response.put("token", token);
        response.put("sessionId", session.getSessionId());
        response.put("effectivePermissions", buildEffectivePermissions(user));
        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(Optional.of("Token renovado."), Optional.of(response)));
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
