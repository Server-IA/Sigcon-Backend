package com.sigcon.backend.assets.niif_alerts.application;

import lombok.Data;

@Data
public class NiifCorrectionRequest {

    private Long assetId;

    private String correctionType;

    private String justification;

    private String previousValue;

    private String newValue;

}