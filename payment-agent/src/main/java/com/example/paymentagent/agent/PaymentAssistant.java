package com.example.paymentagent.agent;

import com.example.paymentagent.service.AgentPromptService;
import com.example.paymentagent.tools.PaymentTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
public class PaymentAssistant {

    private final ChatClient chatClient;
    private final AgentPromptService promptService;

    public PaymentAssistant(
            ChatModel chatModel,
            ChatMemory chatMemory,
            PaymentTools paymentTools,
            VectorStore vectorStore,
            AgentPromptService promptService
    ) {
        this.promptService = promptService;
        this.chatClient = ChatClient.builder(chatModel)
                .defaultAdvisors(
                    MessageChatMemoryAdvisor.builder(chatMemory).build(),
                    QuestionAnswerAdvisor.builder(vectorStore).build()
                )
                .defaultTools(paymentTools)
                .build();
    }

    public Flux<String> chat(String conversationId, String userMessage) {
        return chatClient.prompt()
                .system(promptService.getActivePrompt())
                .user(userMessage)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .stream()
                .content()
                .onErrorResume(e -> Flux.just(
                    "Desculpe, encontrei uma dificuldade técnica. Por favor, tente novamente."
                ));
    }
}
