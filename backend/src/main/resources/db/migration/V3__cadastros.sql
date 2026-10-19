-- Fase 2: clientes, processos e partes. Exclusao logica (invariante 3).
CREATE TABLE cliente (
    id          BIGSERIAL PRIMARY KEY,
    nome        VARCHAR(200) NOT NULL,
    documento   VARCHAR(14),
    email       VARCHAR(254),
    telefone    VARCHAR(30),
    endereco    VARCHAR(400),
    observacoes TEXT,
    ativo       BOOLEAN      NOT NULL DEFAULT TRUE,
    criado_em   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_cliente_documento CHECK (documento IS NULL OR documento ~ '^([0-9]{11}|[0-9]{14})$')
);
CREATE UNIQUE INDEX uq_cliente_documento ON cliente (documento) WHERE documento IS NOT NULL;

CREATE TABLE processo (
    id           BIGSERIAL PRIMARY KEY,
    numero       CHAR(20)      NOT NULL,
    tribunal     VARCHAR(40)   NOT NULL,
    vara_comarca VARCHAR(200),
    classe       VARCHAR(200),
    situacao     VARCHAR(100),
    valor_causa  NUMERIC(15, 2),
    cliente_id   BIGINT        NOT NULL REFERENCES cliente (id),
    advogado_id  BIGINT        NOT NULL REFERENCES usuario (id),
    ativo        BOOLEAN       NOT NULL DEFAULT TRUE,
    criado_em    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_processo_numero UNIQUE (numero),
    CONSTRAINT ck_processo_numero CHECK (numero ~ '^[0-9]{20}$'),
    CONSTRAINT ck_processo_valor CHECK (valor_causa IS NULL OR valor_causa >= 0)
);
CREATE INDEX ix_processo_cliente ON processo (cliente_id);
CREATE INDEX ix_processo_advogado ON processo (advogado_id);

CREATE TABLE parte_processo (
    id          BIGSERIAL PRIMARY KEY,
    processo_id BIGINT       NOT NULL REFERENCES processo (id),
    nome        VARCHAR(200) NOT NULL,
    papel       VARCHAR(20)  NOT NULL,
    CONSTRAINT ck_parte_papel CHECK (papel IN ('CLIENTE', 'CONTRARIA', 'OUTRO'))
);
CREATE INDEX ix_parte_processo ON parte_processo (processo_id);
