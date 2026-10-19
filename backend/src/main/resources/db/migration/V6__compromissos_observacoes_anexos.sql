-- Fase 4: audiencias e atendimentos, observacoes (historico imutavel) e anexos.
ALTER TABLE agendamento ALTER COLUMN processo_id DROP NOT NULL;
ALTER TABLE agendamento
    ADD COLUMN inicio_em             TIMESTAMPTZ,
    ADD COLUMN duracao_min           INTEGER,
    ADD COLUMN modalidade            VARCHAR(10),
    ADD COLUMN local_ou_link         VARCHAR(300),
    ADD COLUMN cliente_id            BIGINT REFERENCES cliente (id),
    ADD COLUMN advogado_atendente_id BIGINT REFERENCES usuario (id);

-- Todo agendamento tem processo, salvo atendimento so com cliente (invariante 8)
ALTER TABLE agendamento ADD CONSTRAINT ck_agendamento_processo CHECK (processo_id IS NOT NULL OR tipo = 'ATD');
ALTER TABLE agendamento ADD CONSTRAINT ck_agendamento_modalidade
    CHECK (modalidade IS NULL OR modalidade IN ('PRESENCIAL', 'ONLINE', 'TELEFONE'));
ALTER TABLE agendamento ADD CONSTRAINT ck_agendamento_compromisso CHECK (
    tipo NOT IN ('AUD', 'ATD') OR (inicio_em IS NOT NULL AND duracao_min > 0 AND modalidade IS NOT NULL));
ALTER TABLE agendamento ADD CONSTRAINT ck_agendamento_aud_modalidade
    CHECK (tipo <> 'AUD' OR modalidade IN ('PRESENCIAL', 'ONLINE'));
ALTER TABLE agendamento ADD CONSTRAINT ck_agendamento_atd
    CHECK (tipo <> 'ATD' OR (cliente_id IS NOT NULL AND advogado_atendente_id IS NOT NULL));
CREATE INDEX ix_agendamento_inicio_em ON agendamento (inicio_em) WHERE inicio_em IS NOT NULL;

CREATE TABLE observacao_agendamento (
    id             BIGSERIAL PRIMARY KEY,
    agendamento_id BIGINT      NOT NULL REFERENCES agendamento (id),
    autor_id       BIGINT      NOT NULL REFERENCES usuario (id),
    texto          TEXT        NOT NULL,
    criado_em      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_observacao_texto CHECK (length(btrim(texto)) > 0)
);
CREATE INDEX ix_observacao_agendamento ON observacao_agendamento (agendamento_id, criado_em);

CREATE FUNCTION observacao_imutavel() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'observacao_agendamento e somente de insercao';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_observacao_imutavel
    BEFORE UPDATE OR DELETE ON observacao_agendamento
    FOR EACH ROW EXECUTE FUNCTION observacao_imutavel();

CREATE TABLE anexo (
    id             BIGSERIAL PRIMARY KEY,
    processo_id    BIGINT REFERENCES processo (id),
    agendamento_id BIGINT REFERENCES agendamento (id),
    nome_original  VARCHAR(255) NOT NULL,
    tamanho        BIGINT       NOT NULL,
    caminho        VARCHAR(80)  NOT NULL,
    autor_id       BIGINT       NOT NULL REFERENCES usuario (id),
    criado_em      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_anexo_dono CHECK (processo_id IS NOT NULL OR agendamento_id IS NOT NULL),
    CONSTRAINT uq_anexo_caminho UNIQUE (caminho)
);
CREATE INDEX ix_anexo_processo ON anexo (processo_id);
CREATE INDEX ix_anexo_agendamento ON anexo (agendamento_id);
