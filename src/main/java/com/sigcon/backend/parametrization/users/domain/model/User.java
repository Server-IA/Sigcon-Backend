package com.sigcon.backend.parametrization.users.domain.model;

import com.sigcon.backend.banks.banks.domain.model.BankBranch;

import com.sigcon.backend.parametrization.users.domain.model.enums.Status;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.antlr.v4.runtime.misc.NotNull;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Where;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Entity
@Table(name = "users")
@SQLDelete(sql = "UPDATE users SET deleted_at = NOW() WHERE id = ?")
@Where(clause = "deleted_at IS NULL")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "username", nullable = false)
    private String username;

    @Column(name = "lastname", nullable = false)
    private String lastname;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "password", nullable = false)
    private String password;

    @Column(length = 255)
    private String avatar;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "users_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<Role> roles;

    @Enumerated(EnumType.STRING)
    private Status status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at", nullable = true)
    private LocalDateTime deletedAt;

    @Column(name = "failed_login_attempts")
    @Builder.Default
    private Integer failedLoginAttempts = 0;

    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    // ---------- Multi-tenant (V10-A, 2026-04-19) ----------
    //
    // Invariante a nivel BD (ck_users_tenant_or_platform):
    //   - company_id NOT NULL y platform_role NULL  -> usuario de una empresa
    //   - company_id NULL     y platform_role PLATFORM_ADMIN -> admin de plataforma
    // Nunca ambos campos llenos ni ambos NULL.
    //
    // El platform_role actua como un "meta-rol" fuera de la jerarquia normal
    // de roles/permisos: NO se usa para @PreAuthorize (ese sigue leyendo de
    // la tabla roles). Se usa para:
    //   - identificar al PLATFORM_ADMIN en el JWT (claim platformRole)
    //   - bypass del Hibernate @Filter de multi-tenancy
    //   - habilitar endpoints /platform/** de gestion de empresas
    /** FK a companies(id). NULL solo para PLATFORM_ADMIN. */
    @Column(name = "company_id")
    private Long companyId;

    /** 'PLATFORM_ADMIN' si es admin de plataforma, NULL si es usuario de una empresa. */
    @Column(name = "platform_role", length = 50)
    private String platformRole;

    /**
     * QA Bloque PA Bug 79 (HU-PA-11 E4, 2026-05-11): timestamp de invalidacion
     * de sesion. Cuando un admin modifica los roles, status (INACTIVE/BLOCKED)
     * o permisos del usuario, se setea a NOW(). El JwtService valida que el
     * iat (issued at) del token sea POSTERIOR a este valor; si no, el token
     * se considera obsoleto y el usuario debe re-loguear para que sus permisos
     * actuales tengan efecto. Resuelve "invalidacion de cache de permisos en
     * sesion activa" sin desplegar un cache distribuido.
     */
    @Column(name = "session_invalidated_at")
    private LocalDateTime sessionInvalidatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return roles.stream()
                .flatMap(role -> {

                    Stream<GrantedAuthority> roleAuthority = Stream.of(
                            new SimpleGrantedAuthority("ROLE_" + role.getName())
                    );


                    Stream<GrantedAuthority> permissionAuthorities =
                            role.getPermissions()
                                    .stream()
                                    .map(permission -> new SimpleGrantedAuthority("PERM_" + permission.getCode()));

                    return Stream.concat(roleAuthority, permissionAuthorities);
                })
                .collect(Collectors.toSet());
    }


    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        if (lockedUntil != null && lockedUntil.isAfter(LocalDateTime.now())) {
            return false;
        }
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return this.status == Status.ACTIVE;
    }
}
