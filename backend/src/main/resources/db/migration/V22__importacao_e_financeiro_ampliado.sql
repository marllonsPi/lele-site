-- Importacao da planilha financeira e do Google Drive (previa, confirmacao, historico) e ampliacao do financeiro:
-- situacao/forma de pagamento da parcela, origem dos lancamentos e despesas do escritorio. So socias acessam (docs/03).

-- ---- parcela: situacao explicita (paga, parcial, cancelada), forma de pagamento e rastro de origem
ALTER TABLE parcela ADD COLUMN situacao        VARCHAR(10) NOT NULL DEFAULT 'ABERTA';
ALTER TABLE parcela ADD COLUMN forma_pagamento VARCHAR(30);
ALTER TABLE parcela ADD COLUMN rotulo          VARCHAR(80);
ALTER TABLE parcela ADD COLUMN origem          VARCHAR(10) NOT NULL DEFAULT 'MANUAL';
ALTER TABLE parcela ADD COLUMN chave_origem    VARCHAR(250);
ALTER TABLE parcela ADD COLUMN hash_origem     VARCHAR(64);
UPDATE parcela SET situacao = 'PAGA' WHERE pago_em IS NOT NULL;
ALTER TABLE parcela DROP CONSTRAINT ck_parcela_pagamento;
ALTER TABLE parcela ADD CONSTRAINT ck_parcela_situacao CHECK (situacao IN ('ABERTA', 'PAGA', 'PARCIAL', 'CANCELADA'));
-- Valor pago existe so quando paga/parcial; a data pode faltar (planilha nao registra a data do pagamento).
ALTER TABLE parcela ADD CONSTRAINT ck_parcela_pagamento CHECK ((situacao IN ('PAGA', 'PARCIAL')) = (valor_pago IS NOT NULL)
    AND (pago_em IS NULL OR valor_pago IS NOT NULL));
ALTER TABLE parcela ADD CONSTRAINT ck_parcela_origem CHECK (origem IN ('MANUAL', 'PLANILHA', 'DRIVE'));
CREATE UNIQUE INDEX uq_parcela_chave_origem ON parcela (chave_origem) WHERE chave_origem IS NOT NULL;
CREATE INDEX ix_parcela_vencimento ON parcela (vencimento);

-- ---- contrato: origem, plano de pagamento descrito, exito, assinatura
ALTER TABLE contrato_honorarios ADD COLUMN origem            VARCHAR(10) NOT NULL DEFAULT 'MANUAL';
ALTER TABLE contrato_honorarios ADD COLUMN chave_origem      VARCHAR(250);
ALTER TABLE contrato_honorarios ADD COLUMN hash_origem       VARCHAR(64);
ALTER TABLE contrato_honorarios ADD COLUMN forma_pagamento   TEXT;
ALTER TABLE contrato_honorarios ADD COLUMN percentual_exito  NUMERIC(5, 2);
ALTER TABLE contrato_honorarios ADD COLUMN situacao_origem   VARCHAR(40);
ALTER TABLE contrato_honorarios ADD COLUMN data_assinatura   DATE;
ALTER TABLE contrato_honorarios ADD COLUMN a_conferir        BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE contrato_honorarios ADD COLUMN numero_parcelas   INTEGER;
-- Contrato so de exito ("ao final do processo") nao tem valor fixo.
ALTER TABLE contrato_honorarios DROP CONSTRAINT ck_contrato_valor;
ALTER TABLE contrato_honorarios ADD CONSTRAINT ck_contrato_valor CHECK (valor_total >= 0);
ALTER TABLE contrato_honorarios ADD CONSTRAINT ck_contrato_origem CHECK (origem IN ('MANUAL', 'PLANILHA', 'DRIVE'));
CREATE UNIQUE INDEX uq_contrato_chave_origem ON contrato_honorarios (chave_origem) WHERE chave_origem IS NOT NULL;

-- ---- despesas do escritorio (sem cliente): lembrancas, custos mensais etc.
CREATE TABLE despesa_escritorio (
    id           BIGSERIAL PRIMARY KEY,
    data         DATE           NOT NULL,
    descricao    VARCHAR(300)   NOT NULL,
    categoria    VARCHAR(80),
    valor        NUMERIC(15, 2) NOT NULL,
    forma_pagamento VARCHAR(30),
    origem       VARCHAR(10)    NOT NULL DEFAULT 'MANUAL',
    chave_origem VARCHAR(250),
    hash_origem  VARCHAR(64),
    criado_em    TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT ck_despesa_escritorio_valor CHECK (valor > 0),
    CONSTRAINT ck_despesa_escritorio_origem CHECK (origem IN ('MANUAL', 'PLANILHA', 'DRIVE'))
);
CREATE UNIQUE INDEX uq_despesa_escritorio_chave ON despesa_escritorio (chave_origem) WHERE chave_origem IS NOT NULL;

-- ---- despesa de cliente: rastro de origem
ALTER TABLE despesa ADD COLUMN origem       VARCHAR(10) NOT NULL DEFAULT 'MANUAL';
ALTER TABLE despesa ADD COLUMN chave_origem VARCHAR(250);
ALTER TABLE despesa ADD COLUMN hash_origem  VARCHAR(64);
CREATE UNIQUE INDEX uq_despesa_chave_origem ON despesa (chave_origem) WHERE chave_origem IS NOT NULL;

-- ---- cliente e processo: rastro de origem e conferencia manual
ALTER TABLE cliente ADD COLUMN a_conferir BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE cliente ADD COLUMN rg VARCHAR(30);
ALTER TABLE cliente ADD COLUMN estado_civil VARCHAR(30);
ALTER TABLE cliente ADD COLUMN origem VARCHAR(10) NOT NULL DEFAULT 'MANUAL';

-- ---- importacao: previa -> confirmacao -> relatorio; cada item guarda as duas versoes (importada e existente)
CREATE TABLE importacao (
    id                   BIGSERIAL PRIMARY KEY,
    origem               VARCHAR(10)  NOT NULL,
    modo                 VARCHAR(10)  NOT NULL DEFAULT 'MANUAL',
    arquivo_nome         VARCHAR(300),
    status               VARCHAR(12)  NOT NULL DEFAULT 'PREVIA',
    responsavel_padrao_id BIGINT REFERENCES usuario (id),
    criado_por           BIGINT REFERENCES usuario (id),
    criado_em            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    confirmado_por       BIGINT REFERENCES usuario (id),
    confirmado_em        TIMESTAMPTZ,
    relatorio            TEXT,
    CONSTRAINT ck_importacao_origem CHECK (origem IN ('PLANILHA', 'DRIVE')),
    CONSTRAINT ck_importacao_modo CHECK (modo IN ('MANUAL', 'DIARIA')),
    CONSTRAINT ck_importacao_status CHECK (status IN ('PREVIA', 'CONCLUIDA', 'CANCELADA'))
);

CREATE TABLE importacao_item (
    id            BIGSERIAL PRIMARY KEY,
    importacao_id BIGINT       NOT NULL REFERENCES importacao (id),
    ordem         INTEGER      NOT NULL,
    tipo          VARCHAR(12)  NOT NULL,
    chave         VARCHAR(250) NOT NULL,
    rotulo        VARCHAR(300) NOT NULL,
    acao          VARCHAR(12)  NOT NULL,
    divergencias  TEXT         NOT NULL DEFAULT '[]',
    importado     TEXT         NOT NULL DEFAULT '{}',
    existente     TEXT,
    decisao       VARCHAR(10),
    ajustes       TEXT         NOT NULL DEFAULT '{}',
    resultado     VARCHAR(12),
    motivo        TEXT,
    aplicado_em   TIMESTAMPTZ,
    CONSTRAINT ck_importacao_item_tipo CHECK (tipo IN ('CLIENTE', 'CONTRATO', 'PARCELA', 'PROCESSO', 'DESPESA', 'DOCUMENTO')),
    CONSTRAINT ck_importacao_item_acao CHECK (acao IN ('CRIAR', 'CONFLITO', 'SEM_MUDANCA', 'IGNORAR')),
    CONSTRAINT ck_importacao_item_decisao CHECK (decisao IS NULL OR decisao IN ('APLICAR', 'MANTER')),
    CONSTRAINT ck_importacao_item_resultado CHECK (resultado IS NULL OR resultado IN ('APLICADO', 'IGNORADO', 'ERRO'))
);
CREATE INDEX ix_importacao_item ON importacao_item (importacao_id, ordem);
