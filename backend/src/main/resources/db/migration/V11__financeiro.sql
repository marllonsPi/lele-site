-- Fase 9: financeiro (somente socias): contrato de honorarios, parcelas, custas/despesas, exito e sucumbencia.
CREATE TABLE contrato_honorarios (
    id          BIGSERIAL PRIMARY KEY,
    cliente_id  BIGINT        NOT NULL REFERENCES cliente (id),
    processo_id BIGINT REFERENCES processo (id),
    descricao   VARCHAR(300)  NOT NULL,
    valor_total NUMERIC(15, 2) NOT NULL,
    ativo       BOOLEAN       NOT NULL DEFAULT TRUE,
    criado_em   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_contrato_valor CHECK (valor_total > 0)
);
CREATE INDEX ix_contrato_cliente ON contrato_honorarios (cliente_id);

CREATE TABLE parcela (
    id          BIGSERIAL PRIMARY KEY,
    contrato_id BIGINT         NOT NULL REFERENCES contrato_honorarios (id),
    numero      INTEGER        NOT NULL,
    vencimento  DATE           NOT NULL,
    valor       NUMERIC(15, 2) NOT NULL,
    pago_em     DATE,
    valor_pago  NUMERIC(15, 2),
    CONSTRAINT uq_parcela UNIQUE (contrato_id, numero),
    CONSTRAINT ck_parcela_valor CHECK (valor > 0),
    CONSTRAINT ck_parcela_pagamento CHECK ((pago_em IS NULL) = (valor_pago IS NULL))
);

CREATE TABLE despesa (
    id           BIGSERIAL PRIMARY KEY,
    cliente_id   BIGINT         NOT NULL REFERENCES cliente (id),
    processo_id  BIGINT REFERENCES processo (id),
    tipo         VARCHAR(10)    NOT NULL,
    descricao    VARCHAR(300)   NOT NULL,
    valor        NUMERIC(15, 2) NOT NULL,
    data         DATE           NOT NULL,
    reembolsavel BOOLEAN        NOT NULL DEFAULT FALSE,
    criado_em    TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT ck_despesa_tipo CHECK (tipo IN ('CUSTAS', 'DESPESA')),
    CONSTRAINT ck_despesa_valor CHECK (valor > 0)
);
CREATE INDEX ix_despesa_cliente ON despesa (cliente_id);

CREATE TABLE honorario_variavel (
    id             BIGSERIAL PRIMARY KEY,
    cliente_id     BIGINT         NOT NULL REFERENCES cliente (id),
    processo_id    BIGINT REFERENCES processo (id),
    tipo           VARCHAR(12)    NOT NULL,
    descricao      VARCHAR(300)   NOT NULL,
    valor_previsto NUMERIC(15, 2) NOT NULL,
    valor_recebido NUMERIC(15, 2),
    recebido_em    DATE,
    criado_em      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT ck_variavel_tipo CHECK (tipo IN ('EXITO', 'SUCUMBENCIA')),
    CONSTRAINT ck_variavel_valor CHECK (valor_previsto > 0),
    CONSTRAINT ck_variavel_recebimento CHECK ((recebido_em IS NULL) = (valor_recebido IS NULL))
);
CREATE INDEX ix_variavel_cliente ON honorario_variavel (cliente_id);
