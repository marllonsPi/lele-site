import type { Eu } from '../api/sessao'
import { itensPara } from '../router/navegacao'

export interface Comando {
  id: string
  titulo: string
  descricao: string
  /** Palavras que o usuario pode digitar para chegar aqui. */
  sinonimos: string[]
  /** Acoes com efeito pedem confirmacao antes de rodar. */
  confirmar?: string
  acao: { tipo: 'rota'; rota: string } | { tipo: 'captura-agora' } | { tipo: 'captura-diaria'; ligar: boolean } | { tipo: 'tema' } | { tipo: 'sair' }
}

export function semAcento(t: string): string {
  return t.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase()
}

const ATALHOS: { rota: string; titulo: string; sinonimos: string[]; perfis?: string[] }[] = [
  { rota: '/clientes', titulo: 'Novo cliente', sinonimos: ['cadastrar cliente', 'criar cliente', 'novo cliente'] },
  { rota: '/processos', titulo: 'Cadastrar processo', sinonimos: ['novo processo', 'criar processo', 'cadastrar processo'] },
  { rota: '/prazos', titulo: 'Lançar prazo', sinonimos: ['novo prazo', 'criar prazo', 'lancar prazo'] },
  { rota: '/agenda', titulo: 'Marcar audiência ou compromisso', sinonimos: ['audiencia', 'compromisso', 'marcar', 'reuniao'] },
  { rota: '/usuarios', titulo: 'Nova conta ou OAB', sinonimos: ['nova conta', 'criar usuario', 'adicionar oab', 'advogado'], perfis: ['SOCIA'] },
  { rota: '/publicacoes', titulo: 'Publicações a classificar', sinonimos: ['fila', 'a classificar', 'intimacoes', 'djen', 'pendentes'] },
  { rota: '/conferencia', titulo: 'Conferir prazos', sinonimos: ['conferir', 'revisar prazos', 'conferencia'] },
]

/** Catalogo de comandos para o perfil logado; o servidor continua decidindo o que cada acao pode. */
export function comandosPara(eu: Eu): Comando[] {
  const socia = eu.perfil === 'SOCIA'
  const lista: Comando[] = itensPara(eu.perfil, eu.mestre === true).map((i) => ({
    id: 'ir:' + i.rota,
    titulo: `Ir para ${i.rotulo}`,
    descricao: i.grupo,
    sinonimos: ['abrir', 'tela', 'ver', i.rotulo],
    acao: { tipo: 'rota', rota: i.rota },
  }))
  for (const a of ATALHOS) {
    if (!a.perfis || a.perfis.includes(eu.perfil)) {
      lista.push({ id: 'atalho:' + a.titulo, titulo: a.titulo, descricao: 'Abre a tela certa para isso', sinonimos: a.sinonimos, acao: { tipo: 'rota', rota: a.rota } })
    }
  }
  if (socia) {
    lista.push(
      { id: 'captura-agora', titulo: 'Capturar publicações agora', descricao: 'Busca no DJEN e no Gmail, passa pelos filtros e coloca na fila', sinonimos: ['executar captura', 'buscar publicacoes', 'puxar', 'atualizar'], confirmar: 'Executar a captura agora?', acao: { tipo: 'captura-agora' } },
      { id: 'captura-ligar', titulo: 'Ligar captura diária', descricao: 'Todo dia às 6h, últimos 3 dias', sinonimos: ['ativar captura', 'automatica', 'agendar'], confirmar: 'Ligar a captura diária automática?', acao: { tipo: 'captura-diaria', ligar: true } },
      { id: 'captura-desligar', titulo: 'Desligar captura diária', descricao: 'Para a busca automática; a manual continua', sinonimos: ['desativar captura', 'parar captura'], confirmar: 'Desligar a captura diária automática?', acao: { tipo: 'captura-diaria', ligar: false } },
    )
  }
  lista.push(
    { id: 'tema', titulo: 'Alternar tema claro/escuro', descricao: 'Aparência', sinonimos: ['escuro', 'claro', 'dark', 'modo'], acao: { tipo: 'tema' } },
    { id: 'sair', titulo: 'Sair do sistema', descricao: 'Encerra a sessão', sinonimos: ['logout', 'encerrar'], confirmar: 'Sair do sistema?', acao: { tipo: 'sair' } },
  )
  return lista
}

/** Pontua por palavras (sem acento, por prefixo); vazio devolve os comandos na ordem do catalogo. */
export function buscar(comandos: Comando[], consulta: string, limite = 8): Comando[] {
  const palavras = semAcento(consulta).split(/\s+/).filter(Boolean)
  if (palavras.length === 0) return comandos.slice(0, limite)
  const pontuados = comandos
    .map((c) => {
      const titulo = semAcento(c.titulo)
      const alvo = [titulo, semAcento(c.descricao), ...c.sinonimos.map(semAcento)]
      let pontos = 0
      for (const p of palavras) {
        if (titulo.split(/\s+/).some((w) => w.startsWith(p))) pontos += 3
        else if (alvo.some((a) => a.split(/\s+/).some((w) => w.startsWith(p)))) pontos += 2
        else if (alvo.some((a) => a.includes(p))) pontos += 1
        else return { c, pontos: 0 }
      }
      return { c, pontos }
    })
    .filter((x) => x.pontos > 0)
  return pontuados.sort((a, b) => b.pontos - a.pontos).slice(0, limite).map((x) => x.c)
}

/** Procura nos verbetes por palavras (sem acento, por prefixo); exige que todas as palavras da pergunta apareçam. */
export function responder(verbetes: import('./conhecimento').Verbete[], consulta: string, limite = 2): import('./conhecimento').Verbete[] {
  const ignorar = new Set(['como', 'que', 'qual', 'quais', 'para', 'por', 'uma', 'um', 'de', 'do', 'da', 'o', 'a', 'e', 'em', 'eu', 'faco', 'fazer', 'posso', 'pode', 'se', 'no', 'na'])
  const palavras = semAcento(consulta).split(/[^a-z0-9]+/).filter((p) => p.length > 1 && !ignorar.has(p))
  if (palavras.length === 0) return []
  return verbetes
    .map((v) => {
      const alvo = [v.titulo, ...v.gatilhos].map(semAcento)
      const texto = semAcento(v.resposta)
      let pontos = 0
      for (const p of palavras) {
        if (alvo.some((a) => a.split(/\s+/).some((w) => w.startsWith(p)))) pontos += 3
        else if (p.length >= 4 && alvo.some((a) => a.split(/\s+/).some((w) => w.startsWith(p.slice(0, 4))))) pontos += 2
        else if (texto.includes(p)) pontos += 1
      }
      return { v, pontos }
    })
    .filter((x) => x.pontos >= 3)
    .sort((a, b) => b.pontos - a.pontos)
    .slice(0, limite)
    .map((x) => x.v)
}
