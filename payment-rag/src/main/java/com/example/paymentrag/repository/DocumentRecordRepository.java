package com.example.paymentrag.repository;

import com.example.paymentrag.domain.DocumentRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentRecordRepository extends JpaRepository<DocumentRecord, UUID> {

    Optional<DocumentRecord> findByName(String name);

    List<DocumentRecord> findByStatusOrderByUploadedAtDesc(String status);
}
