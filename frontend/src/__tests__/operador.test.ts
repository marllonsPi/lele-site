import { describe, expect, it } from 'vitest'
import { buscar, comandosPara } from '../operador/comandos'

const socia = { id: 1, nome: 'Ana', perfil: 'SOCIA', mestre: false } as never

describe('operador', () => {
  it('acha comandos por sinonimo e sem acento', () => {
    const c = comandosPara(socia)
    expect(buscar(c, 'audiencia')[0]!.id).toContain('Marcar')
    expect(buscar(c, 'capturar publicacoes')[0]!.id).toBe('captura-agora')
    expect(buscar(c, 'desligar captura')[0]!.id).toBe('captura-desligar')
  })

  it('nao oferece captura a quem nao e socia', () => {
    const c = comandosPara({ id: 2, nome: 'Bia', perfil: 'ESTAGIARIO', mestre: false } as never)
    expect(c.some((x) => x.id.startsWith('captura'))).toBe(false)
  })
})

import { responder } from '../operador/comandos'
import { VERBETES } from '../operador/conhecimento'

describe('conhecimento do assistente', () => {
  it('responde duvidas do sistema', () => {
    expect(responder(VERBETES, 'como lanço um prazo?')[0]!.id).toBe('prazo-lancar')
    expect(responder(VERBETES, 'quem pode conferir prazo')[0]!.id).toBe('prazo-conferencia')
    expect(responder(VERBETES, 'filtro trt exceção')[0]!.id).toBe('filtros')
  })

  it('nao inventa resposta para o que nao sabe', () => {
    expect(responder(VERBETES, 'receita de bolo')).toEqual([])
  })
})
