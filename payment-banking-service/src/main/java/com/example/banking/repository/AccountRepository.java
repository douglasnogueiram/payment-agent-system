package com.example.banking.repository;

import com.example.banking.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {
    Optional<Account> findByAccountNumber(String accountNumber);
    Optional<Account> findByCpf(String cpf);
    boolean existsByCpf(String cpf);
    boolean existsByKeycloakUserId(String keycloakUserId);
    Optional<Account> findByKeycloakUserId(String keycloakUserId);
    Optional<Account> findByEmail(String email);
}
