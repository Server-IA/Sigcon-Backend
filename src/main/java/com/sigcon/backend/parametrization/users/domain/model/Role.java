package com.sigcon.backend.parametrization.users.domain.model;

import com.sigcon.backend.parametrization.users.domain.model.enums.Status;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Where;

@Entity
@Table(name = "roles")
@SQLDelete(sql = "UPDATE roles SET deleted_at = NOW() WHERE id = ?")
@Where(clause = "deleted_at IS NULL")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Role {

    /**
     * QA Bloque PA Bug 4 (HU-PA-04 E3, 2026-05-09): nombres reservados para
     * roles predefinidos del sistema. Cada empresa tiene su propio rol con
     * uno de estos nombres (clon hecho por V9-ZZZY o RoleService al crear
     * empresa). NO se valida unicidad global sobre estos nombres porque
     * cada tenant tiene el suyo.
     */
    public static final Set<String> PREDEFINED_NAMES = new HashSet<>(Arrays.asList(
            "ADMIN_EMPRESA", "CONTADOR", "AUXILIAR_CONTABLE",
            "TESORERO", "AUDITOR", "OPERADOR_NOMINA"));

    /**
     * Roles globales del sistema (sin company_id). Solo PLATFORM_ADMIN puede
     * gestionarlos.
     */
    public static final Set<String> SYSTEM_GLOBAL_NAMES = new HashSet<>(Arrays.asList(
            "PLATFORM_ADMIN", "ADMIN", "USER"));

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    /**
     * QA Bloque PA Bug 2 (HU-PA-03 E1, 2026-05-09): descripcion textual visible
     * en el listado y en el modal de edicion. Opcional.
     */
    @Column(name = "description", length = 500, nullable = true)
    private String description;

    /**
     * QA Bloque PA Bug 4 (HU-PA-04 E3, 2026-05-09): id de la empresa duenia
     * del rol. NULL = rol global del sistema (PLATFORM_ADMIN, ADMIN, USER).
     * NOT NULL = rol del tenant. La unicidad de nombre se valida con UNIQUE
     * compuesto (company_id, LOWER(name)).
     */
    @Column(name = "company_id", nullable = true)
    private Long companyId;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "roles_permissions",
            uniqueConstraints = @UniqueConstraint(columnNames = {"role_id", "permission_id"}),
            joinColumns = @JoinColumn(name = "role_id"),
            inverseJoinColumns = @JoinColumn(name = "permission_id")
    )
    private Set<Permission> permissions;

    @Enumerated(EnumType.STRING)
    private Status status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at", nullable = true)
    private LocalDateTime deletedAt;

    /**
     * QA Bloque PA Bug 9 (HU-PA-05 E4, 2026-05-09): optimistic locking. Hibernate
     * incrementa este campo en cada UPDATE. Cuando dos administradores editan
     * el mismo rol simultaneamente, el segundo guardado dispara
     * ObjectOptimisticLockingFailureException que el handler global traduce a
     * HTTP 409 con el mensaje "Este rol fue modificado por otro usuario...".
     */
    @jakarta.persistence.Version
    @Column(name = "version", nullable = false,
            columnDefinition = "BIGINT NOT NULL DEFAULT 0")
    @Builder.Default
    private Long version = 0L;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    /** HU-PA-03 E1: indica si el rol es uno de los predefinidos del sistema. */
    @Transient
    public boolean isPredefined() {
        return name != null && PREDEFINED_NAMES.contains(name.toUpperCase());
    }

    /** HU-PA-03 E1: indica si el rol es global (cross-tenant). */
    @Transient
    public boolean isGlobal() {
        return companyId == null;
    }
}
