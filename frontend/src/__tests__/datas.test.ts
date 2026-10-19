import { describe, expect, it } from 'vitest'
import { formatarData, formatarHora, formatarTamanho, hojeIso } from '../utils/datas'

describe('datas', () => {
  it('formata ISO sem deslocar o dia', () => {
    expect(formatarData('2026-10-01')).toBe('01/10/2026')
    expect(formatarData(null)).toBe('')
  })

  it('corta segundos da hora', () => {
    expect(formatarHora('10:30:00')).toBe('10:30')
    expect(formatarHora(null)).toBe('')
  })

  it('formata tamanhos', () => {
    expect(formatarTamanho(500)).toBe('500 B')
    expect(formatarTamanho(2048)).toBe('2 KB')
    expect(formatarTamanho(3 * 1024 * 1024)).toBe('3.0 MB')
  })

  it('hoje ISO tem o formato aaaa-mm-dd', () => {
    expect(hojeIso()).toMatch(/^\d{4}-\d{2}-\d{2}$/)
    expect(hojeIso(1) > hojeIso()).toBe(true)
  })
})
