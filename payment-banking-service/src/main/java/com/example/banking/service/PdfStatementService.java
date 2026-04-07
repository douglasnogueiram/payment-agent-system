package com.example.banking.service;

import com.example.banking.entity.Account;
import com.example.banking.entity.Transaction;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import com.lowagie.text.pdf.draw.LineSeparator;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Generates BACEN-compliant PDF bank statements.
 *
 * Regulatory basis:
 *  - Resolução CMN 3.919/2010 — obrigações de informação ao cliente
 *  - Resolução CMN 4.282/2013 — identificação da instituição e do correntista
 *  - LGPD (Lei 13.709/2018)   — CPF parcialmente mascarado
 */
@Service
public class PdfStatementService {

    private static final String BANK_NAME    = "Payment Bank S.A.";
    private static final String BANK_CNPJ    = "00.000.000/0001-91";
    private static final String BANK_ISPB    = "00000000";
    private static final String BANK_ADDRESS = "Av. Paulista, 1000 — Bela Vista — São Paulo/SP — CEP 01310-100";

    private static final ZoneId           BR_ZONE      = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter DATE_FMT     = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    // Color palette
    private static final Color NAVY      = new Color(0x1A, 0x3A, 0x5C);
    private static final Color NAVY_LIGHT = new Color(0x2A, 0x5A, 0x8C);
    private static final Color GRAY_BG   = new Color(0xF5, 0xF7, 0xFA);
    private static final Color GRAY_LINE = new Color(0xDD, 0xE0, 0xE5);
    private static final Color GREEN     = new Color(0x1A, 0x7A, 0x40);
    private static final Color RED       = new Color(0xB0, 0x20, 0x20);
    private static final Color WHITE     = Color.WHITE;
    private static final Color TEXT      = new Color(0x1A, 0x1A, 0x2E);
    private static final Color MUTED     = new Color(0x60, 0x70, 0x80);

    public byte[] generate(Account account, List<Transaction> transactions, int days) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 40, 40, 40, 50);
            PdfWriter writer = PdfWriter.getInstance(doc, baos);
            addPageEvents(writer);
            doc.open();

            ZonedDateTime now    = ZonedDateTime.now(BR_ZONE);
            ZonedDateTime since  = now.minusDays(days);
            String authCode      = UUID.randomUUID().toString().toUpperCase().replace("-", "").substring(0, 16);

            addHeader(doc, now);
            addDocumentTitle(doc);
            addAccountInfo(doc, account, since, now);
            doc.add(Chunk.NEWLINE);
            addSummaryBox(doc, account, transactions);
            doc.add(Chunk.NEWLINE);
            addTransactionsTable(doc, transactions);
            doc.add(Chunk.NEWLINE);
            addFooter(doc, now, authCode);

            doc.close();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Erro ao gerar extrato PDF", e);
        }
    }

    // ── Header ────────────────────────────────────────────────────────────────

    private void addHeader(Document doc, ZonedDateTime now) throws DocumentException {
        PdfPTable header = new PdfPTable(2);
        header.setWidthPercentage(100);
        header.setWidths(new float[]{3f, 2f});
        header.setSpacingAfter(4);

        // Bank identity cell
        Phrase bankPhrase = new Phrase();
        bankPhrase.add(new Chunk(BANK_NAME + "\n",
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, WHITE)));
        bankPhrase.add(new Chunk("CNPJ: " + BANK_CNPJ + "   |   ISPB: " + BANK_ISPB + "\n",
                FontFactory.getFont(FontFactory.HELVETICA, 8, new Color(0xBB, 0xCC, 0xDD))));
        bankPhrase.add(new Chunk(BANK_ADDRESS,
                FontFactory.getFont(FontFactory.HELVETICA, 7, new Color(0xBB, 0xCC, 0xDD))));

        PdfPCell bankCell = new PdfPCell(bankPhrase);
        bankCell.setBackgroundColor(NAVY);
        bankCell.setPadding(12);
        bankCell.setBorder(Rectangle.NO_BORDER);
        header.addCell(bankCell);

        // Date/time cell
        Phrase datePhrase = new Phrase();
        datePhrase.add(new Chunk("Gerado em\n",
                FontFactory.getFont(FontFactory.HELVETICA, 8, new Color(0xBB, 0xCC, 0xDD))));
        datePhrase.add(new Chunk(DATETIME_FMT.format(now),
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, WHITE)));

        PdfPCell dateCell = new PdfPCell(datePhrase);
        dateCell.setBackgroundColor(NAVY_LIGHT);
        dateCell.setPadding(12);
        dateCell.setBorder(Rectangle.NO_BORDER);
        dateCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        header.addCell(dateCell);

        doc.add(header);
    }

    // ── Document title ────────────────────────────────────────────────────────

    private void addDocumentTitle(Document doc) throws DocumentException {
        PdfPTable title = new PdfPTable(1);
        title.setWidthPercentage(100);
        title.setSpacingAfter(8);

        PdfPCell cell = new PdfPCell(new Phrase(
                "EXTRATO DE CONTA CORRENTE",
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13, NAVY)));
        cell.setBackgroundColor(GRAY_BG);
        cell.setPadding(8);
        cell.setPaddingLeft(12);
        cell.setBorderColor(NAVY);
        cell.setBorderWidthBottom(2);
        cell.setBorderWidthTop(0);
        cell.setBorderWidthLeft(0);
        cell.setBorderWidthRight(0);
        title.addCell(cell);

        doc.add(title);
    }

    // ── Account info ──────────────────────────────────────────────────────────

    private void addAccountInfo(Document doc, Account account,
                                ZonedDateTime since, ZonedDateTime now) throws DocumentException {
        PdfPTable info = new PdfPTable(4);
        info.setWidthPercentage(100);
        info.setWidths(new float[]{2.5f, 2.5f, 1.5f, 1.5f});
        info.setSpacingAfter(4);

        addInfoCell(info, "Titular",       account.getName());
        addInfoCell(info, "CPF",           maskCpf(account.getCpf()));
        addInfoCell(info, "Agência",       account.getAgency());
        addInfoCell(info, "Conta",         account.getAccountNumber());
        addInfoCell(info, "E-mail",        account.getEmail());
        addInfoCell(info, "Instituição",   BANK_NAME);
        addInfoCell(info, "Período início", DATE_FMT.format(since));
        addInfoCell(info, "Período fim",   DATE_FMT.format(now));

        doc.add(info);
    }

    private void addInfoCell(PdfPTable table, String label, String value) {
        Phrase phrase = new Phrase();
        phrase.add(new Chunk(label + "\n", FontFactory.getFont(FontFactory.HELVETICA, 7, MUTED)));
        phrase.add(new Chunk(value != null ? value : "—",
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, TEXT)));

        PdfPCell cell = new PdfPCell(phrase);
        cell.setPadding(7);
        cell.setPaddingBottom(8);
        cell.setBorderColor(GRAY_LINE);
        cell.setBorderWidth(0.5f);
        table.addCell(cell);
    }

    // ── Summary box ───────────────────────────────────────────────────────────

    private void addSummaryBox(Document doc, Account account,
                               List<Transaction> transactions) throws DocumentException {
        BigDecimal totalCredits = transactions.stream()
                .filter(t -> t.getType() == Transaction.TransactionType.ACCOUNT_CREDIT
                          && t.getStatus() == Transaction.TransactionStatus.SUCCESS)
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalDebits = transactions.stream()
                .filter(t -> t.getType() != Transaction.TransactionType.ACCOUNT_CREDIT
                          && t.getStatus() == Transaction.TransactionStatus.SUCCESS)
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal currentBalance = account.getBalance();
        BigDecimal initialBalance = currentBalance.add(totalDebits).subtract(totalCredits);

        PdfPTable summary = new PdfPTable(4);
        summary.setWidthPercentage(100);
        summary.setSpacingAfter(4);

        addSummaryCell(summary, "Saldo Inicial", fmtBrl(initialBalance), TEXT,     GRAY_BG);
        addSummaryCell(summary, "Total Créditos", fmtBrl(totalCredits),  GREEN,    new Color(0xF0, 0xFB, 0xF4));
        addSummaryCell(summary, "Total Débitos",  fmtBrl(totalDebits),   RED,      new Color(0xFB, 0xF0, 0xF0));
        addSummaryCell(summary, "Saldo Final",    fmtBrl(currentBalance), NAVY,    new Color(0xF0, 0xF4, 0xFB));

        doc.add(summary);
    }

    private void addSummaryCell(PdfPTable table, String label, String value,
                                Color valueColor, Color bgColor) {
        Phrase phrase = new Phrase();
        phrase.add(new Chunk(label + "\n", FontFactory.getFont(FontFactory.HELVETICA, 8, MUTED)));
        phrase.add(new Chunk(value, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, valueColor)));

        PdfPCell cell = new PdfPCell(phrase);
        cell.setBackgroundColor(bgColor);
        cell.setPadding(10);
        cell.setBorderColor(GRAY_LINE);
        cell.setBorderWidth(0.5f);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        table.addCell(cell);
    }

    // ── Transactions table ────────────────────────────────────────────────────

    private void addTransactionsTable(Document doc, List<Transaction> transactions)
            throws DocumentException {

        // Section title
        doc.add(new Paragraph("Lançamentos",
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, NAVY)));
        doc.add(new Chunk(Chunk.NEWLINE));

        // Column headers
        PdfPTable table = new PdfPTable(5);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{1.5f, 2.5f, 3.5f, 1.5f, 1.8f});
        table.setHeaderRows(1);

        addTableHeader(table, "Data");
        addTableHeader(table, "Tipo");
        addTableHeader(table, "Histórico / Chave");
        addTableHeader(table, "Valor (R$)");
        addTableHeader(table, "Saldo (R$)");

        if (transactions.isEmpty()) {
            PdfPCell empty = new PdfPCell(new Phrase(
                    "Nenhum lançamento no período.",
                    FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 9, MUTED)));
            empty.setColspan(5);
            empty.setPadding(12);
            empty.setBorderColor(GRAY_LINE);
            empty.setBorderWidth(0.5f);
            table.addCell(empty);
        } else {
            boolean alt = false;
            for (Transaction tx : transactions) {
                Color rowBg = alt ? GRAY_BG : WHITE;
                boolean isCredit = tx.getType() == Transaction.TransactionType.ACCOUNT_CREDIT;
                Color amtColor = isCredit ? GREEN : RED;
                String amtPrefix = isCredit ? "+" : "-";
                String statusSuffix = tx.getStatus() == Transaction.TransactionStatus.FAILED ? " [FALHA]"
                        : tx.getStatus() == Transaction.TransactionStatus.PENDING ? " [PENDENTE]" : "";

                ZonedDateTime dt = tx.getCreatedAt().atZone(BR_ZONE);

                addTxCell(table, DATE_FMT.format(dt),           TEXT,     rowBg);
                addTxCell(table, typePtBr(tx.getType()),         MUTED,    rowBg);
                addTxCellDescription(table, tx, rowBg);
                addTxCell(table, amtPrefix + fmtBrl(tx.getAmount()) + statusSuffix, amtColor, rowBg);
                addTxCell(table, tx.getBalanceAfter() != null ? fmtBrl(tx.getBalanceAfter()) : "—",
                         TEXT, rowBg);

                alt = !alt;
            }
        }

        doc.add(table);
    }

    private void addTableHeader(PdfPTable table, String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text,
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, WHITE)));
        cell.setBackgroundColor(NAVY);
        cell.setPadding(7);
        cell.setBorder(Rectangle.NO_BORDER);
        table.addCell(cell);
    }

    private void addTxCell(PdfPTable table, String text, Color textColor, Color bgColor) {
        PdfPCell cell = new PdfPCell(new Phrase(text,
                FontFactory.getFont(FontFactory.HELVETICA, 8, textColor)));
        cell.setBackgroundColor(bgColor);
        cell.setPadding(6);
        cell.setBorderColor(GRAY_LINE);
        cell.setBorderWidth(0.5f);
        table.addCell(cell);
    }

    private void addTxCellDescription(PdfPTable table, Transaction tx, Color bgColor) {
        String desc = tx.getDescription() != null && !tx.getDescription().isBlank()
                ? tx.getDescription() : "";
        String ref  = tx.getReference()   != null && !tx.getReference().isBlank()
                ? tx.getReference() : "";
        String e2e  = tx.getEndToEndId()  != null ? "E2E: " + tx.getEndToEndId().substring(0,
                Math.min(16, tx.getEndToEndId().length())) + "…" : "";

        Phrase phrase = new Phrase();
        if (!desc.isBlank()) {
            phrase.add(new Chunk(desc + "\n", FontFactory.getFont(FontFactory.HELVETICA, 8, TEXT)));
        }
        if (!ref.isBlank()) {
            phrase.add(new Chunk(ref + "\n",  FontFactory.getFont(FontFactory.HELVETICA, 7, MUTED)));
        }
        if (!e2e.isBlank()) {
            phrase.add(new Chunk(e2e,         FontFactory.getFont(FontFactory.HELVETICA, 6, MUTED)));
        }
        if (phrase.isEmpty()) {
            phrase.add(new Chunk("—", FontFactory.getFont(FontFactory.HELVETICA, 8, MUTED)));
        }

        PdfPCell cell = new PdfPCell(phrase);
        cell.setBackgroundColor(bgColor);
        cell.setPadding(6);
        cell.setBorderColor(GRAY_LINE);
        cell.setBorderWidth(0.5f);
        table.addCell(cell);
    }

    // ── Footer ────────────────────────────────────────────────────────────────

    private void addFooter(Document doc, ZonedDateTime now, String authCode)
            throws DocumentException {
        doc.add(new LineSeparator(0.5f, 100, GRAY_LINE, Element.ALIGN_CENTER, -2));
        doc.add(Chunk.NEWLINE);

        Paragraph footer = new Paragraph();
        footer.setAlignment(Element.ALIGN_CENTER);
        footer.add(new Chunk(
                "Documento gerado eletronicamente em " + DATETIME_FMT.format(now) +
                " (horário de Brasília)\n",
                FontFactory.getFont(FontFactory.HELVETICA, 7, MUTED)));
        footer.add(new Chunk(
                "Código de autenticidade: " + formatAuthCode(authCode) + "\n",
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7, MUTED)));
        footer.add(new Chunk(
                "As informações de CPF foram parcialmente ocultadas em conformidade com a LGPD (Lei 13.709/2018).\n",
                FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 6.5f, MUTED)));
        footer.add(new Chunk(
                "Este extrato tem validade legal conforme Resolução CMN 3.919/2010 e 4.282/2013.",
                FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 6.5f, MUTED)));

        doc.add(footer);
    }

    // ── Page numbering event ──────────────────────────────────────────────────

    private void addPageEvents(PdfWriter writer) {
        writer.setPageEvent(new PdfPageEventHelper() {
            @Override
            public void onEndPage(PdfWriter w, Document d) {
                try {
                    PdfContentByte cb = w.getDirectContent();
                    Phrase pageNum = new Phrase(
                            "Página " + w.getPageNumber(),
                            FontFactory.getFont(FontFactory.HELVETICA, 7, MUTED));
                    ColumnText.showTextAligned(cb, Element.ALIGN_RIGHT, pageNum,
                            d.right(), d.bottom() - 15, 0);
                } catch (Exception ignored) {}
            }
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String maskCpf(String cpf) {
        if (cpf == null || cpf.length() < 11) return "***.***.***-**";
        String digits = cpf.replaceAll("\\D", "");
        if (digits.length() < 11) return "***.***.***-**";
        // Show only middle 3 digits: ***.456.789-**
        return "***." + digits.substring(3, 6) + "." + digits.substring(6, 9) + "-**";
    }

    private String fmtBrl(BigDecimal value) {
        if (value == null) return "0,00";
        return String.format("%,.2f", value).replace(',', 'X').replace('.', ',').replace('X', '.');
    }

    private String typePtBr(Transaction.TransactionType type) {
        return switch (type) {
            case PIX_OUT      -> "PIX Enviado";
            case BOLETO_OUT   -> "Boleto";
            case ACCOUNT_CREDIT -> "Crédito";
        };
    }

    private String formatAuthCode(String raw) {
        // Format as XXXX-XXXX-XXXX-XXXX
        return raw.substring(0, 4)  + "-" + raw.substring(4, 8)  + "-" +
               raw.substring(8, 12) + "-" + raw.substring(12, 16);
    }
}
