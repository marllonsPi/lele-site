-- Fase 3: agendamento (nucleo de prazos). AUD/ATD e demais campos entram na Fase 4 por migracao aditiva.
CREATE TABLE agendamento (
    id             BIGSERIAL PRIMARY KEY,
    processo_id    BIGINT       NOT NULL REFERENCES processo (id),
    titular_id     BIGINT       NOT NULL REFERENCES usuario (id),
    tipo           VARCHAR(3)   NOT NULL,
    descricao      VARCHAR(300),
    responsavel_id BIGINT       NOT NULL REFERENCES usuario (id),
    criado_por     BIGINT       NOT NULL REFERENCES usuario (id),
    urgente        BOOLEAN      NOT NULL DEFAULT FALSE,
    status         VARCHAR(40)  NOT NULL,
    inicio_prazo   DATE,
    prazo_interno  DATE,
    prazo_fatal    DATE,
    lancado_por    BIGINT REFERENCES usuario (id),
    conferido_por  BIGINT REFERENCES usuario (id),
    conferido_em   TIMESTAMPTZ,
    criado_em      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_agendamento_tipo CHECK (tipo IN ('CTR', 'INI', 'CON', 'MAN', 'AUD', 'REC', 'ATD')),
    CONSTRAINT ck_agendamento_status CHECK (status IN ('AGUARDANDO_CONFERENCIA_PRAZO', 'PENDENTE', 'FINALIZADO',
        'EM_CONFERENCIA_SUPERVISOR', 'EM_CONFERENCIA_SOCIA', 'CONFERIDO', 'DEVOLVIDO')),
    -- Dupla conferencia no banco (invariante 2)
    CONSTRAINT ck_agendamento_dupla_conferencia CHECK (conferido_por IS NULL OR conferido_por <> lancado_por),
    CONSTRAINT ck_agendamento_conferencia_completa CHECK ((conferido_por IS NULL) = (conferido_em IS NULL)),
    -- Prazo: tres datas coerentes; AUD/ATD nao seguem a regra dos 3 dias uteis (invariante 5)
    CONSTRAINT ck_agendamento_datas CHECK (
        tipo IN ('AUD', 'ATD')
        OR (prazo_fatal IS NOT NULL AND prazo_interno IS NOT NULL AND inicio_prazo IS NOT NULL
            AND inicio_prazo <= prazo_fatal AND prazo_interno <= prazo_fatal AND lancado_por IS NOT NULL)),
    -- Prazo liberado para trabalho so depois de conferido
    CONSTRAINT ck_agendamento_pendente_conferido CHECK (
        tipo IN ('AUD', 'ATD') OR status = 'AGUARDANDO_CONFERENCIA_PRAZO' OR conferido_por IS NOT NULL)
);
CREATE INDEX ix_agendamento_titular ON agendamento (titular_id);
CREATE INDEX ix_agendamento_responsavel ON agendamento (responsavel_id);
CREATE INDEX ix_agendamento_processo ON agendamento (processo_id);
