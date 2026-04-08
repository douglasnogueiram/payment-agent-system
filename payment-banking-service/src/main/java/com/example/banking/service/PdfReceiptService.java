package com.example.banking.service;

import com.example.banking.entity.Account;
import com.example.banking.entity.Transaction;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import com.lowagie.text.pdf.draw.LineSeparator;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Generates a BACEN-compliant Pix payment receipt (comprovante).
 *
 * Regulatory basis:
 *  - Resolução BCB n.º 1/2020 — Regulamento do Pix
 *  - Circular BCB n.º 3.978/2020 — dados obrigatórios no comprovante
 *  - LGPD (Lei 13.709/2018) — CPF parcialmente mascarado
 */
@Service
public class PdfReceiptService {

    private static final String BANK_NAME    = "Meu Agente Pix";
    private static final String BANK_CNPJ    = "00.000.000/0001-91";
    private static final String BANK_ISPB    = "00000000";

    private static final ZoneId           BR_ZONE      = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter DATE_FMT     = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm:ss");

    // ── Brand palette ─────────────────────────────────────────────────────────
    private static final Color PIX_TEAL    = new Color(0x00, 0xBD, 0xAE); // #00BDAE
    private static final Color PIX_DARK    = new Color(0x00, 0x94, 0x88); // #009488
    private static final Color HEADER_BG   = new Color(0x11, 0x18, 0x27); // #111827
    private static final Color HEADER_R    = new Color(0x1C, 0x25, 0x36); // #1C2536
    private static final Color SUCCESS     = new Color(0x10, 0xB9, 0x81); // #10B981
    private static final Color SUCCESS_BG  = new Color(0xD1, 0xFA, 0xE5);
    private static final Color ERROR       = new Color(0xEF, 0x44, 0x44); // #EF4444
    private static final Color ERROR_BG    = new Color(0xFE, 0xE2, 0xE2);
    private static final Color GRAY_BG     = new Color(0xF5, 0xF7, 0xFA);
    private static final Color GRAY_LINE   = new Color(0xDD, 0xE0, 0xE5);
    private static final Color TEAL_LIGHT  = new Color(0xE6, 0xFB, 0xF9);
    private static final Color TEXT        = new Color(0x1A, 0x1A, 0x2E);
    private static final Color MUTED       = new Color(0x60, 0x70, 0x80);
    private static final Color WHITE       = Color.WHITE;

    // Fonts
    private static final Font F_TITLE    = FontFactory.getFont(FontFactory.HELVETICA_BOLD,  18, TEXT);
    private static final Font F_AMOUNT   = FontFactory.getFont(FontFactory.HELVETICA_BOLD,  26, PIX_DARK);
    private static final Font F_SECTION  = FontFactory.getFont(FontFactory.HELVETICA_BOLD,   9, PIX_DARK);
    private static final Font F_LABEL    = FontFactory.getFont(FontFactory.HELVETICA,         8, MUTED);
    private static final Font F_VALUE    = FontFactory.getFont(FontFactory.HELVETICA_BOLD,    9, TEXT);
    private static final Font F_MONO     = FontFactory.getFont(FontFactory.COURIER,            7, MUTED);
    private static final Font F_FOOTER   = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 6.5f, MUTED);
    private static final Font F_WHITE_SM = FontFactory.getFont(FontFactory.HELVETICA,          8, new Color(0x8B, 0x9C, 0xB5));
    private static final Font F_WHITE_BD = FontFactory.getFont(FontFactory.HELVETICA_BOLD,    11, WHITE);

    public byte[] generate(Account payerAccount, Transaction tx) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 60, 60, 40, 50);
            PdfWriter writer = PdfWriter.getInstance(doc, baos);
            addPageEvent(writer);
            doc.open();

            ZonedDateTime txTime  = tx.getCreatedAt().atZone(BR_ZONE);
            ZonedDateTime nowTime = ZonedDateTime.now(BR_ZONE);
            String authCode = UUID.randomUUID().toString().toUpperCase().replace("-", "").substring(0, 16);
            boolean success = tx.getStatus() == Transaction.TransactionStatus.SUCCESS;

            addHeader(doc, nowTime);
            addStatusBadge(doc, success);
            addAmountBlock(doc, tx);
            doc.add(Chunk.NEWLINE);
            addSection(doc, "PAGADOR");
            addInfoTable(doc, payerAccount, tx);
            doc.add(Chunk.NEWLINE);
            addSection(doc, "RECEBEDOR");
            addRecipientTable(doc, tx);
            doc.add(Chunk.NEWLINE);
            addSection(doc, "DETALHES DA TRANSAÇÃO");
            addDetailsTable(doc, tx, txTime);
            doc.add(Chunk.NEWLINE);
            addE2EBlock(doc, tx);
            doc.add(Chunk.NEWLINE);
            addLegalFooter(doc, nowTime, authCode);

            doc.close();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Erro ao gerar comprovante Pix", e);
        }
    }

    // ── Header ────────────────────────────────────────────────────────────────

    private void addHeader(Document doc, ZonedDateTime now) throws DocumentException {
        PdfPTable header = new PdfPTable(2);
        header.setWidthPercentage(100);
        header.setWidths(new float[]{3f, 2f});
        header.setSpacingAfter(0);

        Phrase left = new Phrase();
        left.add(new Chunk(BANK_NAME + "\n",
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13, PIX_TEAL)));
        left.add(new Chunk("Assistente de Pagamentos com IA\n",
                FontFactory.getFont(FontFactory.HELVETICA, 7, new Color(0x8B, 0x9C, 0xB5))));
        left.add(new Chunk("CNPJ: " + BANK_CNPJ + "   ISPB: " + BANK_ISPB, F_WHITE_SM));
        PdfPCell lCell = new PdfPCell(left);
        lCell.setBackgroundColor(HEADER_BG);
        lCell.setPadding(14);
        lCell.setBorder(Rectangle.NO_BORDER);
        header.addCell(lCell);

        Phrase right = new Phrase();
        right.add(new Chunk("Emitido em\n", F_WHITE_SM));
        right.add(new Chunk(DATETIME_FMT.format(now), F_WHITE_BD));
        PdfPCell rCell = new PdfPCell(right);
        rCell.setBackgroundColor(HEADER_R);
        rCell.setPadding(14);
        rCell.setBorder(Rectangle.NO_BORDER);
        rCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        header.addCell(rCell);

        doc.add(header);

        // Teal accent stripe
        PdfPTable accent = new PdfPTable(1);
        accent.setWidthPercentage(100);
        PdfPCell accentCell = new PdfPCell(new Phrase(""));
        accentCell.setBackgroundColor(PIX_TEAL);
        accentCell.setFixedHeight(3f);
        accentCell.setBorder(Rectangle.NO_BORDER);
        accent.addCell(accentCell);
        accent.setSpacingAfter(12);
        doc.add(accent);

        Paragraph title = new Paragraph("COMPROVANTE DE TRANSFERÊNCIA PIX", F_TITLE);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingAfter(6);
        doc.add(title);
    }

    // ── Status badge ──────────────────────────────────────────────────────────

    private void addStatusBadge(Document doc, boolean success) throws DocumentException {
        PdfPTable badge = new PdfPTable(1);
        badge.setWidthPercentage(60);
        badge.setHorizontalAlignment(Element.ALIGN_CENTER);
        badge.setSpacingAfter(16);

        String label = success ? "✓  Pagamento confirmado" : "✕  Pagamento não concluído";
        Color  bg    = success ? SUCCESS_BG : ERROR_BG;
        Color  fg    = success ? SUCCESS    : ERROR;
        Color  border= success ? new Color(0x6E, 0xE7, 0xB7) : new Color(0xFC, 0xA5, 0xA5);

        PdfPCell cell = new PdfPCell(new Phrase(label,
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, fg)));
        cell.setBackgroundColor(bg);
        cell.setPadding(10);
        cell.setBorderColor(border);
        cell.setBorderWidth(1f);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        badge.addCell(cell);

        doc.add(badge);
    }

    // ── Amount ────────────────────────────────────────────────────────────────

    private void addAmountBlock(Document doc, Transaction tx) throws DocumentException {
        Paragraph label = new Paragraph("Valor transferido", F_LABEL);
        label.setAlignment(Element.ALIGN_CENTER);
        doc.add(label);

        Paragraph amount = new Paragraph(fmtBrl(tx.getAmount()), F_AMOUNT);
        amount.setAlignment(Element.ALIGN_CENTER);
        amount.setSpacingAfter(4);
        doc.add(amount);
    }

    // ── Section title ─────────────────────────────────────────────────────────

    private void addSection(Document doc, String title) throws DocumentException {
        doc.add(new LineSeparator(0.5f, 100, GRAY_LINE, Element.ALIGN_CENTER, -2));
        Paragraph p = new Paragraph(title, F_SECTION);
        p.setSpacingBefore(8);
        p.setSpacingAfter(6);
        doc.add(p);
    }

    // ── Payer info table ──────────────────────────────────────────────────────

    private void addInfoTable(Document doc, Account acc, Transaction tx) throws DocumentException {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{1f, 1f});
        table.setSpacingAfter(4);

        addRow(table, "Nome",            acc.getName());
        addRow(table, "CPF",             maskCpf(acc.getCpf()));
        addRow(table, "Instituição",     BANK_NAME + " (ISPB " + BANK_ISPB + ")");
        addRow(table, "Agência / Conta", acc.getAgency() + " / " + acc.getAccountNumber());

        doc.add(table);
    }

    // ── Recipient table ───────────────────────────────────────────────────────

    private void addRecipientTable(Document doc, Transaction tx) throws DocumentException {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{1f, 1f});
        table.setSpacingAfter(4);

        String recipientName = tx.getRecipientName() != null ? tx.getRecipientName() : "—";
        addRow(table, "Nome",        recipientName);
        addRow(table, "Chave Pix",   tx.getReference() != null ? tx.getReference() : "—");
        addRow(table, "Instituição", ispbFromE2E(tx.getEndToEndId()));

        doc.add(table);
    }

    // ── Transaction details table ─────────────────────────────────────────────

    private void addDetailsTable(Document doc, Transaction tx, ZonedDateTime txTime)
            throws DocumentException {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{1f, 1f});
        table.setSpacingAfter(4);

        addRow(table, "Data e hora",     DATETIME_FMT.format(txTime));
        addRow(table, "Tipo de operação","Pix — Transferência a Crédito");
        addRow(table, "Status",          statusPtBr(tx.getStatus()));
        addRow(table, "ID da transação", "#" + tx.getId());

        doc.add(table);
    }

    // ── E2E block ────────────────────────────────────────────────────────────

    private void addE2EBlock(Document doc, Transaction tx) throws DocumentException {
        if (tx.getEndToEndId() == null) return;

        PdfPTable box = new PdfPTable(1);
        box.setWidthPercentage(100);
        box.setSpacingAfter(4);

        Phrase phrase = new Phrase();
        phrase.add(new Chunk("ID de fim a fim (EndToEndId)\n", F_LABEL));
        phrase.add(new Chunk(tx.getEndToEndId(), F_MONO));

        PdfPCell cell = new PdfPCell(phrase);
        cell.setBackgroundColor(TEAL_LIGHT);
        cell.setPadding(10);
        cell.setBorderColor(new Color(0xA7, 0xF3, 0xD0));
        cell.setBorderWidth(0.5f);
        box.addCell(cell);

        doc.add(box);
    }

    // ── Legal footer ──────────────────────────────────────────────────────────

    private void addLegalFooter(Document doc, ZonedDateTime now, String authCode)
            throws DocumentException {
        doc.add(new LineSeparator(1f, 100, PIX_TEAL, Element.ALIGN_CENTER, -2));
        doc.add(Chunk.NEWLINE);

        Paragraph footer = new Paragraph();
        footer.setAlignment(Element.ALIGN_CENTER);
        footer.add(new Chunk(
                "Código de autenticidade: " + formatCode(authCode) + "\n",
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7, PIX_DARK)));
        footer.add(new Chunk(
                "Documento gerado eletronicamente em " + DATETIME_FMT.format(now) + " (horário de Brasília).\n",
                F_FOOTER));
        footer.add(new Chunk(
                "CPF parcialmente ocultado em conformidade com a LGPD (Lei 13.709/2018).\n",
                F_FOOTER));
        footer.add(new Chunk(
                "Comprovante emitido conforme Resolução BCB n.º 1/2020 e Circular BCB n.º 3.978/2020.",
                F_FOOTER));
        doc.add(footer);
    }

    // ── Page numbering ────────────────────────────────────────────────────────

    private void addPageEvent(PdfWriter writer) {
        writer.setPageEvent(new PdfPageEventHelper() {
            @Override
            public void onEndPage(PdfWriter w, Document d) {
                try {
                    ColumnText.showTextAligned(w.getDirectContent(), Element.ALIGN_RIGHT,
                            new Phrase("Pág. " + w.getPageNumber(),
                                    FontFactory.getFont(FontFactory.HELVETICA, 7, MUTED)),
                            d.right(), d.bottom() - 15, 0);
                } catch (Exception ignored) {}
            }
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void addRow(PdfPTable table, String label, String value) {
        PdfPCell lCell = new PdfPCell(new Phrase(label, F_LABEL));
        lCell.setPadding(8);
        lCell.setBorderColor(GRAY_LINE);
        lCell.setBorderWidth(0.5f);
        lCell.setBackgroundColor(GRAY_BG);
        table.addCell(lCell);

        PdfPCell vCell = new PdfPCell(new Phrase(value != null ? value : "—", F_VALUE));
        vCell.setPadding(8);
        vCell.setBorderColor(GRAY_LINE);
        vCell.setBorderWidth(0.5f);
        table.addCell(vCell);
    }

    private String fmtBrl(java.math.BigDecimal value) {
        if (value == null) return "R$ 0,00";
        return "R$ " + String.format("%,.2f", value).replace(',', 'X').replace('.', ',').replace('X', '.');
    }

    private String maskCpf(String cpf) {
        if (cpf == null || cpf.length() < 11) return "***.***.***-**";
        String d = cpf.replaceAll("\\D", "");
        if (d.length() < 11) return "***.***.***-**";
        return "***." + d.substring(3, 6) + "." + d.substring(6, 9) + "-**";
    }

    private String statusPtBr(Transaction.TransactionStatus status) {
        return switch (status) {
            case SUCCESS -> "Concluído com sucesso";
            case FAILED  -> "Falhou — saldo estornado";
            case PENDING -> "Pendente";
        };
    }

    private String ispbFromE2E(String e2e) {
        if (e2e != null && e2e.startsWith("E") && e2e.length() >= 9) {
            String ispb = e2e.substring(1, 9);
            if (BANK_ISPB.equals(ispb)) {
                return BANK_NAME + " (ISPB " + ispb + ")";
            }
            return "ISPB " + ispb;
        }
        return "—";
    }

    private String formatCode(String raw) {
        return raw.substring(0, 4) + "-" + raw.substring(4, 8) + "-"
             + raw.substring(8, 12) + "-" + raw.substring(12, 16);
    }
}
