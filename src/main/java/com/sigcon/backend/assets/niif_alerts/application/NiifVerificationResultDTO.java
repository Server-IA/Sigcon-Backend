package com.sigcon.backend.assets.niif_alerts.application;

import lombok.*;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NiifVerificationResultDTO {

    private Long assetId;

    private String assetName;

    private String result;

    private List<String> alerts;

}