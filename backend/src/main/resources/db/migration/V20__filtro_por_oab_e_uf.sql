-- Filtro por OAB e modo "so trazer estes estados" (ex.: na OAB/DF, so processos de GO).
ALTER TABLE filtro_captura ALTER COLUMN tribunal_prefixo DROP NOT NULL;
ALTER TABLE filtro_captura ADD COLUMN modo VARCHAR(10) NOT NULL DEFAULT 'IGNORAR';
ALTER TABLE filtro_captura ADD COLUMN ufs VARCHAR(100);
ALTER TABLE filtro_captura ADD COLUMN oab_numero VARCHAR(20);
ALTER TABLE filtro_captura ADD COLUMN oab_uf CHAR(2);
ALTER TABLE filtro_captura ADD CONSTRAINT ck_filtro_modo CHECK (modo IN ('IGNORAR', 'SO_UF'));
ALTER TABLE filtro_captura ADD CONSTRAINT ck_filtro_modo_dados CHECK (
    (modo = 'IGNORAR' AND tribunal_prefixo IS NOT NULL) OR (modo = 'SO_UF' AND ufs IS NOT NULL));
