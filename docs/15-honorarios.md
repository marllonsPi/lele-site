# Honorarios: tabelas, sugestao, proposta, minuta e relatorios

Pedido das socias (2026-10-21), a partir da planilha de honorarios enviada (aba "Honorarios por Area" + "Parametros").

## Tabelas (so socias)
- `tabela_honorarios_versao` separa OAB (por seccional DF/SP) e ESCRITORIO; cada importacao cria versao nova e a anterior vira historico (uma vigente por tabela). `tabela_honorarios_item`: area, tipo de servico, fase (judicial, extrajudicial, conhecimento, recurso, cumprimento), item da OAB, valor em URH, minimo da OAB ou valor de tabela do escritorio, desconto, valor sugerido, exito (texto e min/max %), parcelamento, observacoes. OAB e escritorio sao pareados por `chave_servico` (area+tipo+fase).
- Tela `/honorarios`: importar (seccional; substituir tambem a do escritorio), editar/incluir/excluir (logico)/restaurar itens da versao vigente, consultar versoes antigas, atualizar a URH (recalcula minimos e valores derivados = minimo x fator, com desconto, nunca abaixo do minimo) e a politica (fator, descontos, desconto por pacote), modelo da minuta.
- Mapeamento de area da planilha: "Familia e Sucessoes" -> Familia ou Sucessao (inventario, testamento...), "Direito Imobiliario", "Civel e Contratos", "Direito Penal" -> Criminal, "Militar e Administrativo" -> Militar ou Administrativo, "Trabalhista"; "Consultoria Preventiva" fica sem area.

## Sugestao no atendimento
- No atendimento (ATD) o advogado que o conduz (e as socias) escolhe seccional do local do servico, area e tipo(s) de servico e ve: minimo da OAB, valor da tabela do escritorio, valor sugerido (nunca abaixo do minimo), exito sugerido e opcoes de parcelamento (a vista; entrada de 30% + 3/6/10/12 parcelas; tres etapas do art. 6º). Varios servicos: soma e desconto de pacote definido pelas socias (so com 2+ servicos, nunca abaixo da soma dos minimos). Socias tambem criam proposta para cliente fora de atendimento.
- Advogado ve o catalogo de servicos sem valores; assistente e estagiario nao tem acesso; outro advogado recebe "nao encontrado".

## Fechamento e alerta
- A proposta guarda o valor sugerido (e o minimo da OAB) e o valor efetivamente fechado. Abaixo do minimo: alerta com a diferenca e exigencia de motivo (permuta, hipossuficiencia, pro bono, pacote, outro) e justificativa (servico e CHECK no banco). Esses casos vao ao relatorio das socias e a auditoria (`PROPOSTA_FECHADA_ABAIXO_OAB`).
- Minuta (`POST /api/propostas/{id}/minuta`): .docx a partir do modelo do escritorio (campos `{{cliente_nome}}`, `{{valor_total}}`, `{{valor_extenso}}`, `{{parcelas}}`...), guardado restrito na pasta do cliente.
- Assinatura (so socias): lanca um contrato por area (valor repartido pelo valor sugerido de cada servico), com entrada e parcelas, vinculados ao cliente e a area; soma as areas ao cliente.

## Relatorios (so socias)
`GET /api/honorarios/relatorios?de=&ate=`: valor sugerido x fechado por advogado, area e mes; conversao (reunioes realizadas x contratos fechados) por advogado e geral; ticket medio por area; fechados abaixo da tabela da OAB com justificativa.

## Como testar
`./mvnw -o test -Dtest='HonorariosPostgresTest,SugestaoHonorariosTest,ValorPorExtensoTest'` e `npx vitest run src/__tests__/honorarios.test.ts`.
