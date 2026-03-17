package com.sigcon.backend.assets.assets.application;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkAssetsUploadResponse {
    private int totalProcessed;
    private int created;
}
