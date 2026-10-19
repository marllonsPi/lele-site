-- Fase 7: notificacoes (alertas por e-mail; WhatsApp quando houver provedor).
CREATE TABLE notificacao (
    id              BIGSERIAL PRIMARY KEY,
    tipo            VARCHAR(30)  NOT NULL,
    escopo_id       BIGINT       NOT NULL,
    destinatario_id BIGINT       NOT NULL REFERENCES usuario (id),
    canal           VARCHAR(10)  NOT NULL,
    data_ref        DATE         NOT NULL,
    assunto         VARCHAR(200) NOT NULL,
    corpo           TEXT         NOT NULL,
    status          VARCHAR(10)  NOT NULL DEFAULT 'PENDENTE',
    tentativas      INTEGER      NOT NULL DEFAULT 0,
    erro            VARCHAR(300),
    criado_em       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    enviado_em      TIMESTAMPTZ,
    CONSTRAINT ck_notificacao_canal CHECK (canal IN ('EMAIL', 'WHATSAPP')),
    CONSTRAINT ck_notificacao_status CHECK (status IN ('PENDENTE', 'ENVIADA', 'FALHA')),
    -- idempotente: o mesmo alerta, para o mesmo destinatario, canal e dia, existe uma vez so
    CONSTRAINT uq_notificacao UNIQUE (tipo, escopo_id, destinatario_id, canal, data_ref)
);
CREATE INDEX ix_notificacao_pendente ON notificacao (status, data_ref);
