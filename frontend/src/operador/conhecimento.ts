export interface Verbete {
  id: string
  titulo: string
  /** Perguntas e palavras com que a pessoa costuma chegar aqui. */
  gatilhos: string[]
  resposta: string
  passos?: string[]
  /** Comando do assistente que faz (ou abre) o que o verbete explica. */
  comando?: string
}

/** Tudo o que o assistente sabe sobre o sistema (fonte: docs/00-especificacao.md e as telas). */
export const VERBETES: Verbete[] = [
  {
    id: 'prazo-lancar',
    titulo: 'Como lançar um prazo',
    gatilhos: ['lancar prazo', 'novo prazo', 'criar prazo', 'cadastrar prazo', 'prazo fatal', 'prazo interno', 'intimacao'],
    resposta: 'Todo prazo é ligado a um processo e tem três datas: início (data da intimação), prazo interno (3 dias úteis antes do fatal; é a data de trabalho que aparece na agenda) e prazo fatal.',
    passos: ['Abra Prazos.', 'Escolha o processo, o tipo (CTR, INI, CON, MAN, REC) e o responsável.', 'Informe início e prazo fatal; o interno é calculado em dias úteis.', 'Salve: o prazo fica "Aguardando conferência" até outra pessoa validar.'],
    comando: 'ir:/prazos',
  },
  {
    id: 'prazo-conferencia',
    titulo: 'Dupla conferência de prazos',
    gatilhos: ['conferir prazo', 'conferencia', 'quem confere', 'aguardando conferencia', 'validar prazo', 'estagiario confere'],
    resposta: 'Quem lança o prazo não pode ser quem confirma: outra pessoa valida a contagem, e o sistema registra quem lançou e quem conferiu. Estagiários não conferem prazos.',
    passos: ['Abra Conferência.', 'Confira a contagem de cada prazo lançado por outra pessoa.', 'Confirme ou devolva.'],
    comando: 'ir:/conferencia',
  },
  {
    id: 'tipos',
    titulo: 'Tipos de agendamento e cores',
    gatilhos: ['tipos', 'siglas', 'ctr', 'ini', 'con', 'man', 'aud', 'rec', 'atd', 'bolinha', 'cores'],
    resposta: 'CTR controladoria (preto), INI petição inicial (rosa), CON contestação (laranja), MAN manifestação (cinza), AUD audiência (roxo), REC recurso (marrom), ATD atendimento (azul). Na agenda: vermelho = vencido (até 30 dias), amarelo = do dia, verde = dia seguinte; o ícone marca URGENTE.',
    comando: 'ir:/agenda',
  },
  {
    id: 'audiencia',
    titulo: 'Audiências e atendimentos',
    gatilhos: ['audiencia', 'atendimento', 'marcar', 'compromisso', 'conflito de agenda', 'sobreposicao', 'horario'],
    resposta: 'Audiências (AUD) e atendimentos (ATD) têm data e hora próprias e não seguem a regra dos 3 dias úteis. Audiência pede modalidade (presencial/online) e local ou link, e avisa 10 dias antes e na véspera. O sistema alerta sobreposição para o mesmo advogado e entre audiências do escritório.',
    passos: ['Abra Agenda ou Atendimento.', 'Informe data, hora, duração, modalidade e local/link.', 'Atendimento concluído gera o registro em Atendimento.'],
    comando: 'ir:/agenda',
  },
  {
    id: 'publicacoes-fila',
    titulo: 'Fila "A classificar"',
    gatilhos: ['publicacoes', 'a classificar', 'classificar', 'fila', 'intimacoes', 'descartar publicacao', 'djen'],
    resposta: 'Toda publicação capturada entra na fila "A classificar". Uma sócia a classifica (define tipo, responsável e datas, e o prazo nasce aguardando conferência) ou a descarta com motivo obrigatório. Nada some sem registro. Ao classificar, o sistema já sugere tipo, prazo e datas lendo o texto; confira antes de salvar.',
    passos: ['Abra Publicações.', 'Se o processo não estiver cadastrado, use "Cadastrar processo automaticamente".', 'Clique em Classificar, confira o que foi preenchido e salve.'],
    comando: 'ir:/publicacoes',
  },
  {
    id: 'cadastro-auto',
    titulo: 'Cadastro automático de processo e cliente',
    gatilhos: ['cadastrar processo automaticamente', 'cadastro automatico', 'processo nao cadastrado', 'cliente automatico', 'partes'],
    resposta: 'Para publicações do DJEN o sistema guarda classe, órgão, tipo de comunicação, link e partes. "Cadastrar processo automaticamente" cria o processo com esses dados, reaproveita ou cria o cliente que você escolher e registra as demais partes como parte contrária.',
    comando: 'ir:/publicacoes',
  },
  {
    id: 'captura',
    titulo: 'Como funciona a captura',
    gatilhos: ['captura', 'capturar', 'buscar publicacoes', 'fontes', 'domicilio judicial', 'gmail', 'email push', 'executar captura'],
    resposta: 'As fontes são: DJEN (busca por número de OAB, automática), e-mail via Gmail (de quem conectou a conta Google; padrão "from:jus.br") e Domicílio Judicial (sem conector: só entra por importação manual). Os itens passam pelos filtros, são cruzados pelo número do processo (sem duplicar) e entram na fila.',
    passos: ['Em Captura, use "Executar captura agora" para buscar já.', 'Ou ligue a captura diária (6h, últimos 3 dias; vem desligada).'],
    comando: 'ir:/captura',
  },
  {
    id: 'captura-diaria',
    titulo: 'Captura diária automática',
    gatilhos: ['captura diaria', 'ligar captura', 'desligar captura', 'automatica', 'agendar', '6h'],
    resposta: 'Fica desligada por padrão. Quando ligada, roda todo dia às 6h (horário de Brasília) olhando os últimos 3 dias; repetir é seguro porque cada passada é idempotente. Liga e desliga na tela Captura, sem reiniciar nada.',
    comando: 'captura-ligar',
  },
  {
    id: 'filtros',
    titulo: 'Filtros de captura',
    gatilhos: ['filtro', 'filtros', 'ignorar', 'ignorados', 'trt', 'excecao', 'excecoes', 'prefixo'],
    resposta: 'Um filtro tem fonte, prefixo de tribunal e exceções. Ex.: prefixo "TRT" com exceção "TRT-10" ignora todos os TRT, menos o TRT-10. Dá para editar, desativar/reativar e excluir. O que o filtro ignora fica no registro de ignorados; excluir o filtro não apaga esse registro.',
    passos: ['Contas e OABs → escolha a pessoa → aba Captura, ou a tela Captura.', 'Use editar, desativar ou excluir na linha do filtro.'],
    comando: 'ir:/captura',
  },
  {
    id: 'oab',
    titulo: 'OABs e contas',
    gatilhos: ['oab', 'nova oab', 'suplementar', 'criar usuario', 'nova conta', 'desativar usuario', 'perfil', 'senha', 'redefinir senha'],
    resposta: 'Só sócias gerenciam contas. Cada advogado pode ter várias OABs (principal e suplementares); ao incluir uma, a captura passa a monitorá-la sozinha. Contas desativadas não entram, mas o histórico é preservado. Perfis: Sócia, Advogado, Assistente e Estagiário (este com supervisor).',
    passos: ['Abra Contas e OABs.', 'Escolha a pessoa e use as abas: Dados e perfil, Contato e alertas, OABs, Captura, Acesso.'],
    comando: 'ir:/usuarios',
  },
  {
    id: 'visibilidade',
    titulo: 'Quem vê o quê',
    gatilhos: ['visibilidade', 'quem ve', 'permissao', 'permissoes', 'acesso', 'wendell', 'andressa', 'luana', 'titular'],
    resposta: 'A visibilidade é por titular da publicação e é configurável pelas sócias. Em regra: sócias veem o das sócias; Andressa e Wendell veem só o próprio (as sócias veem para conferência); Luana vê Leticia, Lorraynny e Andressa; estagiário vê só o que lhe for distribuído. O servidor é quem decide: esconder um menu não dá acesso a ninguém.',
    comando: 'ir:/visibilidade',
  },
  {
    id: 'processo',
    titulo: 'Processos e clientes',
    gatilhos: ['processo', 'cliente', 'cadastrar processo', 'novo cliente', 'cpf', 'cnpj', 'partes', 'vara', 'classe'],
    resposta: 'Cliente tem nome, CPF/CNPJ, contatos, endereço e observações. Processo tem número CNJ (validado), tribunal, vara/comarca, classe, partes, advogado responsável, situação e valor da causa. Todo agendamento é ligado a um processo, e todo processo a um cliente.',
    passos: ['Cadastre o cliente em Clientes.', 'Cadastre o processo em Processos, escolhendo o cliente e o advogado.'],
    comando: 'ir:/processos',
  },
  {
    id: 'alertas',
    titulo: 'Alertas automáticos',
    gatilhos: ['alerta', 'alertas', 'email', 'whatsapp', 'aviso', 'vespera', 'notificacao'],
    resposta: 'Os alertas por e-mail avisam na véspera do prazo interno e do fatal, 10 dias antes e na véspera das audiências, na véspera de atendimentos e trazem um resumo diário de vencidos, com cópia às sócias. WhatsApp depende de provedor ainda não ligado. Cada pessoa liga/desliga o e-mail e escolhe o endereço em Contato e alertas.',
    comando: 'ir:/seguranca',
  },
  {
    id: 'relatorios',
    titulo: 'Relatórios',
    gatilhos: ['relatorio', 'relatorios', 'pdf', 'relatorio ao cliente', 'aprovar relatorio', 'timbre'],
    resposta: 'Internos: agenda do dia, por pessoa, finalizados/pendentes/vencidos, atendimentos e financeiro por cliente. Ao cliente: o sistema gera um rascunho em linguagem simples, um advogado responsável ou sócia revisa e aprova, e só então sai em PDF com timbre. Nada informal vai ao cliente: observações, prazos internos, conferências e nomes de quem executou ficam de fora.',
    comando: 'ir:/relatorios',
  },
  {
    id: 'financeiro',
    titulo: 'Financeiro',
    gatilhos: ['financeiro', 'honorarios', 'parcela', 'custas', 'exito', 'sucumbencia', 'contrato'],
    resposta: 'Por cliente e processo: contrato de honorários, parcelas (a vencer, pagas, em atraso), custas e despesas, honorários de êxito e sucumbência. Acesso restrito às sócias.',
    comando: 'ir:/financeiro',
  },
  {
    id: 'modelos',
    titulo: 'Modelos de peças',
    gatilhos: ['modelo', 'modelos', 'peca', 'pecas', 'minuta', 'biblioteca'],
    resposta: 'A biblioteca de modelos é organizada por tipo (INI, CON, MAN, REC) e área do direito. Anexos de peças e documentos ficam no agendamento e no processo.',
    comando: 'ir:/modelos',
  },
  {
    id: 'conclusao',
    titulo: 'Concluir tarefa e conferência das sócias',
    gatilhos: ['concluir', 'finalizar', 'finalizado', 'devolver', 'conferido', 'devolvido', 'observacao'],
    resposta: 'Ao finalizar, o agendamento vai ao relatório do responsável e às sócias para conferência; a sócia marca "Conferido" ou "Devolvido" com motivo. Estagiário passa antes pelo supervisor. Cada agendamento tem observações em texto livre com histórico (quem escreveu e quando).',
    comando: 'ir:/agenda',
  },
  {
    id: 'painel',
    titulo: 'Painel das sócias',
    gatilhos: ['painel', 'carga de trabalho', 'atrasos', 'semana', 'visao geral'],
    resposta: 'Visão exclusiva das sócias: tarefas por pessoa, atrasos, carga de trabalho, prazos da semana, audiências e atendimentos próximos, itens aguardando conferência e a fila "A classificar".',
    comando: 'ir:/painel',
  },
  {
    id: 'google',
    titulo: 'Conectar o Gmail (Google)',
    gatilhos: ['google', 'conectar gmail', 'conexao google', 'oauth', 'email nao captura'],
    resposta: 'A captura por e-mail só funciona para quem conectou a própria conta Google. Sem conexão, o Gmail dessa pessoa não é lido. Veja a conexão na sua conta.',
    comando: 'ir:/seguranca',
  },
  {
    id: 'seguranca',
    titulo: 'Minha conta e segurança',
    gatilhos: ['minha conta', 'dois fatores', '2fa', 'trocar senha', 'seguranca', 'login', 'sair'],
    resposta: 'Em Minha conta você altera contato, e-mail de alertas, assinatura e segurança (senha e verificação em duas etapas). Acesso mestre é para quem administra o sistema.',
    comando: 'ir:/seguranca',
  },
  {
    id: 'atalhos',
    titulo: 'Atalhos do assistente',
    gatilhos: ['ajuda', 'o que voce faz', 'atalho', 'ctrl k', 'comandos', 'como usar'],
    resposta: 'Abra o assistente com Ctrl+K ou clicando no ✦. Digite o que quer: ele responde dúvidas sobre o sistema, abre telas e executa ações (com confirmação). Ele também avisa o que está pendente, como publicações a classificar e prazos para conferir.',
  },
]

/** Dicas por tela: o que da para fazer onde a pessoa esta. */
export const DICAS_DA_TELA: Record<string, string[]> = {
  '/': ['Veja a agenda do dia e o que está vencido.'],
  '/prazos': ['Lance prazos ligados a um processo; outra pessoa precisa conferir.'],
  '/publicacoes': ['Classifique ou descarte (com motivo) o que está na fila.', 'Sem processo? Use "Cadastrar processo automaticamente".'],
  '/captura': ['Ajuste filtros, rode a captura agora ou ligue a diária.'],
  '/processos': ['Cadastre o processo com cliente e advogado responsável.'],
}
