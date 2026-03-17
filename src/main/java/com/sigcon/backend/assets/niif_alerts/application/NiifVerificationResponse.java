package com.sigcon.backend.assets.niif_alerts.application;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class NiifVerificationResponse {

    private Long assetId;

    private String result;

    private List<String> alerts;

}