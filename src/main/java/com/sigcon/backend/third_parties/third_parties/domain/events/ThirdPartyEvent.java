package com.sigcon.backend.third_parties.third_parties.domain.events;

import java.time.LocalDateTime;
import lombok.Getter;

/**
 * Evento base para operaciones sobre terceros.
 * Otros modulos pueden suscribirse via @EventListener.
 */
@Getter
public abstract class ThirdPartyEvent {
    private final Long thirdPartyId;
    private final String nit;
    private final String businessName;
    private final LocalDateTime timestamp;

    protected ThirdPartyEvent(Long thirdPartyId, String nit, String businessName) {
        this.thirdPartyId = thirdPartyId;
        this.nit = nit;
        this.businessName = businessName;
        this.timestamp = LocalDateTime.now();
    }
}
