package com.sigcon.backend.parametrization.notifications.application;

import com.sigcon.backend.parametrization.notifications.domain.model.Notification.Severity;
import lombok.*;

/**
 * Request interno para que un service emisor publique una notificacion (USER_EVENT o ROL_EVENT).
 * No se expone al exterior; la usan los services del dominio cuando disparan eventos.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublishEventRequest {
    /** Empresa destino. */
    private Long companyId;
    /** Codigo del evento (ej. TEMP_PERMISSION_ASSIGNED). Debe existir en notification_event_catalog. */
    private String eventKey;
    /** Titulo corto (max 160 chars). */
    private String title;
    /** Cuerpo descriptivo. */
    private String body;
    /** Ruta de la app a la que lleva el click (HU-PA-23). */
    private String actionUrl;
    /** ID del registro origen (para dedup HU-PA-25 y deep-link). */
    private Long sourceId;
    /** Tipo de fuente (e.g. "JournalEntry", "TemporaryPermission"). */
    private String sourceType;
    /** Severidad. */
    @Builder.Default
    private Severity severity = Severity.INFO;
}
