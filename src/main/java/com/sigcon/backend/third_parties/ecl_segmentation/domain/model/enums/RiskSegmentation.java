package com.sigcon.backend.third_parties.ecl_segmentation.domain.model.enums;

public enum RiskSegmentation {

    LOW,              // Riesgo Bajo: 0-30 dias mora
    MEDIUM,           // Riesgo Medio: 31-60 dias mora 
    HIGH,            //  Riesgo Alto: >60 dias mora
    PENDING          // Fallback: Datos AR no disponibles o cliente sin historial 

}
