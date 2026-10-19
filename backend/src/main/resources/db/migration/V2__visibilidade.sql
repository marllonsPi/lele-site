-- Fase 1: regras de visibilidade configuraveis (observador ve o que esta em nome do titular).
CREATE TABLE regra_visibilidade (
    id            BIGSERIAL PRIMARY KEY,
    observador_id BIGINT      NOT NULL REFERENCES usuario (id),
    titular_id    BIGINT      NOT NULL REFERENCES usuario (id),
    escopo        VARCHAR(20) NOT NULL,
    criado_em     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_regra_escopo CHECK (escopo IN ('PUBLICACAO', 'AGENDAMENTO', 'PROCESSO')),
    CONSTRAINT uq_regra_visibilidade UNIQUE (observador_id, titular_id, escopo)
);
CREATE INDEX ix_regra_visibilidade_observador ON regra_visibilidade (observador_id, escopo);
