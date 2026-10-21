-- Pasta do cliente: documentos (contratos de honorarios, procuracoes, documentos pessoais). Contratos de honorarios trazem valores e
-- ficam restritos as socias; os demais seguem a visibilidade do cliente. Arquivos no armazenamento protegido (nome = UUID).
CREATE TABLE documento_cliente (
    id                  BIGSERIAL PRIMARY KEY,
    cliente_id          BIGINT       NOT NULL REFERENCES cliente (id),
    tipo                VARCHAR(20)  NOT NULL,
    nome_original       VARCHAR(255) NOT NULL,
    mime                VARCHAR(100),
    tamanho             BIGINT       NOT NULL,
    caminho             VARCHAR(80)  NOT NULL,
    sha256              VARCHAR(64)  NOT NULL,
    restrito_socias     BOOLEAN      NOT NULL DEFAULT FALSE,
    origem              VARCHAR(10)  NOT NULL DEFAULT 'MANUAL',
    drive_id            VARCHAR(120),
    drive_modificado_em TIMESTAMPTZ,
    autor_id            BIGINT REFERENCES usuario (id),
    criado_em           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_documento_tipo CHECK (tipo IN ('CONTRATO_HONORARIOS', 'PROCURACAO', 'DOCUMENTO_PESSOAL', 'OUTRO')),
    CONSTRAINT ck_documento_origem CHECK (origem IN ('MANUAL', 'DRIVE')),
    CONSTRAINT ck_documento_contrato_restrito CHECK (tipo <> 'CONTRATO_HONORARIOS' OR restrito_socias),
    CONSTRAINT uq_documento_caminho UNIQUE (caminho)
);
CREATE UNIQUE INDEX uq_documento_drive ON documento_cliente (drive_id) WHERE drive_id IS NOT NULL;
CREATE INDEX ix_documento_cliente ON documento_cliente (cliente_id);
