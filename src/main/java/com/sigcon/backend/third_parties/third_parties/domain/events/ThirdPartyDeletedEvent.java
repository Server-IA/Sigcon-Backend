package com.sigcon.backend.third_parties.third_parties.domain.events;

import lombok.Getter;

/**
 * Evento emitido cuando se elimina (soft delete) un tercero.
 */
@Getter
public class ThirdPartyDeletedEvent extends ThirdPartyEvent {
    private final String justification;

    public ThirdPartyDeletedEvent(Long thirdPartyId, String nit, String businessName, String justification) {
        super(thirdPartyId, nit, businessName);
        this.justification = justification;
    }
}
