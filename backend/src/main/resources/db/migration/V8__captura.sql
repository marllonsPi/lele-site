-- Fase 6: captura de publicacoes (DJEN, Domicilio, e-mail), cruzamento, fila "A classificar".
CREATE TABLE execucao_captura (
    id           BIGSERIAL PRIMARY KEY,
    fonte        VARCHAR(10) NOT NULL,
    inicio       TIMESTAMPTZ NOT NULL DEFAULT now(),
    fim          TIMESTAMPTZ,
    resultado    VARCHAR(10) NOT NULL DEFAULT 'EM_ANDAMENTO',
    itens_lidos  INTEGER     NOT NULL DEFAULT 0,
    itens_novos  INTEGER     NOT NULL DEFAULT 0,
    erro         TEXT,
    CONSTRAINT ck_execucao_fonte CHECK (fonte IN ('DJEN', 'DOMICILIO', 'EMAIL')),
    CONSTRAINT ck_execucao_resultado CHECK (resultado IN ('EM_ANDAMENTO', 'OK', 'ERRO'))
);

CREATE TABLE publicacao_bruta (
    id               BIGSERIAL PRIMARY KEY,
    fonte            VARCHAR(10)  NOT NULL,
    id_externo       VARCHAR(100) NOT NULL,
    titular_id       BIGINT       NOT NULL REFERENCES usuario (id),
    numero_processo  VARCHAR(30),
    tribunal         VARCHAR(40),
    data_publicacao  DATE         NOT NULL,
    conteudo         TEXT         NOT NULL,
    hash             VARCHAR(64)  NOT NULL,
    recebido_em      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_bruta_fonte CHECK (fonte IN ('DJEN', 'DOMICILIO', 'EMAIL')),
    -- idempotencia: o mesmo item da mesma fonte, para o mesmo titular, entra uma unica vez
    CONSTRAINT uq_bruta_fonte_externo UNIQUE (fonte, id_externo, titular_id)
);

CREATE TABLE publicacao (
    id              BIGSERIAL PRIMARY KEY,
    numero_processo VARCHAR(40)  NOT NULL,
    titular_id      BIGINT       NOT NULL REFERENCES usuario (id),
    data_publicacao DATE         NOT NULL,
    tribunal        VARCHAR(40),
    resumo          VARCHAR(400),
    estado          VARCHAR(30)  NOT NULL DEFAULT 'A_CLASSIFICAR',
    processo_id     BIGINT REFERENCES processo (id),
    agendamento_id  BIGINT REFERENCES agendamento (id),
    motivo_descarte TEXT,
    criado_em       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_publicacao_estado CHECK (estado IN ('A_CLASSIFICAR', 'CLASSIFICADA', 'DESCARTADA_COM_REGISTRO')),
    CONSTRAINT ck_publicacao_descarte CHECK (estado <> 'DESCARTADA_COM_REGISTRO' OR length(btrim(coalesce(motivo_descarte, ''))) > 0),
    -- cruzamento: a mesma publicacao vinda de varias fontes e uma so (invariante 4)
    CONSTRAINT uq_publicacao UNIQUE (titular_id, numero_processo, data_publicacao)
);
CREATE INDEX ix_publicacao_estado ON publicacao (estado);

CREATE TABLE publicacao_fonte (
    publicacao_id BIGINT      NOT NULL REFERENCES publicacao (id),
    fonte         VARCHAR(10) NOT NULL,
    bruta_id      BIGINT      NOT NULL REFERENCES publicacao_bruta (id),
    PRIMARY KEY (publicacao_id, fonte)
);

-- Nada e descartado sem registro (invariante 3)
CREATE TABLE item_ignorado (
    id        BIGSERIAL PRIMARY KEY,
    bruta_id  BIGINT      NOT NULL REFERENCES publicacao_bruta (id),
    regra     VARCHAR(80) NOT NULL,
    motivo    VARCHAR(300) NOT NULL,
    momento   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE FUNCTION item_ignorado_imutavel() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'item_ignorado e somente de insercao';
END;
$$ LANGUAGE plpgsql;
CREATE TRIGGER trg_item_ignorado_imutavel BEFORE UPDATE OR DELETE ON item_ignorado
    FOR EACH ROW EXECUTE FUNCTION item_ignorado_imutavel();

-- Filtros configuraveis por dados (ex.: ignorar push de e-mail de TRT, exceto TRT-10)
CREATE TABLE filtro_captura (
    id               BIGSERIAL PRIMARY KEY,
    usuario_id       BIGINT      NOT NULL REFERENCES usuario (id),
    fonte            VARCHAR(10) NOT NULL,
    tribunal_prefixo VARCHAR(20) NOT NULL,
    excecoes         VARCHAR(200),
    ativo            BOOLEAN     NOT NULL DEFAULT TRUE,
    criado_em        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_filtro_fonte CHECK (fonte IN ('DJEN', 'DOMICILIO', 'EMAIL'))
);
