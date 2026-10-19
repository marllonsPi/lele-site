const formato = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' })

export function formatarMoeda(valor: number | null | undefined): string {
  return formato.format(valor ?? 0)
}
