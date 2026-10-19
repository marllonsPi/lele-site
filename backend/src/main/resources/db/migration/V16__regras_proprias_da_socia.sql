-- A primeira socia criada por BootstrapSocia nascia sem regras de visibilidade (o evento UsuarioCriado nao era
-- publicado) e nao enxergava nem os proprios processos. Idempotente: so toca usuarios ativos SOCIA/ADVOGADO que
-- ainda nao tem NENHUMA regra como observador (nao refaz regras que a matriz tenha editado).
INSERT INTO regra_visibilidade (observador_id, titular_id, escopo)
SELECT u.id, u.id, e.escopo
FROM usuario u
CROSS JOIN (VALUES ('PUBLICACAO'), ('AGENDAMENTO'), ('PROCESSO')) AS e (escopo)
WHERE u.ativo
  AND u.perfil IN ('SOCIA', 'ADVOGADO')
  AND NOT EXISTS (SELECT 1 FROM regra_visibilidade r WHERE r.observador_id = u.id)
ON CONFLICT DO NOTHING;
