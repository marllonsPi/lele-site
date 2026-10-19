# Invariantes

Quebrar qualquer uma exige pedido explicito da usuaria registrado em `decisions.md`.

1. **Visibilidade centralizada**: publicacoes, agendamentos, processos e relatorios so sao lidos por `Visibilidade.visiveisPara(usuario)`. Wendell e Andressa veem so o proprio; Luana nao ve Wendell; financeiro so socias. Toda mudanca nisso tem teste na matriz de `docs/03`.
2. **Dupla conferencia de prazo**: quem lancou nao confirma (regra no dominio e no banco). Estagiario nunca confirma.
3. **Nada some**: publicacao ignorada por filtro vai para `ItemIgnorado`; exclusao logica em dados juridicos; auditoria so de insercao.
4. **Captura idempotente**: nunca duplicar a mesma publicacao; sinalizar quando aparece so em uma fonte.
5. **Prazo interno = fatal menos 3 dias uteis** (calendario editavel); AUD e ATD nao seguem essa regra.
6. **Relatorio ao cliente**: nada sai sem aprovacao; nunca inclui observacoes internas, prazos internos, conferencias, executores nem dados de outros clientes (lista branca de campos).
7. **Luana nao atribui tarefa a advogado nem a socia.**
8. **Todo agendamento tem processo; todo processo tem cliente** (excecao: ATD de primeira consulta so com cliente).
9. **LGPD e sigilo**: dados reais nunca em testes, fixtures, logs, RAG ou chat; segredos fora do codigo; backup diario criptografado.
10. **Configuravel por dados**: usuarios, OABs, visibilidade, feriados e filtros mudam sem alterar o sistema.
11. **Interface em portugues e responsiva.**
