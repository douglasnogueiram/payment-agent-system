package com.example.paymentagent.agent;

import com.example.paymentagent.service.AgentPromptService;
import com.example.paymentagent.tools.PaymentTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
public class PaymentAssistant {

    private final ChatClient.Builder chatClientBuilder;
    private final AgentPromptService promptService;
    private final PaymentTools paymentTools;
    private final VectorStore vectorStore;

    public PaymentAssistant(
            ChatClient.Builder chatClientBuilder,
            AgentPromptService promptService,
            PaymentTools paymentTools,
            VectorStore vectorStore
    ) {
        this.chatClientBuilder = chatClientBuilder;
        this.promptService = promptService;
        this.paymentTools = paymentTools;
        this.vectorStore = vectorStore;
    }

    public Flux<String> chat(String conversationId, String userMessage) {
        return chatClientBuilder.build()
                .prompt()
                .system(promptService.getActivePrompt())
                .user(userMessage)
                .advisors(
                    new MessageChatMemoryAdvisor(new InMemoryChatMemory(), conversationId, 20),
                    new QuestionAnswerAdvisor(vectorStore)
                )
                .tools(paymentTools)
                .stream()
                .content();
    }
}
