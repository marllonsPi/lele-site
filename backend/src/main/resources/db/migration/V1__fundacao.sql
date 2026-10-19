-- Fase 0: usuarios, OABs e auditoria imutavel.

CREATE TABLE usuario (
    id            BIGSERIAL PRIMARY KEY,
    nome          VARCHAR(200) NOT NULL,
    email         VARCHAR(254) NOT NULL,
    senha_hash    VARCHAR(100) NOT NULL,
    perfil        VARCHAR(20)  NOT NULL,
    ativo         BOOLEAN      NOT NULL DEFAULT TRUE,
    supervisor_id BIGINT REFERENCES usuario (id),
    criado_em     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_usuario_email UNIQUE (email),
    CONSTRAINT ck_usuario_perfil CHECK (perfil IN ('SOCIA', 'ADVOGADO', 'ASSISTENTE', 'ESTAGIARIO')),
    -- supervisor so existe para estagiario
    CONSTRAINT ck_usuario_supervisor CHECK (supervisor_id IS NULL OR perfil = 'ESTAGIARIO'),
    CONSTRAINT ck_usuario_nao_supervisor_de_si CHECK (supervisor_id IS NULL OR supervisor_id <> id)
);

CREATE TABLE oab (
    id         BIGSERIAL PRIMARY KEY,
    usuario_id BIGINT      NOT NULL REFERENCES usuario (id),
    numero     VARCHAR(20) NOT NULL,
    uf         CHAR(2)     NOT NULL,
    principal  BOOLEAN     NOT NULL DEFAULT FALSE,
    ativa      BOOLEAN     NOT NULL DEFAULT TRUE,
    criado_em  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_oab_numero_uf UNIQUE (numero, uf)
);
CREATE INDEX ix_oab_usuario ON oab (usuario_id);
-- no maximo uma OAB principal por usuario
CREATE UNIQUE INDEX uq_oab_principal ON oab (usuario_id) WHERE principal;

CREATE TABLE log_auditoria (
    id          BIGSERIAL PRIMARY KEY,
    momento     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    usuario_id  BIGINT,
    acao        VARCHAR(60)  NOT NULL,
    entidade    VARCHAR(60),
    entidade_id VARCHAR(60),
    detalhe     TEXT,
    ip          VARCHAR(45)
);
CREATE INDEX ix_log_auditoria_momento ON log_auditoria (momento);
CREATE INDEX ix_log_auditoria_entidade ON log_auditoria (entidade, entidade_id);

-- Auditoria somente de insercao.
CREATE FUNCTION log_auditoria_imutavel() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'log_auditoria e somente de insercao';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_log_auditoria_imutavel
    BEFORE UPDATE OR DELETE ON log_auditoria
    FOR EACH ROW EXECUTE FUNCTION log_auditoria_imutavel();
