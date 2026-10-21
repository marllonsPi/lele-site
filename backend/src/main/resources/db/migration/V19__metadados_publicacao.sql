-- Metadados que o DJEN ja entrega (classe, orgao, partes, link, tipo): guardados em JSON no bruto, para cadastro automatico.
ALTER TABLE publicacao_bruta ADD COLUMN metadados TEXT;
