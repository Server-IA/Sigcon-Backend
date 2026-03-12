package com.sigcon.backend.third_parties.third_parties.domain.repository;

import com.sigcon.backend.third_parties.third_parties.domain.model.ThirdContact;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ThirdContactRepository extends JpaRepository<ThirdContact, Long> {
}