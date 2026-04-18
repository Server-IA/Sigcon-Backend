package com.sigcon.backend.third_parties.third_parties.domain.events;

import java.util.Map;
import lombok.Getter;

/**
 * Evento emitido cuando se actualiza un tercero.
 */
@Getter
public class ThirdPartyUpdatedEvent extends ThirdPartyEvent {
    private final Map<String, String> changedFields;

    public ThirdPartyUpdatedEvent(Long thirdPartyId, String nit, String businessName, Map<String, String> changedFields) {
        super(thirdPartyId, nit, businessName);
        this.changedFields = changedFields;
    }
}
