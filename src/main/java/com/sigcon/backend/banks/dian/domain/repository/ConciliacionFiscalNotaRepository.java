package com.sigcon.backend.banks.dian.domain.repository;

import com.sigcon.backend.banks.dian.domain.model.ConciliacionFiscalNota;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConciliacionFiscalNotaRepository extends JpaRepository<ConciliacionFiscalNota, Long> {
    List<ConciliacionFiscalNota> findByAnoFiscal(Integer anoFiscal);
    Optional<ConciliacionFiscalNota> findByAnoFiscalAndPartidaKey(Integer anoFiscal, String partidaKey);
}
