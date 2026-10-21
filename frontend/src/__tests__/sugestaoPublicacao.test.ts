import { describe, expect, it } from 'vitest'
import { somarDiasUteis, sugerir } from '../utils/sugestaoPublicacao'

describe('sugestaoPublicacao', () => {
  it('pula fins de semana', () => {
    expect(somarDiasUteis('2026-10-16', 1)).toBe('2026-10-19') // sexta -> segunda
    expect(somarDiasUteis('2026-10-19', 5)).toBe('2026-10-26')
  })

  it('le o prazo e o tipo do texto', () => {
    const s = sugerir('Fica intimada a parte ré para apresentar contestação no prazo de 15 (quinze) dias.')
    expect(s.tipo).toBe('CON')
    expect(s.dias).toBe(15)
    expect(s.urgente).toBe(false)
  })

  it('detecta urgencia e usa manifestacao por padrao', () => {
    const s = sugerir('Intime-se para manifestar sobre a tutela de urgência.')
    expect(s.tipo).toBe('MAN')
    expect(s.urgente).toBe(true)
    expect(s.dias).toBe(5)
  })
})
