package com.example.celcoinmock.repository;

import com.example.celcoinmock.entity.PixKey;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PixKeyRepository extends JpaRepository<PixKey, Long> {
    Optional<PixKey> findByKeyValue(String keyValue);
    boolean existsByKeyValue(String keyValue);
}
