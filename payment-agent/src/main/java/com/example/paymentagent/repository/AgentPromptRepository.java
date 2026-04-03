package com.example.paymentagent.repository;

import com.example.paymentagent.entity.AgentPrompt;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface AgentPromptRepository extends JpaRepository<AgentPrompt, Long> {
    Optional<AgentPrompt> findByActiveTrue();
    List<AgentPrompt> findAllByOrderByIdDesc();
}
