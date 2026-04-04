package com.example.paymentagent.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.http.MediaType;
import java.util.Map;

/**
 * Spring AI tools exposed to the LLM.
 * Each method calls payment-banking-service, which proxies to Celcoin (mock or real).
 * Critical operations require the transaction password, which the agent asks the user for
 * and passes through — it is never stored by the agent.
 */
@Component
public class PaymentTools {

    private final RestClient bankingClient;

    public PaymentTools(@Value("${banking.service.url}") String bankingUrl) {
        this.bankingClient = RestClient.builder().baseUrl(bankingUrl).build();
    }

    @Tool(description = """
            Opens a new checking account (conta corrente) for a customer via Celcoin.
            Required: full name, CPF (11 digits), email, and a transaction password (4-6 digits).
            Optional but recommended: phone number (format +55XXXXXXXXXXX), mother's full name,
            and birth date (format DD-MM-YYYY).
            The transaction password will be used to authorize future sensitive operations.
            Returns the new account number and agency.
            """)
    public String openAccount(
            @ToolParam(description = "Customer's full name") String name,
            @ToolParam(description = "Customer's CPF (11 digits, numbers only)") String cpf,
            @ToolParam(description = "Customer's email address") String email,
            @ToolParam(description = "Customer's phone number, format +55XXXXXXXXXXX") String phoneNumber,
            @ToolParam(description = "Customer's mother full name") String motherName,
            @ToolParam(description = "Customer's birth date in DD-MM-YYYY format") String birthDate,
            @ToolParam(description = "Transaction password chosen by the customer (4 to 6 digits)") String transactionPassword
    ) {
        var body = Map.of(
            "name", name,
            "cpf", cpf,
            "email", email,
            "phoneNumber", phoneNumber != null ? phoneNumber : "+5511999999999",
            "motherName", motherName != null ? motherName : "",
            "birthDate", birthDate != null ? birthDate : "",
            "transactionPassword", transactionPassword
        );
        return bankingClient.post()
                .uri("/api/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);
    }

    @Tool(description = """
            Returns the current balance of an account.
            Requires the account number and the transaction password.
            """)
    public String getBalance(
            @ToolParam(description = "Account number") String accountNumber,
            @ToolParam(description = "Transaction password") String transactionPassword
    ) {
        var body = Map.of("accountNumber", accountNumber, "transactionPassword", transactionPassword);
        return bankingClient.post()
                .uri("/api/accounts/balance")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);
    }

    @Tool(description = """
            Returns the account statement (list of recent transactions).
            Requires the account number, transaction password, and number of days to look back (default 30).
            """)
    public String getStatement(
            @ToolParam(description = "Account number") String accountNumber,
            @ToolParam(description = "Transaction password") String transactionPassword,
            @ToolParam(description = "Number of days to look back (e.g. 30)") int days
    ) {
        var body = Map.of("accountNumber", accountNumber, "transactionPassword", transactionPassword, "days", days);
        return bankingClient.post()
                .uri("/api/accounts/statement")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);
    }

    @Tool(description = """
            Performs a PIX transfer.
            Requires: source account number, transaction password, destination PIX key
            (CPF, phone number, email, or random key), amount in BRL, and an optional description.
            Always confirm the destination key and amount with the user before calling this tool.
            """)
    public String payPix(
            @ToolParam(description = "Source account number") String accountNumber,
            @ToolParam(description = "Transaction password") String transactionPassword,
            @ToolParam(description = "Destination PIX key (CPF, phone, email or random key)") String pixKey,
            @ToolParam(description = "Amount in BRL (e.g. 150.00)") double amount,
            @ToolParam(description = "Optional payment description") String description
    ) {
        var body = Map.of(
            "accountNumber", accountNumber,
            "transactionPassword", transactionPassword,
            "pixKey", pixKey,
            "amount", amount,
            "description", description != null ? description : ""
        );
        return bankingClient.post()
                .uri("/api/payments/pix")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);
    }

    @Tool(description = """
            Pays a boleto (Brazilian payment slip / bill).
            Requires: source account number, transaction password, and the boleto barcode (linha digitável).
            The amount is extracted from the boleto barcode.
            Always confirm the barcode and amount with the user before calling this tool.
            """)
    public String payBoleto(
            @ToolParam(description = "Source account number") String accountNumber,
            @ToolParam(description = "Transaction password") String transactionPassword,
            @ToolParam(description = "Boleto barcode / linha digitável (numbers only)") String boletoCode
    ) {
        var body = Map.of(
            "accountNumber", accountNumber,
            "transactionPassword", transactionPassword,
            "boletoCode", boletoCode
        );
        return bankingClient.post()
                .uri("/api/payments/boleto")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);
    }
}
