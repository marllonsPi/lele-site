-- Acesso mestre (sempre uma SOCIA com poderes extras), dados de contato/atuacao por conta e configuracao do escritorio.
ALTER TABLE usuario
    ADD COLUMN mestre        BOOLEAN      NOT NULL DEFAULT FALSE,
    ADD COLUMN telefone      VARCHAR(30),
    ADD COLUMN whatsapp      VARCHAR(30),
    ADD COLUMN cargo         VARCHAR(80),
    ADD COLUMN email_alertas VARCHAR(254),
    ADD COLUMN alertas_email BOOLEAN      NOT NULL DEFAULT TRUE,
    ADD COLUMN assinatura    VARCHAR(500);

ALTER TABLE usuario
    ADD CONSTRAINT ck_usuario_mestre CHECK (NOT mestre OR perfil = 'SOCIA');

CREATE TABLE configuracao (
    chave          VARCHAR(80)  PRIMARY KEY,
    valor          TEXT         NOT NULL,
    atualizado_em  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    atualizado_por BIGINT       REFERENCES usuario (id)
);
