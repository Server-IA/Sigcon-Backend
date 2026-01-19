package com.sigcon.backend.parametrization.parameters.domain.model;

import com.sigcon.backend.parametrization.users.domain.model.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_parameters", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "parameter_id"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserParameter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parameter_id", nullable = false)
    private Parameter parameter;

    @Column(name = "color_value", length = 7, nullable = false)
    private String colorValue; // Formato hexadecimal (ej: #FF5733)

    private LocalDateTime creationDate;

    private LocalDateTime lastUpdateDate;
}
