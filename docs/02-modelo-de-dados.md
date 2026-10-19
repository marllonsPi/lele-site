# Modelo De Dados (rascunho conceitual)

Status: proposta, 2026-10-19. Nomes de tabela/campo definitivos nascem na primeira migracao; depois dela este arquivo passa a refletir o codigo (codigo prevalece).

## Identidade e acesso
- `Usuario`: nome, e-mail, perfil (SOCIA | ADVOGADO | ASSISTENTE | ESTAGIARIO), ativo, supervisor (so estagiario -> advogado), 2FA.
- `OAB`: usuario, numero, UF, principal (bool), ativa. Varias por usuario. Criar/ativar dispara inclusao automatica na captura.
- `ContaEmailCaptura`: usuario, endereco, tipo de acesso, ativo (credenciais fora do banco em texto claro).
- `RegraVisibilidade` (migracao V2, existe): observador, titular, escopo (PUBLICACAO | AGENDAMENTO | PROCESSO); presenca da linha = regra ativa, unica por (observador, titular, escopo).
- `LogAuditoria`: usuario, acao, entidade, id, antes, depois, momento, ip. Somente insercao.

## Cadastros
- `Cliente` (V3, existe): nome, documento (so digitos, CPF/CNPJ validado, unico), e-mail, telefone, endereco, observacoes, ativo.
- `Processo` (V3, existe): numero CNJ (20 digitos, DV validado, unico), tribunal, vara/comarca, classe, situacao, valor da causa, cliente, advogado responsavel (`advogado_id` = titular para a visibilidade), ativo.
- `ParteProcesso` (V3, existe): processo, nome, papel (CLIENTE | CONTRARIA | OUTRO); a parte CLIENTE e criada junto com o processo.

## Agenda
- `Agendamento` (V5, parcial: so prazos; `titular_id` = advogado do processo no lancamento, base da visibilidade; CHECK `conferido_por <> lancado_por`): processo (obrigatorio, exceto ATD so com cliente), tipo (CTR|INI|CON|MAN|AUD|REC|ATD), responsavel, criado_por, urgente, status (PENDENTE | AGUARDANDO_CONFERENCIA_PRAZO | FINALIZADO | EM_CONFERENCIA_SOCIA | CONFERIDO | DEVOLVIDO | EM_CONFERENCIA_SUPERVISOR), origem (publicacao opcional).
- Campos de prazo (CTR/INI/CON/MAN/REC): `inicio_prazo`, `prazo_interno`, `prazo_fatal`, `lancado_por`, `conferido_por`, `conferido_em`.
- Campos de AUD/ATD: `inicio_em`, `duracao_min`, `modalidade`, `local_ou_link`; ATD: `cliente`, `advogado_atendente`.
- `ObservacaoAgendamento`: agendamento, autor, texto, momento (historico imutavel).
- `Anexo`: agendamento ou processo, arquivo, autor, momento.
- `ConferenciaSocia`: agendamento, socia, resultado (CONFERIDO | DEVOLVIDO), motivo, momento.
- `ModeloPeca`: tipo (INI|CON|MAN|REC), area do direito, titulo, arquivo.

## Calendario
- `Feriado` (V4, existe; um registro serve a feriado e suspensao, campo `tipo`): escopo (NACIONAL | ESTADO | MUNICIPIO | TRIBUNAL), referencia (UF, cidade ou tribunal), data inicial, data final, descricao, editavel.

## Captura
- `PublicacaoBruta`: fonte (DJEN | DOMICILIO | EMAIL), id_externo, numero_processo, conteudo, hash, recebido_em, oab/usuario.
- `Publicacao`: numero_processo, titular, data, resumo, fontes_presentes, processo (nulo ate vincular), estado (A_CLASSIFICAR | CLASSIFICADA | DESCARTADA_COM_REGISTRO).
- `ItemIgnorado`: bruta, regra aplicada, motivo, momento.
- `ExecucaoCaptura`: inicio, fim, fonte, resultado, erros.

## Financeiro (somente Socia)
- `ContratoHonorarios`, `Parcela` (a vencer | paga | em atraso), `Despesa` (custas), `HonorarioExito`, `Sucumbencia`; sempre ligados a cliente e, quando couber, processo.

## Atendimento e relatorios
- `Atendimento`: cliente, processo opcional, data/hora, canal, atendente, resumo, origem (agendamento ATD concluido | avulso).
- `AndamentoPendenteRepasse`: andamento, cliente, repassado (bool), repassado_em, via.
- `RelatorioCliente`: cliente, processos, periodo, estado (RASCUNHO | EM_REVISAO | APROVADO | ENVIADO), comentarios do advogado, incluir_financeiro, aprovado_por, enviado_em, canal, arquivo PDF.

## Restricoes essenciais (no banco, nao so na tela)
- `conferido_por <> lancado_por` em prazos.
- Impedir sobreposicao de AUD/ATD do mesmo advogado (aviso) e de AUD no escritorio (aviso): conflito avisa, nao bloqueia, salvo decisao contraria.
- Unicidade de `Publicacao` por (numero_processo normalizado, data, hash) para idempotencia.
- OAB unica por (numero, UF).
