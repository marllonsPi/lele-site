-- Drive do escritorio como armazenamento do ecossistema: conta Google (conectada com acesso ao Drive) e pasta raiz dos clientes,
-- mais o cache das pastas ja criadas (cliente/processo) para nao procurar a cada anexo. Anexos com caminho 'drive:<id>' vivem no Drive.
CREATE TABLE drive_config (
    id               SMALLINT PRIMARY KEY CHECK (id = 1),
    conta_usuario_id BIGINT REFERENCES usuario (id),
    pasta_raiz_id    VARCHAR(120),
    atualizado_em    TIMESTAMPTZ NOT NULL DEFAULT now()
);
INSERT INTO drive_config (id) VALUES (1);

CREATE TABLE drive_pasta (
    chave     VARCHAR(400) PRIMARY KEY,
    drive_id  VARCHAR(120) NOT NULL,
    criada_em TIMESTAMPTZ  NOT NULL DEFAULT now()
);
