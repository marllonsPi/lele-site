-- Fase 3: feriados e suspensoes (tabela editavel). Seed inicial minimo: ver docs/rag/decisions.md.
CREATE TABLE feriado (
    id           BIGSERIAL PRIMARY KEY,
    escopo       VARCHAR(10)  NOT NULL,
    referencia   VARCHAR(80),
    tipo         VARCHAR(10)  NOT NULL,
    data_inicial DATE         NOT NULL,
    data_final   DATE         NOT NULL,
    descricao    VARCHAR(200) NOT NULL,
    ativo        BOOLEAN      NOT NULL DEFAULT TRUE,
    criado_em    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_feriado_escopo CHECK (escopo IN ('NACIONAL', 'ESTADO', 'MUNICIPIO', 'TRIBUNAL')),
    CONSTRAINT ck_feriado_tipo CHECK (tipo IN ('FERIADO', 'SUSPENSAO')),
    CONSTRAINT ck_feriado_periodo CHECK (data_final >= data_inicial),
    CONSTRAINT ck_feriado_referencia CHECK ((escopo = 'NACIONAL') = (referencia IS NULL))
);
CREATE INDEX ix_feriado_periodo ON feriado (data_inicial, data_final) WHERE ativo;

INSERT INTO feriado (escopo, tipo, data_inicial, data_final, descricao) VALUES
 ('NACIONAL','FERIADO','2026-01-01','2026-01-01','Confraternizacao Universal'),
 ('NACIONAL','FERIADO','2026-04-03','2026-04-03','Sexta-feira Santa'),
 ('NACIONAL','FERIADO','2026-04-21','2026-04-21','Tiradentes'),
 ('NACIONAL','FERIADO','2026-05-01','2026-05-01','Dia do Trabalho'),
 ('NACIONAL','FERIADO','2026-09-07','2026-09-07','Independencia do Brasil'),
 ('NACIONAL','FERIADO','2026-10-12','2026-10-12','Nossa Senhora Aparecida'),
 ('NACIONAL','FERIADO','2026-11-02','2026-11-02','Finados'),
 ('NACIONAL','FERIADO','2026-11-15','2026-11-15','Proclamacao da Republica'),
 ('NACIONAL','FERIADO','2026-11-20','2026-11-20','Consciencia Negra'),
 ('NACIONAL','FERIADO','2026-12-25','2026-12-25','Natal'),
 ('NACIONAL','FERIADO','2027-01-01','2027-01-01','Confraternizacao Universal'),
 ('NACIONAL','FERIADO','2027-03-26','2027-03-26','Sexta-feira Santa'),
 ('NACIONAL','FERIADO','2027-04-21','2027-04-21','Tiradentes'),
 ('NACIONAL','FERIADO','2027-05-01','2027-05-01','Dia do Trabalho'),
 ('NACIONAL','FERIADO','2027-09-07','2027-09-07','Independencia do Brasil'),
 ('NACIONAL','FERIADO','2027-10-12','2027-10-12','Nossa Senhora Aparecida'),
 ('NACIONAL','FERIADO','2027-11-02','2027-11-02','Finados'),
 ('NACIONAL','FERIADO','2027-11-15','2027-11-15','Proclamacao da Republica'),
 ('NACIONAL','FERIADO','2027-11-20','2027-11-20','Consciencia Negra'),
 ('NACIONAL','FERIADO','2027-12-25','2027-12-25','Natal'),
 -- Suspensao dos prazos de 20/12 a 20/01 (CPC art. 220)
 ('NACIONAL','SUSPENSAO','2025-12-20','2026-01-20','Suspensao de prazos (20/12 a 20/01)'),
 ('NACIONAL','SUSPENSAO','2026-12-20','2027-01-20','Suspensao de prazos (20/12 a 20/01)'),
 ('NACIONAL','SUSPENSAO','2027-12-20','2028-01-20','Suspensao de prazos (20/12 a 20/01)');
