# Importacao da planilha financeira e do Google Drive

Pedido das socias (2026-10-21). Tudo restrito as socias; advogados e assistente nao veem valores (invariante 1).

## Fluxo
1. `/importacao` (so socias): enviar a planilha `.xlsx`, ou ler a planilha e as pastas de clientes do Drive. Gera uma PREVIA (nada e gravado).
2. A previa lista cada registro como Novo, Conflito, Sem mudanca ou Ignorado, com divergencias e, nos conflitos, as duas versoes lado a lado. A socia corrige area, vencimento, valor, decide aplicar/manter por item (ou em lote) e so entao confirma.
3. Confirmar recusa se houver pendencia (area faltando, vencimento invalido, parcela dependente de contrato nao importado). Cada item e gravado em transacao propria; erro vira "ERRO" no relatorio e nao derruba o resto.
4. Relatorio: totais por tipo (criados, atualizados, mantidos, ignorados, erros), com os ignorados e o motivo. Fica no historico (`importacao`, `importacao_item`, auditoria so de insercao).

## Regras
- Identidade do cliente: CPF/CNPJ (preferencial); sem ele, nome normalizado (sem acento/caixa) e marcacao `a_conferir`. A planilha real nao traz CPF/CNPJ.
- Idempotencia: cada registro tem chave de origem (`PLANILHA|cliente|rotulo|n`, `DRIVE|id`, `CNJ|numero`). Reimportar nao duplica. Compara-se so o que a fonte informa (campo vazio na fonte nao conflita).
- Nao sobrescreve: CONFLITO mantem o existente por padrao. Do Drive, "aplicar" so preenche campos vazios; trocar valores existentes exige marcar "sobrescrever".
- Area: cliente/contrato/processo importados exigem area (sugerida por `AreaPorTexto`; o que nao casa pede escolha). Processo novo usa o responsavel padrao (quem gerou a previa, ou o escolhido).
- Parcelas: situacao PAGO/EM ABERTO/PAGO METADE/CANCELAMENTO/vazio; numero por rotulo (ENTRADA = 0, PARCELA n/N, VALOR TOTAL = 1; repetido e renumerado com aviso). Parcela sem contrato correspondente ou ambigua vai para um contrato avulso `a_conferir`. Sem data de pagamento na planilha: `pago_em` fica nulo.
- Ignorados com motivo: totais mensais, linhas "EXTRA - ..." sem cliente, despesa incompleta, arquivo solto na raiz do Drive ("processo sem cliente vinculado"), arquivo maior que 20 MB.
- Despesas da planilha ("Despesas Mensais") sao do escritorio (`despesa_escritorio`), sem cliente.
- Exito: percentual vai para o contrato; se ha valor da causa numerico, nasce `honorario_variavel` EXITO previsto.

## Drive
- Conexao: a socia conecta "Google com Drive" (escopo `drive.readonly`, opt-in, na mesma conexao do Gmail; docs/13). Servidor: `IMPORTACAO_PASTA_CLIENTES_ID` (pasta raiz com uma subpasta por cliente) e `IMPORTACAO_PLANILHA_ID`.
- Por cliente: dados cadastrais (CPF/CNPJ, RG, estado civil, endereco, telefone, e-mail) extraidos de contrato/procuracao (PDF com texto, DOCX, TXT) na secao CONTRATANTE/OUTORGANTE; contrato de honorarios (valor, forma, parcelas, assinatura, processo) que complementa o contrato da planilha e sinaliza valor diferente; numeros CNJ validos (digito verificador) viram processos; todos os arquivos viram documentos na pasta do cliente (`documento_cliente`; contratos restritos as socias; baixados so na confirmacao).
- Ordem recomendada: planilha, depois Drive.

## Sincronizacao diaria
Desligada por padrao (`IMPORTACAO_SINCRONIZACAO_DIARIA=true`, `IMPORTACAO_CONTA_USUARIO_ID`, cron `app.importacao.cron`, 06:30). Gera a previa de cada fonte; sem mudanca so audita; se so ha parcelas, despesas e documentos novos sem divergencia, conclui sozinha (modo DIARIA no historico); senao deixa PREVIA para confirmacao. Conflitos ja mantidos com os mesmos valores nao voltam.

## Painel financeiro (socias)
`GET /api/financeiro/painel?area=`: recebido e em aberto por mes, parcelas vencidas (cliente, contrato, valor em aberto, dias de atraso), a vencer em 7 e 30 dias, receita por area. Alerta diario `PARCELA_VENCIDA` as socias (so contagem e total, sem nomes).

## Como testar
`./mvnw -o test -Dtest='ImportacaoPostgresTest,ImportacaoDrivePostgresTest,PlanilhaFinanceiraTest,ExtratorDocumentoTest'` (planilhas e arquivos ficticios gerados em `XlsxFicticio`; Drive simulado por `DriveFonte`). Dado real nunca entra em teste, fixture, log ou RAG.
