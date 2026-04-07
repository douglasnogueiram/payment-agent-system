-- ============================================================
-- Payment Agent System — Queries de Monitoramento
-- Banco: payment_banking  (postgres-banking:5432)
-- ============================================================


-- ────────────────────────────────────────────────────────────
-- 1. CONTAS ABERTAS
--    Visão geral de todas as contas correntes cadastradas.
-- ────────────────────────────────────────────────────────────

SELECT
    a.id,
    a.name                                          AS nome,
    a.cpf,
    a.email,
    a.account_number                                AS conta,
    a.agency                                        AS agencia,
    TO_CHAR(a.balance, 'FM999G999G990D00')          AS saldo,
    CASE WHEN a.active THEN 'Ativa' ELSE 'Inativa' END AS situacao,
    a.keycloak_user_id,
    a.created_at AT TIME ZONE 'America/Sao_Paulo'   AS aberta_em
FROM accounts a
ORDER BY a.created_at DESC;


-- ────────────────────────────────────────────────────────────
-- 2. RESUMO POR CONTA
--    Saldo atual, total de transações e volume movimentado.
-- ────────────────────────────────────────────────────────────

SELECT
    a.name                                              AS nome,
    a.account_number                                    AS conta,
    TO_CHAR(a.balance, 'FM999G999G990D00')              AS saldo_atual,
    COUNT(t.id)                                         AS total_transacoes,
    TO_CHAR(COALESCE(SUM(t.amount) FILTER (WHERE t.type <> 'ACCOUNT_CREDIT'), 0),
            'FM999G999G990D00')                         AS volume_movimentado,
    MAX(t.created_at) AT TIME ZONE 'America/Sao_Paulo'  AS ultima_transacao
FROM accounts a
LEFT JOIN transactions t ON t.account_number = a.account_number
GROUP BY a.id, a.name, a.account_number, a.balance
ORDER BY total_transacoes DESC;


-- ────────────────────────────────────────────────────────────
-- 3. TODAS AS TRANSAÇÕES (mais recentes primeiro)
-- ────────────────────────────────────────────────────────────

SELECT
    t.id,
    t.account_number                                AS conta,
    a.name                                          AS titular,
    CASE t.type
        WHEN 'PIX_OUT'      THEN 'Pix Enviado'
        WHEN 'BOLETO_OUT'   THEN 'Boleto Pago'
        WHEN 'ACCOUNT_CREDIT' THEN 'Abertura de Conta'
    END                                             AS tipo,
    TO_CHAR(t.amount, 'FM999G999G990D00')           AS valor,
    TO_CHAR(t.balance_after, 'FM999G999G990D00')    AS saldo_apos,
    t.description                                   AS descricao,
    t.reference                                     AS referencia,
    t.status                                        AS status,
    t.created_at AT TIME ZONE 'America/Sao_Paulo'   AS data_hora
FROM transactions t
JOIN accounts a ON a.account_number = t.account_number
ORDER BY t.created_at DESC
LIMIT 100;


-- ────────────────────────────────────────────────────────────
-- 4. TRANSAÇÕES PIX — destinos e valores
-- ────────────────────────────────────────────────────────────

SELECT
    t.created_at AT TIME ZONE 'America/Sao_Paulo'   AS data_hora,
    a.name                                          AS remetente,
    a.account_number                                AS conta_origem,
    t.reference                                     AS chave_pix_destino,
    TO_CHAR(t.amount, 'FM999G999G990D00')           AS valor,
    t.description                                   AS descricao,
    t.status
FROM transactions t
JOIN accounts a ON a.account_number = t.account_number
WHERE t.type = 'PIX_OUT'
ORDER BY t.created_at DESC;


-- ────────────────────────────────────────────────────────────
-- 5. TRANSAÇÕES BOLETO — códigos pagos
-- ────────────────────────────────────────────────────────────

SELECT
    t.created_at AT TIME ZONE 'America/Sao_Paulo'   AS data_hora,
    a.name                                          AS pagador,
    a.account_number                                AS conta,
    t.reference                                     AS codigo_boleto,
    TO_CHAR(t.amount, 'FM999G999G990D00')           AS valor,
    t.status
FROM transactions t
JOIN accounts a ON a.account_number = t.account_number
WHERE t.type = 'BOLETO_OUT'
ORDER BY t.created_at DESC;


-- ────────────────────────────────────────────────────────────
-- 6. TRANSAÇÕES FALHAS
-- ────────────────────────────────────────────────────────────

SELECT
    t.id,
    t.created_at AT TIME ZONE 'America/Sao_Paulo'   AS data_hora,
    a.name                                          AS titular,
    a.account_number                                AS conta,
    t.type                                          AS tipo,
    TO_CHAR(t.amount, 'FM999G999G990D00')           AS valor,
    t.reference                                     AS referencia,
    t.description
FROM transactions t
JOIN accounts a ON a.account_number = t.account_number
WHERE t.status = 'FAILED'
ORDER BY t.created_at DESC;


-- ────────────────────────────────────────────────────────────
-- 7. ONBOARDING CELCOIN — status dos cadastros
-- ────────────────────────────────────────────────────────────

SELECT
    co.full_name                                        AS nome,
    co.document_number                                  AS cpf,
    co.email,
    co.phone_number                                     AS telefone,
    co.status                                           AS status_onboarding,
    co.account_number                                   AS conta_gerada,
    co.account_branch                                   AS agencia,
    co.created_at AT TIME ZONE 'America/Sao_Paulo'      AS iniciado_em,
    co.confirmed_at AT TIME ZONE 'America/Sao_Paulo'    AS confirmado_em,
    EXTRACT(EPOCH FROM (co.confirmed_at - co.created_at))::int AS duracao_seg
FROM celcoin_onboarding co
ORDER BY co.created_at DESC;


-- ────────────────────────────────────────────────────────────
-- 8. DASHBOARD GERAL — números consolidados
-- ────────────────────────────────────────────────────────────

SELECT
    (SELECT COUNT(*) FROM accounts WHERE active = true)                         AS contas_ativas,
    (SELECT COUNT(*) FROM accounts WHERE active = false)                        AS contas_inativas,
    (SELECT TO_CHAR(COALESCE(SUM(balance), 0), 'FM999G999G990D00')
     FROM accounts WHERE active = true)                                         AS saldo_total_custodiado,
    (SELECT COUNT(*) FROM transactions WHERE type = 'PIX_OUT')                  AS total_pix,
    (SELECT COUNT(*) FROM transactions WHERE type = 'BOLETO_OUT')               AS total_boletos,
    (SELECT TO_CHAR(COALESCE(SUM(amount), 0), 'FM999G999G990D00')
     FROM transactions WHERE type IN ('PIX_OUT','BOLETO_OUT')
       AND status = 'SUCCESS')                                                   AS volume_total_transacionado,
    (SELECT COUNT(*) FROM transactions WHERE status = 'FAILED')                 AS transacoes_falhas,
    (SELECT COUNT(*) FROM celcoin_onboarding WHERE status = 'PROCESSING')       AS onboardings_pendentes;


-- ────────────────────────────────────────────────────────────
-- 9. EXTRATO DE UMA CONTA ESPECÍFICA
--    Substitua '00000000001' pelo número da conta desejada.
-- ────────────────────────────────────────────────────────────

SELECT
    t.id,
    t.created_at AT TIME ZONE 'America/Sao_Paulo'   AS data_hora,
    CASE t.type
        WHEN 'PIX_OUT'        THEN 'Pix Enviado'
        WHEN 'BOLETO_OUT'     THEN 'Boleto Pago'
        WHEN 'ACCOUNT_CREDIT' THEN 'Abertura de Conta'
    END                                             AS tipo,
    TO_CHAR(t.amount, 'FM999G999G990D00')           AS valor,
    TO_CHAR(t.balance_after, 'FM999G999G990D00')    AS saldo_apos,
    t.reference                                     AS referencia,
    t.description,
    t.status
FROM transactions t
WHERE t.account_number = '00000000001'   -- << altere aqui
ORDER BY t.created_at DESC;
