-- Fase 5: finalizacao e conferencia (estagiario -> supervisor -> socia).
ALTER TABLE agendamento
    ADD COLUMN finalizado_por BIGINT REFERENCES usuario (id),
    ADD COLUMN finalizado_em  TIMESTAMPTZ;
ALTER TABLE agendamento ADD CONSTRAINT ck_agendamento_finalizacao
    CHECK ((finalizado_por IS NULL) = (finalizado_em IS NULL));
-- Estado de conferencia so existe depois de finalizado
ALTER TABLE agendamento ADD CONSTRAINT ck_agendamento_status_finalizado
    CHECK (status NOT IN ('FINALIZADO', 'EM_CONFERENCIA_SUPERVISOR', 'EM_CONFERENCIA_SOCIA', 'CONFERIDO')
           OR finalizado_por IS NOT NULL);

CREATE TABLE conferencia (
    id             BIGSERIAL PRIMARY KEY,
    agendamento_id BIGINT      NOT NULL REFERENCES agendamento (id),
    etapa          VARCHAR(10) NOT NULL,
    resultado      VARCHAR(10) NOT NULL,
    motivo         TEXT,
    usuario_id     BIGINT      NOT NULL REFERENCES usuario (id),
    criado_em      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_conferencia_etapa CHECK (etapa IN ('SUPERVISOR', 'SOCIA')),
    CONSTRAINT ck_conferencia_resultado CHECK (resultado IN ('CONFERIDO', 'DEVOLVIDO')),
    CONSTRAINT ck_conferencia_motivo CHECK (resultado <> 'DEVOLVIDO' OR length(btrim(coalesce(motivo, ''))) > 0)
);
CREATE INDEX ix_conferencia_agendamento ON conferencia (agendamento_id);

-- Historico de conferencia imutavel (mesma regra da auditoria)
CREATE FUNCTION conferencia_imutavel() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'conferencia e somente de insercao';
END;
$$ LANGUAGE plpgsql;
CREATE TRIGGER trg_conferencia_imutavel BEFORE UPDATE OR DELETE ON conferencia
    FOR EACH ROW EXECUTE FUNCTION conferencia_imutavel();
