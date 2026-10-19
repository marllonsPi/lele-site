-- Fase 11: segundo fator (TOTP) opcional por usuario.
ALTER TABLE usuario
    ADD COLUMN totp_secret VARCHAR(64),
    ADD COLUMN totp_ativo  BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE usuario ADD CONSTRAINT ck_usuario_totp CHECK (NOT totp_ativo OR totp_secret IS NOT NULL);
