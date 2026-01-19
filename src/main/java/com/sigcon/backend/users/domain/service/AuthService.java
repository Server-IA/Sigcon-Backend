package com.sigcon.backend.users.domain.service;

import com.sigcon.backend.config.EmailService;
import com.sigcon.backend.security.JwtService;
import com.sigcon.backend.users.application.auth.AuthRequest;
import com.sigcon.backend.users.application.auth.ResetPasswordRequest;
import com.sigcon.backend.users.domain.model.BlackListedToken;
import com.sigcon.backend.users.domain.model.PasswordResetToken;
import com.sigcon.backend.users.domain.model.Role;
import com.sigcon.backend.users.domain.model.User;
import com.sigcon.backend.users.domain.model.enums.Status;
import com.sigcon.backend.users.domain.repository.BlackListedTokenRepository;
import com.sigcon.backend.users.domain.repository.PasswordResetTokenRepository;
import com.sigcon.backend.users.domain.repository.RoleRepository;
import com.sigcon.backend.users.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final PasswordResetTokenRepository tokenRepository;
    private final EmailService emailService;
    private final BlackListedTokenRepository blackListedTokenRepository;
    private final RoleRepository roleRepository;

    public ResponseEntity<?> register(AuthRequest request) {


        if (request.getName().isEmpty() ||
                request.getLastname().isEmpty() ||
                request.getEmail().isEmpty() ||
                request.getPassword().isEmpty()) {

            return ResponseEntity.badRequest().body("Todos los campos son obligatorios (nombre, apellido, correo y contraseña)");
        }


        String email = request.getEmail().trim();


        if (userRepository.existsByEmail(email)) {
            return ResponseEntity.badRequest().body("Este correo ya está en uso");
        }

        Role role = roleRepository.findByName("USER")
                .orElseThrow(() -> new RuntimeException("El rol USER no existe en la base de datos."));


        User user = User.builder()
                .name(request.getName())
                .lastname(request.getLastname())
                .email(email.toLowerCase())
                .password(passwordEncoder.encode(request.getPassword()))
                .roles(Set.of(role))
                .status(Status.ACTIVE)
                .creationDate(LocalDateTime.now())
                .lastUpdateDate(LocalDateTime.now())
                .build();

        userRepository.save(user);

        String token = jwtService.generateToken(user);

        return ResponseEntity.ok(token);
    }

    public ResponseEntity<?> login(AuthRequest request){
        try {
            Authentication auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
            );

            User user = (User) auth.getPrincipal();

            String token = jwtService.generateToken(user);

            return ResponseEntity.ok(token);

        } catch (AuthenticationException e) {
            return ResponseEntity.badRequest()
                    .body("Credenciales incorrectas. Por favor, verifica tu correo electrónico y contraseña.");
        }
    }

    public void sendResetPasswordLink(AuthRequest request){
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

        String resetLink = "http://localhost:8080/auth/reset-password?token=" + token; //Aqui toca poner un redireccionamiento en el front para que el usuario pueda cambiar la contraseña

        String subject = "Restablecimiento de contraseña - SIGCON";
        String message = """
                Hola %s,
                
                Recibimos una solicitud para restablecer tu contraseña.
                
                Haz clic en el siguiente enlace para continuar:
                %s
                
                Este enlace expirará en 15 minutos.
                
                Si no fuiste tú, ignora este mensaje.
                
                Equipo SIGCON
                """.formatted(user.getName(), resetLink);

        emailService.sendEmail(user.getEmail(), subject, message);

    }

    public void resetPassword(ResetPasswordRequest request){
        PasswordResetToken resetToken = tokenRepository.findByTokenAndUsedFalse(request.getToken())
                .orElseThrow(() -> new RuntimeException("El token es inválido o ya ha sido utilizado."));

        if (resetToken.isExpired()){
            throw new RuntimeException("El token ha expirado. Por favor, solicita un nuevo restablecimiento de contraseña.");
        }

        User user = resetToken.getUser();
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        resetToken.setUsed(true);
        tokenRepository.save(resetToken);
    }

    public ResponseEntity<?> logout(String token){
        if (token == null || token.isEmpty()) {
            return ResponseEntity.badRequest().body("Token no proporcionado.");
        }

        if (!blackListedTokenRepository.existsByToken(token)) {
            BlackListedToken blackListedToken = BlackListedToken.builder().token(token).build();
            blackListedTokenRepository.save(blackListedToken);
            return ResponseEntity.ok("Cierre de sesión exitoso.");
        } else {
            return ResponseEntity.badRequest().body("El token ya ha sido invalidado.");
        }
    }

}
