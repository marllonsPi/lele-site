import { vi } from 'vitest'
import type { ItemAgenda, Prazo } from '../api/agenda'
import type { Eu } from '../api/sessao'
import { useSessao } from '../composables/useSessao'
import { useUsuarios } from '../composables/useUsuarios'

export function item(parcial: Partial<ItemAgenda> = {}): ItemAgenda {
  return {
    id: 10,
    processoId: 1,
    clienteId: 1,
    tipo: 'CON',
    corTipo: 'laranja',
    descricao: 'Contestação do caso',
    responsavelId: 3,
    criadoPor: 1,
    urgente: false,
    status: 'PENDENTE',
    data: '2026-10-20',
    hora: null,
    duracaoMin: null,
    modalidade: null,
    localOuLink: null,
    inicioPrazo: null,
    prazoInterno: '2026-10-20',
    prazoFatal: '2026-10-23',
    statusVisual: 'NENHUM',
    alertaAudiencia: false,
    aguardandoConferenciaPrazo: false,
    ...parcial,
  }
}

export function prazo(parcial: Partial<Prazo> = {}): Prazo {
  return {
    id: 20,
    processoId: 1,
    tipo: 'CON',
    cor: 'laranja',
    descricao: 'Prazo de teste',
    responsavelId: 3,
    lancadoPor: 1,
    urgente: false,
    status: 'AGUARDANDO_CONFERENCIA_PRAZO',
    inicioPrazo: '2026-10-10',
    prazoInterno: '2026-10-20',
    prazoFatal: '2026-10-23',
    conferidoPor: null,
    conferidoEm: null,
    ...parcial,
  }
}

/** Define quem esta logado sem passar pela rede. */
export function logarComo(eu: Eu, usuarios: unknown[] = []): Promise<unknown> {
  const sessao = useSessao()
  sessao.reiniciar()
  vi.stubGlobal(
    'fetch',
    vi.fn(async (url: string) =>
      url === '/api/me'
        ? new Response(JSON.stringify(eu), { status: 200 })
        : url === '/api/usuarios' && usuarios.length
          ? new Response(JSON.stringify(usuarios), { status: 200 })
          : new Response('[]', { status: 403 }),
    ),
  )
  useUsuarios().reiniciar()
  return sessao.carregar()
}
