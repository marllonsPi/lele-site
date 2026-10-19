-- Fase 10: relatorio ao cliente (rascunho -> revisao -> aprovado -> enviado) com copia guardada.
CREATE TABLE relatorio_cliente (
    id             BIGSERIAL PRIMARY KEY,
    cliente_id     BIGINT      NOT NULL REFERENCES cliente (id),
    periodo_inicio DATE,
    periodo_fim    DATE        NOT NULL,
    estado         VARCHAR(12) NOT NULL DEFAULT 'RASCUNHO',
    incluir_financeiro BOOLEAN NOT NULL DEFAULT FALSE,
    conteudo       TEXT        NOT NULL,
    andamento_ids  BIGINT[]    NOT NULL DEFAULT '{}',
    criado_por     BIGINT      NOT NULL REFERENCES usuario (id),
    criado_em      TIMESTAMPTZ NOT NULL DEFAULT now(),
    aprovado_por   BIGINT REFERENCES usuario (id),
    aprovado_em    TIMESTAMPTZ,
    enviado_por    BIGINT REFERENCES usuario (id),
    enviado_em     TIMESTAMPTZ,
    canal          VARCHAR(12),
    pdf            BYTEA,
    CONSTRAINT ck_relatorio_estado CHECK (estado IN ('RASCUNHO', 'EM_REVISAO', 'APROVADO', 'ENVIADO')),
    CONSTRAINT ck_relatorio_canal CHECK (canal IS NULL OR canal IN ('EMAIL', 'WHATSAPP', 'IMPRESSAO')),
    -- Nenhum relatorio vai ao cliente sem aprovacao (invariante 6), garantido no banco
    CONSTRAINT ck_relatorio_aprovacao CHECK (estado NOT IN ('APROVADO', 'ENVIADO') OR (aprovado_por IS NOT NULL AND aprovado_em IS NOT NULL)),
    CONSTRAINT ck_relatorio_envio CHECK (estado <> 'ENVIADO' OR (pdf IS NOT NULL AND enviado_em IS NOT NULL AND enviado_por IS NOT NULL AND canal IS NOT NULL))
);
CREATE INDEX ix_relatorio_cliente ON relatorio_cliente (cliente_id, criado_em);
