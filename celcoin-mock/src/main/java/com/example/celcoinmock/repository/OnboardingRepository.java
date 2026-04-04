package com.example.celcoinmock.repository;

import com.example.celcoinmock.entity.OnboardingRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface OnboardingRepository extends JpaRepository<OnboardingRecord, String> {
    Optional<OnboardingRecord> findByClientCode(String clientCode);
    Optional<OnboardingRecord> findByDocumentNumber(String documentNumber);
    List<OnboardingRecord> findByStatus(OnboardingRecord.OnboardingStatus status);
    boolean existsByDocumentNumber(String documentNumber);
    boolean existsByClientCode(String clientCode);
}
