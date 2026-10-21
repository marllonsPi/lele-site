-- Sugestao de honorarios: tabela da OAB (por seccional) e tabela do escritorio, separadas e versionadas; propostas de atendimento
-- (valor sugerido x valor fechado), justificativa abaixo do minimo da OAB e modelo da minuta de contrato. Somente socias editam/veem tudo.
CREATE TABLE tabela_honorarios_versao (
    id                   BIGSERIAL PRIMARY KEY,
    tabela               VARCHAR(10)   NOT NULL,
    seccional            VARCHAR(2),
    referencia           VARCHAR(80),
    valor_urh            NUMERIC(12, 4),
    fator_escritorio     NUMERIC(6, 3),
    desconto_padrao      NUMERIC(5, 4),
    desconto_trabalhista NUMERIC(5, 4),
    desconto_pacote      NUMERIC(5, 4) NOT NULL DEFAULT 0,
    vigente              BOOLEAN       NOT NULL DEFAULT TRUE,
    arquivo_nome         VARCHAR(300),
    criado_por           BIGINT REFERENCES usuario (id),
    criado_em            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    substituida_em       TIMESTAMPTZ,
    CONSTRAINT ck_tabela_versao_tabela CHECK (tabela IN ('OAB', 'ESCRITORIO')),
    CONSTRAINT ck_tabela_versao_seccional CHECK ((tabela = 'OAB' AND seccional IN ('DF', 'SP')) OR (tabela = 'ESCRITORIO' AND seccional IS NULL)),
    CONSTRAINT ck_tabela_versao_pacote CHECK (desconto_pacote >= 0 AND desconto_pacote <= 1)
);
-- uma versao vigente por tabela (e por seccional, no caso da OAB)
CREATE UNIQUE INDEX uq_tabela_versao_vigente ON tabela_honorarios_versao (tabela, coalesce(seccional, '-')) WHERE vigente;

CREATE TABLE tabela_honorarios_item (
    id                 BIGSERIAL PRIMARY KEY,
    versao_id          BIGINT       NOT NULL REFERENCES tabela_honorarios_versao (id),
    area               VARCHAR(20),
    tipo_servico       VARCHAR(300) NOT NULL,
    fase               VARCHAR(20),
    chave_servico      VARCHAR(64)  NOT NULL,
    item_oab           VARCHAR(30),
    valor_urh          NUMERIC(10, 2),
    -- OAB: valor minimo em R$; ESCRITORIO: valor de tabela do escritorio. Nulo = sem valor fixo (so exito)
    valor              NUMERIC(15, 2),
    derivado           BOOLEAN      NOT NULL DEFAULT FALSE,
    desconto_percentual NUMERIC(5, 4),
    valor_sugerido     NUMERIC(15, 2),
    exito_texto        VARCHAR(300),
    exito_min          NUMERIC(5, 2),
    exito_max          NUMERIC(5, 2),
    parcelamento       VARCHAR(400),
    observacoes        VARCHAR(500),
    ordem              INTEGER      NOT NULL DEFAULT 0,
    ativo              BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT ck_tabela_item_area CHECK (area IS NULL OR area IN ('TRABALHISTA', 'PREVIDENCIARIO', 'FAMILIA', 'SUCESSAO', 'IMOBILIARIO',
        'CONDOMINIAL', 'MILITAR', 'CRIMINAL', 'ADMINISTRATIVO', 'CIVEL')),
    CONSTRAINT ck_tabela_item_fase CHECK (fase IS NULL OR fase IN ('JUDICIAL', 'EXTRAJUDICIAL', 'CONHECIMENTO', 'RECURSO', 'CUMPRIMENTO')),
    CONSTRAINT ck_tabela_item_valor CHECK (valor IS NULL OR valor >= 0)
);
CREATE INDEX ix_tabela_item_versao ON tabela_honorarios_item (versao_id, ordem);
CREATE INDEX ix_tabela_item_chave ON tabela_honorarios_item (chave_servico);

-- proposta = a negociacao de um atendimento: o que foi sugerido x o que foi efetivamente fechado
CREATE TABLE proposta_honorarios (
    id                   BIGSERIAL PRIMARY KEY,
    cliente_id           BIGINT        NOT NULL REFERENCES cliente (id),
    agendamento_id       BIGINT REFERENCES agendamento (id),
    advogado_id          BIGINT        NOT NULL REFERENCES usuario (id),
    seccional            VARCHAR(2)    NOT NULL DEFAULT 'DF',
    status               VARCHAR(12)   NOT NULL DEFAULT 'ABERTA',
    valor_minimo_oab     NUMERIC(15, 2) NOT NULL DEFAULT 0,
    valor_sugerido       NUMERIC(15, 2) NOT NULL DEFAULT 0,
    desconto_pacote      NUMERIC(5, 4)  NOT NULL DEFAULT 0,
    valor_fechado        NUMERIC(15, 2),
    justificativa_tipo   VARCHAR(20),
    justificativa        TEXT,
    entrada              NUMERIC(15, 2),
    parcelas             INTEGER,
    primeiro_vencimento  DATE,
    forma_pagamento      TEXT,
    data_assinatura      DATE,
    criado_em            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    atualizado_em        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    fechado_em           TIMESTAMPTZ,
    CONSTRAINT ck_proposta_status CHECK (status IN ('ABERTA', 'FECHADA', 'NAO_FECHADA')),
    CONSTRAINT ck_proposta_seccional CHECK (seccional IN ('DF', 'SP')),
    CONSTRAINT ck_proposta_fechado CHECK (valor_fechado IS NULL OR valor_fechado >= 0),
    CONSTRAINT ck_proposta_justificativa_tipo CHECK (justificativa_tipo IS NULL OR justificativa_tipo IN ('PERMUTA', 'HIPOSSUFICIENTE', 'PRO_BONO', 'PACOTE', 'OUTRO')),
    -- valor fechado abaixo do minimo da OAB so com justificativa (alerta + registro para as socias)
    CONSTRAINT ck_proposta_abaixo_oab CHECK (valor_fechado IS NULL OR valor_fechado >= valor_minimo_oab
        OR (justificativa_tipo IS NOT NULL AND length(btrim(coalesce(justificativa, ''))) > 0)),
    CONSTRAINT ck_proposta_fechada CHECK (status <> 'FECHADA' OR valor_fechado IS NOT NULL)
);
CREATE UNIQUE INDEX uq_proposta_agendamento ON proposta_honorarios (agendamento_id) WHERE agendamento_id IS NOT NULL;
CREATE INDEX ix_proposta_cliente ON proposta_honorarios (cliente_id);
CREATE INDEX ix_proposta_advogado ON proposta_honorarios (advogado_id, criado_em);

CREATE TABLE proposta_item (
    id                  BIGSERIAL PRIMARY KEY,
    proposta_id         BIGINT       NOT NULL REFERENCES proposta_honorarios (id),
    chave_servico       VARCHAR(64)  NOT NULL,
    area                VARCHAR(20),
    tipo_servico        VARCHAR(300) NOT NULL,
    fase                VARCHAR(20),
    item_oab_id         BIGINT REFERENCES tabela_honorarios_item (id),
    item_escritorio_id  BIGINT REFERENCES tabela_honorarios_item (id),
    minimo_oab          NUMERIC(15, 2),
    valor_tabela        NUMERIC(15, 2),
    valor_sugerido      NUMERIC(15, 2),
    exito_texto         VARCHAR(300),
    exito_min           NUMERIC(5, 2),
    exito_max           NUMERIC(5, 2),
    CONSTRAINT uq_proposta_item UNIQUE (proposta_id, chave_servico)
);

-- contratos lancados a partir de uma proposta (um por area de atuacao)
ALTER TABLE contrato_honorarios ADD COLUMN proposta_id BIGINT REFERENCES proposta_honorarios (id);
CREATE INDEX ix_contrato_proposta ON contrato_honorarios (proposta_id) WHERE proposta_id IS NOT NULL;

-- modelo da minuta do contrato de honorarios (texto com {{campos}}); editavel pelas socias
CREATE TABLE modelo_contrato_honorarios (
    id             BIGSERIAL PRIMARY KEY,
    texto          TEXT        NOT NULL,
    atualizado_por BIGINT REFERENCES usuario (id),
    atualizado_em  TIMESTAMPTZ NOT NULL DEFAULT now()
);
