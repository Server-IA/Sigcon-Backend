package com.sigcon.backend.assets.niif_alerts.application;

import lombok.*;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VerifyNiifRequest {

    private List<Long> assetIds;

}