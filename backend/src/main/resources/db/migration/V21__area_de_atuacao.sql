-- Area de atuacao: cliente (varias), processo e contrato (uma). Colunas anteriores ficam nulas ate serem preenchidas
-- (cadastro incompleto); o servico exige a area em todo salvamento novo.
CREATE TABLE cliente_area (
    cliente_id BIGINT      NOT NULL REFERENCES cliente (id),
    area       VARCHAR(20) NOT NULL,
    PRIMARY KEY (cliente_id, area),
    CONSTRAINT ck_cliente_area CHECK (area IN ('TRABALHISTA', 'PREVIDENCIARIO', 'FAMILIA', 'SUCESSAO', 'IMOBILIARIO',
        'CONDOMINIAL', 'MILITAR', 'CRIMINAL', 'ADMINISTRATIVO', 'CIVEL'))
);

ALTER TABLE processo ADD COLUMN area VARCHAR(20);
ALTER TABLE processo ADD CONSTRAINT ck_processo_area CHECK (area IS NULL OR area IN ('TRABALHISTA', 'PREVIDENCIARIO',
    'FAMILIA', 'SUCESSAO', 'IMOBILIARIO', 'CONDOMINIAL', 'MILITAR', 'CRIMINAL', 'ADMINISTRATIVO', 'CIVEL'));
CREATE INDEX ix_processo_area ON processo (area);

ALTER TABLE contrato_honorarios ADD COLUMN area VARCHAR(20);
ALTER TABLE contrato_honorarios ADD CONSTRAINT ck_contrato_area CHECK (area IS NULL OR area IN ('TRABALHISTA',
    'PREVIDENCIARIO', 'FAMILIA', 'SUCESSAO', 'IMOBILIARIO', 'CONDOMINIAL', 'MILITAR', 'CRIMINAL', 'ADMINISTRATIVO', 'CIVEL'));
CREATE INDEX ix_contrato_area ON contrato_honorarios (area);
