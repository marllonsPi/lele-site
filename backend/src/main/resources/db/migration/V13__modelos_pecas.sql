-- Fase 11: biblioteca de modelos de pecas por tipo (INI, CON, MAN, REC) e area do direito.
CREATE TABLE modelo_peca (
    id            BIGSERIAL PRIMARY KEY,
    tipo          VARCHAR(3)   NOT NULL,
    area_direito  VARCHAR(60)  NOT NULL,
    titulo        VARCHAR(200) NOT NULL,
    nome_original VARCHAR(255) NOT NULL,
    tamanho       BIGINT       NOT NULL,
    caminho       VARCHAR(80)  NOT NULL UNIQUE,
    ativo         BOOLEAN      NOT NULL DEFAULT TRUE,
    criado_por    BIGINT       NOT NULL REFERENCES usuario (id),
    criado_em     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_modelo_tipo CHECK (tipo IN ('INI', 'CON', 'MAN', 'REC'))
);
CREATE INDEX ix_modelo_tipo_area ON modelo_peca (tipo, area_direito) WHERE ativo;
