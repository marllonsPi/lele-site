-- Comentarios enviados pelo site institucional: moderacao pelas socias (por link no e-mail ou na fila do sistema).
-- So os APROVADOS aparecem no site. O token do e-mail e guardado apenas como SHA-256.
CREATE TABLE comentario_site (
    id               BIGSERIAL PRIMARY KEY,
    nome             VARCHAR(80)   NOT NULL,
    email            VARCHAR(254)  NOT NULL,
    mensagem         VARCHAR(700)  NOT NULL,
    estado           VARCHAR(12)   NOT NULL DEFAULT 'PENDENTE',
    token_hash       CHAR(64)      NOT NULL UNIQUE,
    criado_em        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    aviso_enviado_em TIMESTAMPTZ,
    decidido_em      TIMESTAMPTZ,
    decidido_por     BIGINT REFERENCES usuario (id),
    decisao_via      VARCHAR(10),
    CONSTRAINT ck_comentario_estado CHECK (estado IN ('PENDENTE', 'APROVADO', 'REJEITADO')),
    CONSTRAINT ck_comentario_via CHECK (decisao_via IS NULL OR decisao_via IN ('EMAIL', 'SISTEMA'))
);
CREATE INDEX ix_comentario_estado ON comentario_site (estado, criado_em DESC);

-- Depoimentos que ja estavam publicados no index.html (aprovados antes desta rotina).
INSERT INTO comentario_site (nome, email, mensagem, estado, token_hash, criado_em, decidido_em, decisao_via)
VALUES ('Raul', 'importado@site.invalid', 'Excelente advogadas! Muito atenciosas, transparente e educadas', 'APROVADO',
        md5('seed-raul') || md5('seed-raul-2'), '2026-09-02 12:00:00-03', '2026-09-02 12:00:00-03', 'SISTEMA'),
       ('Pai de cliente', 'importado@site.invalid',
        'Graças a Deus deu tudo certo, sou muito grato a vocês que me ajudaram e ajudaram a [nome da menor ocultado], muito obrigado de verdade. Agora tenho a certeza de que ela vai ficar bem e segura. Vocês são demais. ❤️❤️❤️❤️❤️',
        'APROVADO', md5('seed-pai') || md5('seed-pai-2'), '2026-02-21 12:00:00-03', '2026-02-21 12:00:00-03', 'SISTEMA');
