-- Conexao OAuth 2.0 do Google por usuario (captura de intimacoes por e-mail). Nunca guarda senha do Google;
-- o refresh token fica cifrado (AES-256-GCM) com chave fora do banco.
CREATE TABLE conexao_google (
    id                    BIGSERIAL PRIMARY KEY,
    usuario_id            BIGINT      NOT NULL UNIQUE REFERENCES usuario (id),
    email_google          VARCHAR(254) NOT NULL,
    refresh_token_cifrado BYTEA       NOT NULL,
    versao_chave          SMALLINT    NOT NULL DEFAULT 1,
    escopos               TEXT        NOT NULL,
    estado                VARCHAR(10) NOT NULL DEFAULT 'ATIVA',
    criada_em             TIMESTAMPTZ NOT NULL DEFAULT now(),
    ultimo_uso_em         TIMESTAMPTZ,
    CONSTRAINT ck_conexao_google_estado CHECK (estado IN ('ATIVA', 'EXPIRADA'))
);

-- Estado temporario do fluxo de consentimento (anti-CSRF + PKCE), preso ao usuario da sessao.
CREATE TABLE oauth_estado (
    state_hash        VARCHAR(64) PRIMARY KEY,
    usuario_id        BIGINT      NOT NULL REFERENCES usuario (id),
    verifier_cifrado  BYTEA       NOT NULL,
    expira_em         TIMESTAMPTZ NOT NULL
);
CREATE INDEX ix_oauth_estado_expira ON oauth_estado (expira_em);
