package com.sigcon.backend.third_parties.third_parties.domain.events;

/**
 * Evento emitido cuando se crea un nuevo tercero.
 */
public class ThirdPartyCreatedEvent extends ThirdPartyEvent {
    public ThirdPartyCreatedEvent(Long thirdPartyId, String nit, String businessName) {
        super(thirdPartyId, nit, businessName);
    }
}
