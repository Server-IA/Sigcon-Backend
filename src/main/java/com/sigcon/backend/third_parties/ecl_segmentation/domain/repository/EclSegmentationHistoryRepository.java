package com.sigcon.backend.third_parties.ecl_segmentation.domain.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.sigcon.backend.third_parties.ecl_segmentation.domain.model.EclSegmentationHistory;

@Repository
public interface EclSegmentationHistoryRepository extends JpaRepository<EclSegmentationHistory, Long> {

    //obtener todo el historico de un cliente ordenado de manera descendente por fecha de cambio
    List<EclSegmentationHistory> findByClientIdOrderByChangeDateDesc(Long clientId);

}
