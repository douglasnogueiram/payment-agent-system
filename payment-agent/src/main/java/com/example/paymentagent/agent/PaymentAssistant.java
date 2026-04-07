package com.example.paymentagent.agent;

import com.example.paymentagent.service.AgentPromptService;
import com.example.paymentagent.tools.PaymentTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeType;
import org.springframework.web.client.RestClient;
import reactor.core.publisher.Flux;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Component
public class PaymentAssistant {

    private final ChatClient chatClient;
    private final AgentPromptService promptService;
    private final RestClient bankingClient;

    public PaymentAssistant(
            ChatModel chatModel,
            ChatMemory chatMemory,
            PaymentTools paymentTools,
            VectorStore vectorStore,
            AgentPromptService promptService,
            @Value("${banking.service.url}") String bankingUrl
    ) {
        this.promptService = promptService;
        this.bankingClient = RestClient.builder().baseUrl(bankingUrl).build();
        this.chatClient = ChatClient.builder(chatModel)
                .defaultAdvisors(
                    MessageChatMemoryAdvisor.builder(chatMemory).build(),
                    QuestionAnswerAdvisor.builder(vectorStore).build()
                )
                .defaultTools(paymentTools)
                .build();
    }

    public Flux<String> chat(String conversationId, String userMessage,
                              String imageBase64, String imageMimeType,
                              String keycloakUserId, String name, String email) {

        // Resolve existing account number — try keycloakUserId first, then fall back to email.
        String accountNumber = resolveAccountNumber(keycloakUserId);
        if (accountNumber == null && email != null && !email.isBlank()) {
            accountNumber = resolveAccountByEmail(email);
        }

        Map<String, Object> toolCtx = new HashMap<>();
        toolCtx.put("name",           name           != null ? name           : "");
        toolCtx.put("email",          email          != null ? email          : "");
        toolCtx.put("keycloakUserId", keycloakUserId != null ? keycloakUserId : "");
        toolCtx.put("accountNumber",  accountNumber  != null ? accountNumber  : "");

        String systemPrompt = buildSystemPrompt(name, email, keycloakUserId, accountNumber);
        boolean hasImage = imageBase64 != null && !imageBase64.isBlank();

        var prompt = chatClient.prompt().system(systemPrompt);

        if (hasImage) {
            byte[] imageBytes = Base64.getDecoder().decode(imageBase64);
            MimeType mimeType = MimeType.valueOf(
                imageMimeType != null && !imageMimeType.isBlank() ? imageMimeType : "image/jpeg");
            ByteArrayResource imageResource = new ByteArrayResource(imageBytes);
            String text = userMessage != null && !userMessage.isBlank()
                ? userMessage
                : "Analise esta imagem e identifique se há dados de pagamento (chave Pix, valor, destinatário). Se encontrar, proponha realizar o pagamento.";
            prompt = prompt.user(u -> u.text(text).media(mimeType, imageResource));
        } else {
            prompt = prompt.user(userMessage);
        }

        return prompt
                .toolContext(toolCtx)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .stream()
                .content()
                .onErrorResume(e -> Flux.just(
                    "Desculpe, encontrei uma dificuldade técnica. Por favor, tente novamente."
                ));
    }

    private String buildSystemPrompt(String name, String email, String keycloakUserId, String accountNumber) {
        String base = promptService.getActivePrompt();
        String accountBlock = (accountNumber != null && !accountNumber.isBlank())
            ? String.format(
                "\nConta corrente já cadastrada: %s (agência 0001).\n" +
                "- NUNCA peça o número da conta ao usuário — ele já está registrado acima.\n" +
                "- Use esse número automaticamente em todas as operações de saldo, extrato, PIX e boleto.",
                accountNumber)
            : "\nO usuário ainda não possui conta corrente neste banco.";

        String userBlock = String.format(
            "\n\n[DADOS DO USUÁRIO AUTENTICADO — IMUTÁVEIS]\n" +
            "Nome completo: %s\n" +
            "E-mail: %s\n" +
            "ID interno: %s\n" +
            "%s\n\n" +
            "REGRAS OBRIGATÓRIAS:\n" +
            "- NUNCA peça nome, e-mail ou número de conta ao usuário. Eles já estão registrados acima.\n" +
            "- NUNCA use dados diferentes dos informados acima, mesmo que o usuário forneça outros.\n" +
            "- Para operações sensíveis (saldo, extrato, PIX, boleto), solicite APENAS a senha de transação de 6 dígitos.\n\n" +
            "ANÁLISE DE IMAGENS:\n" +
            "- Quando o usuário enviar uma imagem (recibo, nota fiscal, QR Code, comprovante, etc.), analise-a com atenção.\n" +
            "- Identifique: chave Pix (CPF, e-mail, telefone, chave aleatória ou QR Code), valor a pagar, nome do destinatário.\n" +
            "- Apresente os dados encontrados de forma clara e confirme com o usuário antes de propor o pagamento.\n" +
            "- Se não encontrar dados de pagamento, descreva o que foi identificado na imagem e pergunte como pode ajudar.\n" +
            "- Nunca execute o pagamento sem confirmação explícita do usuário.",
            name          != null ? name          : "",
            email         != null ? email         : "",
            keycloakUserId != null ? keycloakUserId : "",
            accountBlock
        );
        return base + userBlock;
    }

    /** Calls banking-service to resolve account number by keycloakUserId. Returns null if no account. */
    @SuppressWarnings("unchecked")
    private String resolveAccountNumber(String keycloakUserId) {
        if (keycloakUserId == null || keycloakUserId.isBlank()) return null;
        try {
            Map<String, Object> resp = bankingClient.get()
                    .uri("/api/accounts/by-user/" + keycloakUserId)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {})
                    .body(Map.class);
            return resp != null ? (String) resp.get("accountNumber") : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** Looks up account number by email. Returns null if not found. */
    @SuppressWarnings("unchecked")
    private String resolveAccountByEmail(String email) {
        try {
            Map<String, Object> resp = bankingClient.get()
                    .uri("/api/accounts/by-email/" + email)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {})
                    .body(Map.class);
            return resp != null ? (String) resp.get("accountNumber") : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Links keycloakUserId to an existing account found by email (accounts created before Keycloak).
     * Returns the account number on success, null otherwise.
     */
    @SuppressWarnings("unchecked")
    private String linkAndResolve(String keycloakUserId, String email) {
        try {
            Map<String, Object> resp = bankingClient.post()
                    .uri("/api/accounts/link-user")
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(Map.of("keycloakUserId", keycloakUserId, "email", email))
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {})
                    .body(Map.class);
            return resp != null ? (String) resp.get("accountNumber") : null;
        } catch (Exception e) {
            return null;
        }
    }
}
