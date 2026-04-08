package com.example.paymentagent.service;

import com.example.paymentagent.entity.AgentPrompt;
import com.example.paymentagent.repository.AgentPromptRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class AgentPromptService {

    private static final String DEFAULT_PROMPT = """
            Você é um assistente bancário virtual inteligente e seguro do PaymentAgent Bank.
            Você pode ajudar clientes a:
            - Abrir novas contas correntes
            - Consultar saldo e extrato (requer senha de transação)
            - Realizar pagamentos via Pix (requer senha de transação)
            - Pagar boletos (requer senha de transação)

            FLUXO OBRIGATÓRIO PARA PAGAMENTO VIA PIX:
            1. Assim que o usuário fornecer uma chave Pix (CPF, e-mail, telefone, chave aleatória ou QR Code),
               chame IMEDIATAMENTE a tool lookupPixKey com essa chave — sem pedir confirmações antes.
            2. Apresente os dados retornados (nome, banco, tipo de chave) de forma clara e pergunte o valor.
            3. Quando o usuário informar o valor, peça a senha de transação de 6 dígitos.
            4. Só após receber a senha, chame payPix para executar o pagamento.
            NUNCA pule o lookupPixKey. NUNCA execute payPix sem antes chamar lookupPixKey.

            REGRAS DE SEGURANÇA:
            - NUNCA revele a senha de transação de nenhum cliente.
            - Para qualquer operação que exija senha, solicite-a de forma clara ao cliente.
            - Confirme sempre os dados antes de executar pagamentos.
            - Em caso de dúvida sobre identidade, não prossiga com transações críticas.

            Seja cordial, claro e objetivo. Use linguagem simples e profissional.
            Forneça confirmações claras após cada operação concluída.
            """;

    private final AgentPromptRepository repository;
    private final AtomicReference<String> cache = new AtomicReference<>();

    public AgentPromptService(AgentPromptRepository repository) {
        this.repository = repository;
    }

    @PostConstruct
    public void init() {
        if (repository.count() == 0) {
            AgentPrompt prompt = new AgentPrompt();
            prompt.setContent(DEFAULT_PROMPT);
            prompt.setDescription("Prompt inicial do agente de pagamentos");
            prompt.setActive(true);
            repository.save(prompt);
        }
        repository.findByActiveTrue().ifPresent(p -> cache.set(p.getContent()));
    }

    public String getActivePrompt() {
        return cache.get();
    }

    @Transactional
    public AgentPrompt saveNewVersion(String content, String description) {
        repository.findByActiveTrue().ifPresent(p -> { p.setActive(false); repository.save(p); });
        AgentPrompt prompt = new AgentPrompt();
        prompt.setContent(content);
        prompt.setDescription(description);
        prompt.setActive(true);
        AgentPrompt saved = repository.save(prompt);
        cache.set(content);
        return saved;
    }

    @Transactional
    public AgentPrompt activate(Long id) {
        repository.findByActiveTrue().ifPresent(p -> { p.setActive(false); repository.save(p); });
        AgentPrompt prompt = repository.findById(id).orElseThrow();
        prompt.setActive(true);
        AgentPrompt saved = repository.save(prompt);
        cache.set(saved.getContent());
        return saved;
    }

    public AgentPrompt getActive() {
        return repository.findByActiveTrue().orElseThrow();
    }

    public List<AgentPrompt> getHistory() {
        return repository.findAllByOrderByIdDesc();
    }
}
