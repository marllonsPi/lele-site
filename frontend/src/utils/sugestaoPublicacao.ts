import type { TipoAgendamento } from '../api/agenda'

export interface Sugestao {
  tipo: TipoAgendamento
  dias: number | null
  urgente: boolean
  descricao: string
  motivos: string[]
}

const REGRAS: { tipo: TipoAgendamento; padrao: RegExp; rotulo: string; diasPadrao?: number }[] = [
  { tipo: 'CON', padrao: /contest(ar|a[çc][ãa]o)|cita[çc][ãa]o/i, rotulo: 'Contestação', diasPadrao: 15 },
  { tipo: 'REC', padrao: /recurso|apela[çc][ãa]o|agravo|embargos|contrarraz/i, rotulo: 'Recurso', diasPadrao: 15 },
  { tipo: 'INI', padrao: /emendar|emenda (da|à) inicial|aditar/i, rotulo: 'Emenda à inicial', diasPadrao: 15 },
  { tipo: 'MAN', padrao: /manifest(ar|e-se|e se|a[çc][ãa]o)|intim(e|a)-?se|intimad|r[ée]plica|especificar provas|cumprir/i, rotulo: 'Manifestação', diasPadrao: 5 },
]

/** Soma dias uteis (sab/dom fora; feriados nao conhecidos — a conferencia do prazo cobre). */
export function somarDiasUteis(inicio: string, dias: number): string {
  const d = new Date(inicio + 'T12:00:00')
  let falta = dias
  while (falta > 0) {
    d.setDate(d.getDate() + 1)
    if (d.getDay() !== 0 && d.getDay() !== 6) falta--
  }
  const mm = String(d.getMonth() + 1).padStart(2, '0')
  const dd = String(d.getDate()).padStart(2, '0')
  return `${d.getFullYear()}-${mm}-${dd}`
}

function limpar(texto: string): string {
  return texto.replace(/<[^>]*>/g, ' ').replace(/\s+/g, ' ').trim()
}

/** Le o texto da publicacao e propoe tipo, prazo e descricao. Sempre sugestao: a socia confere e a conferencia cobre o prazo. */
export function sugerir(texto: string): Sugestao {
  const t = limpar(texto)
  const motivos: string[] = []
  let tipo: TipoAgendamento = 'MAN'
  let diasRegra: number | undefined
  const achada = REGRAS.find((r) => r.padrao.test(t))
  if (achada) {
    tipo = achada.tipo
    diasRegra = achada.diasPadrao
    motivos.push(`tipo ${achada.rotulo} (texto menciona o ato)`)
  }
  const m = /prazo (?:legal )?(?:de|d[eo]s?)?\s*(\d{1,3})\s*\(?[^)]{0,30}?\)?\s*dias?/i.exec(t) ?? /(\d{1,3})\s*\(?[^)]{0,30}?\)?\s*dias?/i.exec(t)
  const dias = m ? Number(m[1]) : (diasRegra ?? null)
  if (m) motivos.push(`prazo de ${m[1]} dias lido do texto`)
  else if (diasRegra) motivos.push(`prazo padrão de ${diasRegra} dias para ${achada!.rotulo}`)
  const urgente = /urg[êe]ncia|liminar|tutela|imediat|48 horas|24 horas|\b[1-5] dias úteis/i.test(t)
  if (urgente) motivos.push('há termo de urgência')
  const descricao = t.length <= 300 ? t : t.slice(0, 297) + '...'
  return { tipo, dias: dias && dias > 0 && dias <= 180 ? dias : null, urgente, descricao, motivos }
}
