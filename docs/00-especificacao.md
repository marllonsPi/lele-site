# Especificacao Funcional (fonte da verdade)

Texto original definido pela usuaria em 2026-10-19 (versao com ajuste no item 2/8: Luana tambem inclui agendamentos nos processos da Andressa). Qualquer divergencia entre este arquivo e outro documento: este prevalece, ate a usuaria alterar. Mudancas de regra entram aqui primeiro, com data.

Sistema web de gestao para o escritorio Crispim & Campos Advocacia, com login individual, captura de publicacoes, agenda de prazos, controladoria, cadastro de processos e clientes, financeiro e atendimento.

## 1. USUARIOS E PERFIS
Logins iniciais (5):
- Leticia Crispim Mello – advogada, socia – OAB/DF 84.089 (OAB/SP suplementar a incluir)
- Lorraynny Mendonca Olegario Campos – advogada, socia – OAB/DF 74.371
- Andressa Morais Magalhaes – advogada – OAB/DF 83.736
- Wendell Simao da Rocha – advogado – OAB/DF 89.386 (OAB/GO suplementar a incluir)
- Luana – assistente juridica (dados completos a incluir)

Perfis: Socia (acesso total, distribui tarefas, confere relatorios), Advogado, Assistente e Estagiario.

Gestao de usuarios (apenas socias):
- Cadastrar, editar e desativar usuarios a qualquer momento, sem necessidade de alterar o sistema.
- Cada advogado pode ter varias inscricoes na OAB (principal e suplementares). Ao incluir uma nova OAB, a captura do item 3 passa a monitora-la automaticamente.
- Perfil Estagiario, para cadastro futuro: vinculado a um advogado supervisor, ve apenas as tarefas distribuidas a ele, nao acessa o financeiro, e toda tarefa que finalizar passa primeiro pelo supervisor antes de ir para a conferencia das socias.

## 2. VISIBILIDADE DAS PUBLICACOES (por titular da publicacao)
- Em nome da Leticia: vao para a Controladoria, visivel para Leticia, Lorraynny e Luana.
- Em nome da Lorraynny: visiveis para Leticia, Lorraynny e Luana.
- Em nome da Andressa: visiveis para Andressa, para as socias (conferencia) e para Luana.
- Em nome do Wendell: visiveis para Wendell e para as socias (conferencia).

Restricoes por usuario:
- Wendell ve somente as publicacoes e agendamentos em seu proprio nome.
- Andressa ve somente as publicacoes e agendamentos em seu proprio nome.
- Luana ve as publicacoes da Leticia, da Lorraynny e da Andressa; nao ve as do Wendell.
- Estagiarios veem apenas o que lhes for distribuido.
- A visibilidade deve ser configuravel pelas socias por usuario, para ajustes futuros.

## 3. CAPTURA DIARIA DE PUBLICACOES E PRAZOS
Todos os dias, o sistema busca publicacoes e intimacoes em tres fontes:
a) DJEN (Diario de Justica Eletronico Nacional), por numero de OAB
b) Domicilio Judicial Eletronico
c) E-mails cadastrados de cada advogado (avisos push dos tribunais)

Regras:
- Identificar o advogado por todas as OABs cadastradas no item 1 e distribuir conforme o item 2.
- Filtro da Leticia: ignorar todo push de e-mail vindo de TRT, exceto TRT-10.
- Cruzar as tres fontes pelo numero do processo, para nao duplicar a mesma publicacao e para apontar quando algo aparece em uma fonte e nao em outra.
- Cada item capturado entra na fila "A classificar", onde uma socia define tipo, responsavel e prazos.
- Nada e descartado sem registro: manter log dos itens ignorados pelo filtro.
- Se o processo ja estiver cadastrado, vincular automaticamente; se nao, sugerir o cadastro.

## 4. CADASTRO DE PROCESSOS E CLIENTES
- Cliente: nome, CPF/CNPJ, contatos, endereco, observacoes.
- Processo: numero, tribunal, vara/comarca, classe, partes (cliente e parte contraria), advogado responsavel, situacao e valor da causa.
- Todo agendamento e ligado a um processo, e todo processo a um cliente.
- Tela do processo com historico completo: publicacoes, agendamentos, pecas, atendimentos e lancamentos financeiros.

## 5. TIPOS DE AGENDAMENTO (sigla + bolinha colorida)
- CTR – Controladoria / controle de prazo – preto
- INI – Peticao inicial – rosa
- CON – Contestacao – laranja
- MAN – Manifestacao simples – cinza
- AUD – Audiencia (presencial ou online) – roxo
- REC – Recurso – marrom
- ATD – Atendimento de cliente – azul

Audiencia: campo modalidade (presencial/online), local ou link, e alerta na agenda com 10 dias de antecedencia.

Atendimento de cliente: data, hora de inicio e duracao obrigatorias, cliente vinculado, modalidade (presencial, online ou telefone), local ou link e advogado que atendera. Pode ser ligado a um processo ou apenas ao cliente (ex.: primeira consulta). Aparece na agenda no horario exato e, ao ser concluido, gera automaticamente o registro de atendimento do item 13.

Conflito de agenda: avisar quando audiencias ou atendimentos se sobrepuserem no mesmo dia e horario para o mesmo advogado, e tambem quando houver sobreposicao de audiencias no escritorio.

## 6. PRAZOS
Cada agendamento de prazo registra tres datas:
- Inicio do prazo (data da intimacao)
- Prazo interno: 3 dias uteis antes do fatal (e a data de trabalho e a que aparece na agenda)
- Prazo fatal

O sistema calcula o prazo interno automaticamente em dias uteis, considerando feriados nacionais, locais e suspensoes dos tribunais (tabela editavel).

Dupla conferencia: quem lanca o prazo nao pode ser quem confirma. O agendamento fica como "Aguardando conferencia" ate outro usuario validar a contagem, com registro de quem lancou e quem conferiu. Estagiarios nao confirmam prazos.

Audiencias (AUD) e atendimentos (ATD) tem data e hora proprias e nao seguem a regra dos 3 dias uteis.

## 7. AGENDA INDIVIDUAL
Cada usuario tem sua agenda com os afazeres do dia, com sinalizacao de status:
- Vermelho: vencidos (ate 30 dias atras)
- Amarelo: do dia
- Verde: do dia seguinte
- Marcador de URGENTE (icone destacado)

As cores de status ficam no fundo/borda do cartao; a bolinha indica o tipo (item 5). Compromissos com hora marcada (AUD e ATD) aparecem em ordem de horario.

## 8. DISTRIBUICAO E CRIACAO DE AGENDAMENTOS
- As socias distribuem as tarefas para Luana, para os advogados e, futuramente, para os estagiarios, definindo responsavel, tipo e prazos.
- Luana (assistente juridica) pode incluir agendamentos dentro de cada processo a que tem acesso (processos da Leticia, da Lorraynny e da Andressa), atribuindo-os a ela mesma ou a estagiarios. Ela nao atribui tarefas a advogados nem as socias.
- Os agendamentos criados pela Luana seguem as mesmas regras dos demais: dupla conferencia de prazo (item 6), observacoes, conclusao e envio as socias para conferencia (item 10), e aparecem no painel das socias (item 11) com a indicacao de quem criou.

## 9. ALERTAS AUTOMATICOS
Enviar por e-mail e WhatsApp ao responsavel (com copia as socias):
- Na vespera do prazo interno
- Na vespera do prazo fatal
- 10 dias antes de cada audiencia, e novamente na vespera
- Na vespera de cada atendimento de cliente
- Diariamente, resumo dos itens vencidos e nao finalizados

## 10. OBSERVACOES, DOCUMENTOS E CONCLUSAO
- Campo de observacao em texto livre em cada agendamento, com historico (quem escreveu e quando).
- Anexar pecas e documentos ao agendamento e ao processo.
- Biblioteca de modelos de pecas, organizada por tipo (INI, CON, MAN, REC) e area do direito.
- Ao finalizar, o agendamento vai para o relatorio do responsavel e e enviado as socias para conferencia. A socia marca "Conferido" ou "Devolvido" com motivo.

## 11. PAINEL DAS SOCIAS
Visao exclusiva das socias com: tarefas por pessoa, atrasos, carga de trabalho, prazos da semana, audiencias e atendimentos proximos, itens aguardando conferencia e fila "A classificar".

## 12. FINANCEIRO
Por cliente e por processo: contrato de honorarios, parcelas (a vencer, pagas, em atraso), custas e despesas, honorarios de exito e sucumbencia. Acesso restrito as socias.

## 13. ATENDIMENTO
Registro de cada contato com o cliente (data, hora, canal, quem atendeu, resumo), alimentado pelos agendamentos ATD concluidos e por lancamentos avulsos (ligacoes, mensagens). Lista de andamentos pendentes de repassar ao cliente, com marcacao de "repassado" (manual ou automatica, quando o relatorio ao cliente do item 14 for enviado).

## 14. RELATORIOS
### 14.1 Relatorios internos
- Agenda do dia (geral e por usuario)
- Relatorio de cada agenda individual, para conferencia das socias
- Finalizados no periodo, pendentes e vencidos
- Atendimentos realizados no periodo
- Financeiro por cliente
Exportacao em PDF e envio por e-mail as socias.

### 14.2 Relatorio resumido ao cliente
Relatorio para ser entregue ao cliente, gerado a partir dos andamentos e movimentacoes registrados no sistema.
- Abrangencia: um processo especifico ou todos os processos do cliente, com periodo selecionavel (ex.: ultimo mes, desde o ultimo relatorio, todo o historico).
- Conteudo por processo: numero, vara/comarca, partes, tipo de acao, situacao atual, resumo das movimentacoes do periodo em ordem cronologica, o que o escritorio fez (pecas protocoladas, audiencias realizadas) e proximos passos (audiencias marcadas e providencias previstas).
- Linguagem simples e acessivel ao cliente, sem jargao desnecessario, com campo para o advogado acrescentar comentarios.
- Nao incluir informacoes internas: observacoes dos agendamentos, prazos internos, conferencias, nomes de quem executou cada tarefa e dados de outros clientes.
- Opcao de incluir ou nao um resumo financeiro (parcelas pagas e a vencer).
- Fluxo: o sistema gera um rascunho, o advogado responsavel ou uma socia revisa, edita e aprova, e so entao o relatorio e liberado. Nenhum relatorio vai ao cliente sem aprovacao.
- Saida: PDF com timbre e dados do escritorio, com envio por e-mail ou WhatsApp, ou impressao.
- Registro: guardar no historico do cliente a data de envio, quem aprovou e uma copia do relatorio, e marcar como "repassados" os andamentos incluidos.
- Permissao: socias e advogado responsavel pelo processo. Wendell e Andressa geram apenas dos proprios processos.

## 15. REQUISITOS GERAIS
Interface em portugues, responsiva (celular e computador), registro de todas as acoes (quem fez, o que e quando), backup diario e controle de acesso conforme a LGPD.

## 16. EVOLUCOES DE 2026-10-21 (pedido das socias)
- Cadastros: area de atuacao (Trabalhista, Previdenciario, Familia, Sucessao, Imobiliario, Condominial, Militar, Criminal, Administrativo, Civel). Cliente: uma ou mais, obrigatoria ao salvar; processo e contrato: uma. Filtro por area em clientes, processos e painel das socias. Cliente sem area mostra "cadastro incompleto" ate preencher. Detalhe: `docs/14-importacao.md`.
- Financeiro: importacao da planilha financeira e das pastas de clientes do Google Drive com previa, confirmacao de socia, relatorio e sincronizacao diaria; painel financeiro (recebido/em aberto por mes, vencidas, a vencer 7/30 dias, receita por area) e alerta de parcela vencida; tudo restrito as socias. Detalhe: `docs/14-importacao.md`.
- Honorarios: tabelas da OAB (DF/SP) e do escritorio, sugestao no atendimento, proposta com valor sugerido x fechado, alerta e justificativa abaixo do minimo da OAB, minuta do contrato, lancamento das parcelas na assinatura e relatorios. Detalhe: `docs/15-honorarios.md`.
