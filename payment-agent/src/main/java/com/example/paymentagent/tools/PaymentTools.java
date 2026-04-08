package com.example.paymentagent.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.http.MediaType;

import java.util.HashMap;
import java.util.Map;

/**
 * Spring AI tools exposed to the LLM.
 * name, email and keycloakUserId are NEVER LLM parameters — they are read from
 * ToolContext, which is populated from the JWT in PaymentAssistant.chat().
 * This works correctly even when the tool runs on a non-servlet (Netty IO) thread.
 */
@Component
public class PaymentTools {

    private final RestClient bankingClient;

    public PaymentTools(@Value("${banking.service.url}") String bankingUrl) {
        this.bankingClient = RestClient.builder().baseUrl(bankingUrl).build();
    }

    /** Always returns the response body as String, even on 4xx/5xx — so the LLM sees the real error message. */
    private String post(String uri, Object body) {
        return bankingClient.post()
                .uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange((req, resp) -> resp.bodyTo(String.class));
    }

    private String get(String uri, Object... uriVars) {
        return bankingClient.get()
                .uri(uri, uriVars)
                .exchange((req, resp) -> resp.bodyTo(String.class));
    }

    @Tool(description = """
            Opens a new checking account (conta corrente) for the authenticated customer.
            Name and email are taken automatically from the authenticated session — never ask the user for them.
            Required from user: CPF (11 digits, numbers only) and a 6-digit numeric transaction password.
            Optional but recommended: phone number (format +55XXXXXXXXXXX), mother's full name,
            and birth date (format DD-MM-YYYY).
            The transaction password will be used to authorize all future sensitive operations.
            Returns the new account number and agency.
            """)
    public String openAccount(
            @ToolParam(description = "Customer's CPF (11 digits, numbers only)") String cpf,
            @ToolParam(description = "Customer's phone number, format +55XXXXXXXXXXX") String phoneNumber,
            @ToolParam(description = "Customer's mother full name") String motherName,
            @ToolParam(description = "Customer's birth date in DD-MM-YYYY format") String birthDate,
            @ToolParam(description = "6-digit numeric transaction password chosen by the customer") String transactionPassword,
            ToolContext toolContext
    ) {
        Map<String, Object> body = new HashMap<>();
        body.put("name",              toolContext.getContext().getOrDefault("name", ""));
        body.put("cpf",               cpf);
        body.put("email",             toolContext.getContext().getOrDefault("email", ""));
        body.put("phoneNumber",       phoneNumber != null ? phoneNumber : "+5511999999999");
        body.put("motherName",        motherName  != null ? motherName  : "");
        body.put("birthDate",         birthDate   != null ? birthDate   : "");
        body.put("transactionPassword", transactionPassword);
        body.put("keycloakUserId",    toolContext.getContext().getOrDefault("keycloakUserId", ""));

        return post("/api/accounts", body);
    }

    @Tool(description = """
            Returns the current balance of the authenticated customer's account.
            The account number is resolved automatically from the session — never ask the user for it.
            Requires only the 6-digit numeric transaction password.
            """)
    public String getBalance(
            @ToolParam(description = "6-digit transaction password") String transactionPassword,
            ToolContext toolContext
    ) {
        String accountNumber = (String) toolContext.getContext().getOrDefault("accountNumber", "");
        return post("/api/accounts/balance",
                Map.of("accountNumber", accountNumber, "transactionPassword", transactionPassword));
    }

    @Tool(description = """
            Returns the account statement (list of recent transactions) for the authenticated customer.
            The account number is resolved automatically from the session — never ask the user for it.
            Requires the 6-digit transaction password and number of days to look back (default 30).
            """)
    public String getStatement(
            @ToolParam(description = "6-digit transaction password") String transactionPassword,
            @ToolParam(description = "Number of days to look back (e.g. 30)") int days,
            ToolContext toolContext
    ) {
        String accountNumber = (String) toolContext.getContext().getOrDefault("accountNumber", "");
        return post("/api/accounts/statement",
                Map.of("accountNumber", accountNumber, "transactionPassword", transactionPassword, "days", days));
    }

    @Tool(description = """
            Looks up a PIX key in the DICT (Diretório de Identificadores de Transações do PIX)
            and returns the recipient's name, document (masked), bank (ISPB), account details and key type.
            Call this tool IMMEDIATELY and AUTOMATICALLY as soon as the user provides any PIX key
            (CPF, phone, email, or random key) — do NOT ask for confirmation before calling it.
            After calling, present all returned recipient details clearly to the user,
            then ask for the transfer amount if not yet provided.
            Do NOT execute the payment (payPix) if the key is not found.
            """)
    public String lookupPixKey(
            @ToolParam(description = "PIX key: CPF (numbers only), phone (+55...), email, or random EVP key") String pixKey
    ) {
        return get("/api/payments/pix/lookup/{key}", pixKey);
    }

    @Tool(description = """
            Executes a PIX transfer from the authenticated customer's account.
            The account number is resolved automatically from the session — never ask the user for it.
            IMPORTANT: You MUST call lookupPixKey first and show the recipient details to the user.
            Only call this tool after the user has explicitly confirmed the recipient and amount.
            Requires: 6-digit transaction password, destination PIX key, amount in BRL, optional description.
            The transfer is submitted to Celcoin and confirmed asynchronously via webhook.
            """)
    public String payPix(
            @ToolParam(description = "6-digit transaction password") String transactionPassword,
            @ToolParam(description = "Destination PIX key (CPF, phone, email or random key)") String pixKey,
            @ToolParam(description = "Amount in BRL (e.g. 150.00)") double amount,
            @ToolParam(description = "Optional payment description") String description,
            ToolContext toolContext
    ) {
        String accountNumber = (String) toolContext.getContext().getOrDefault("accountNumber", "");
        Map<String, Object> body = new HashMap<>();
        body.put("accountNumber", accountNumber);
        body.put("transactionPassword", transactionPassword);
        body.put("pixKey", pixKey);
        body.put("amount", amount);
        body.put("description", description != null ? description : "");
        return post("/api/payments/pix", body);
    }

    @Tool(description = """
            Generates a PDF bank statement for the authenticated customer and returns a download link.
            The account number is resolved automatically from the session — never ask the user for it.
            Requires: 6-digit transaction password and number of days to look back (default 30).
            The statement is formatted according to Brazilian Central Bank (BACEN) regulations
            (Resoluções CMN 3.919/2010 and 4.282/2013) and includes account holder info,
            period summary (initial balance, credits, debits, final balance) and full transaction list.
            When this tool succeeds, include in your reply the EXACT markdown link from the
            'downloadLink' field so the user can click to download the PDF.
            """)
    public String exportStatementPdf(
            @ToolParam(description = "6-digit transaction password") String transactionPassword,
            @ToolParam(description = "Number of days to look back (e.g. 30)") int days,
            ToolContext toolContext
    ) {
        String accountNumber = (String) toolContext.getContext().getOrDefault("accountNumber", "");
        Map<String, Object> reqBody = new HashMap<>();
        reqBody.put("accountNumber",       accountNumber);
        reqBody.put("transactionPassword", transactionPassword);
        reqBody.put("days",                days);

        String raw = post("/api/statements/pdf", reqBody);

        // Parse JSON to extract fields; on error the raw message (e.g. {"error":"..."}) is returned to the LLM
        try {
            com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> response = om.readValue(raw, java.util.Map.class);
            Object downloadUrl = response.get("downloadUrl");
            Object txCount     = response.get("transactions");
            if (downloadUrl == null) return raw; // propagate error body to LLM
            return String.format(
                    "Extrato dos últimos %d dias gerado com sucesso. %s lançamento(s) encontrado(s).%n" +
                    "downloadLink: [Baixar Extrato PDF](%s)",
                    days, txCount, downloadUrl);
        } catch (Exception e) {
            return raw;
        }
    }

    @Tool(description = """
            Decodes a Pix QR Code (EMV string) and returns the payment details:
            recipient name, Pix key, amount, QR code type (STATIC or DYNAMIC), and expiration (if dynamic).
            Call this tool whenever the user provides a QR Code string (EMV / Pix Copia e Cola).
            After decoding, show all details clearly to the user and ask for explicit confirmation before paying.
            If the amount is zero (static QR without fixed value), ask the user how much they want to send.
            Do NOT execute payment without user confirmation.
            After confirmation, use payPix with the returned pixKey and amount.
            """)
    public String decodeQrCode(
            @ToolParam(description = "EMV string of the Pix QR Code (Pix Copia e Cola)") String emv
    ) {
        return post("/api/payments/pix/qrcode/decode", Map.of("emv", emv));
    }

    @Tool(description = """
            Pays a boleto (Brazilian payment slip / bill) from the authenticated customer's account.
            The account number is resolved automatically from the session — never ask the user for it.
            Requires: 6-digit transaction password and the boleto barcode (linha digitável).
            The amount is extracted from the boleto barcode.
            Always confirm the barcode and amount with the user before calling this tool.
            """)
    public String payBoleto(
            @ToolParam(description = "6-digit transaction password") String transactionPassword,
            @ToolParam(description = "Boleto barcode / linha digitável (numbers only)") String boletoCode,
            ToolContext toolContext
    ) {
        String accountNumber = (String) toolContext.getContext().getOrDefault("accountNumber", "");
        return post("/api/payments/boleto",
                Map.of("accountNumber", accountNumber, "transactionPassword", transactionPassword, "boletoCode", boletoCode));
    }
}
