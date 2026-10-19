-- Fase 8: atendimento (contatos com o cliente) e andamentos pendentes de repasse.
CREATE TABLE atendimento (
    id             BIGSERIAL PRIMARY KEY,
    cliente_id     BIGINT       NOT NULL REFERENCES cliente (id),
    processo_id    BIGINT REFERENCES processo (id),
    momento        TIMESTAMPTZ  NOT NULL,
    canal          VARCHAR(12)  NOT NULL,
    atendente_id   BIGINT       NOT NULL REFERENCES usuario (id),
    resumo         TEXT         NOT NULL,
    origem         VARCHAR(12)  NOT NULL,
    agendamento_id BIGINT UNIQUE REFERENCES agendamento (id),
    criado_por     BIGINT REFERENCES usuario (id),
    criado_em      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_atendimento_canal CHECK (canal IN ('PRESENCIAL', 'ONLINE', 'TELEFONE', 'WHATSAPP', 'EMAIL', 'OUTRO')),
    CONSTRAINT ck_atendimento_origem CHECK (origem IN ('AGENDAMENTO', 'AVULSO')),
    CONSTRAINT ck_atendimento_agendamento CHECK ((origem = 'AGENDAMENTO') = (agendamento_id IS NOT NULL)),
    CONSTRAINT ck_atendimento_resumo CHECK (length(btrim(resumo)) > 0)
);
CREATE INDEX ix_atendimento_cliente ON atendimento (cliente_id, momento);

CREATE TABLE andamento (
    id            BIGSERIAL PRIMARY KEY,
    processo_id   BIGINT      NOT NULL REFERENCES processo (id),
    data          DATE        NOT NULL,
    descricao     TEXT        NOT NULL,
    origem        VARCHAR(10) NOT NULL,
    publicacao_id BIGINT UNIQUE REFERENCES publicacao (id),
    repassado     BOOLEAN     NOT NULL DEFAULT FALSE,
    repassado_em  TIMESTAMPTZ,
    repassado_via VARCHAR(20),
    repassado_por BIGINT REFERENCES usuario (id),
    criado_por    BIGINT REFERENCES usuario (id),
    criado_em     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_andamento_origem CHECK (origem IN ('PUBLICACAO', 'MANUAL')),
    CONSTRAINT ck_andamento_repasse CHECK (repassado = (repassado_em IS NOT NULL)),
    CONSTRAINT ck_andamento_descricao CHECK (length(btrim(descricao)) > 0)
);
CREATE INDEX ix_andamento_processo ON andamento (processo_id, repassado);
