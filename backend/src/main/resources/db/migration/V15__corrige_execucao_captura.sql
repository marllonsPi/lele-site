-- 'EM_ANDAMENTO' (12 caracteres) nao cabia em VARCHAR(10): toda execucao da captura falhava ao registrar o inicio.
ALTER TABLE execucao_captura ALTER COLUMN resultado TYPE VARCHAR(15);
