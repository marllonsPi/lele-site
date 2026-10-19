/** Datas da API vem em ISO (aaaa-mm-dd); formatar sem passar por Date evita deslocar o dia pelo fuso. */
export function formatarData(iso: string | null | undefined): string {
  if (!iso) {
    return ''
  }
  const [a, m, d] = iso.substring(0, 10).split('-')
  return `${d}/${m}/${a}`
}

export function formatarHora(hora: string | null | undefined): string {
  return hora ? hora.substring(0, 5) : ''
}

export function formatarDataHora(instante: string | null | undefined): string {
  if (!instante) {
    return ''
  }
  const d = new Date(instante)
  return d.toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' })
}

export function formatarTamanho(bytes: number): string {
  if (bytes < 1024) {
    return bytes + ' B'
  }
  return bytes < 1024 * 1024 ? (bytes / 1024).toFixed(0) + ' KB' : (bytes / 1024 / 1024).toFixed(1) + ' MB'
}

/** Data de hoje no fuso local, em ISO. */
export function hojeIso(deslocamentoDias = 0): string {
  const d = new Date()
  d.setDate(d.getDate() + deslocamentoDias)
  const mm = String(d.getMonth() + 1).padStart(2, '0')
  const dd = String(d.getDate()).padStart(2, '0')
  return `${d.getFullYear()}-${mm}-${dd}`
}
