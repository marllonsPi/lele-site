import { createRouter, createWebHistory, type RouterHistory } from 'vue-router'
import { useSessao } from '../composables/useSessao'
import LoginView from '../views/LoginView.vue'
import LayoutApp from '../components/LayoutApp.vue'
import InicioView from '../views/InicioView.vue'
import AgendaView from '../views/AgendaView.vue'
import PrazosView from '../views/PrazosView.vue'
import ConferenciaView from '../views/ConferenciaView.vue'
import ClientesView from '../views/ClientesView.vue'
import ProcessosView from '../views/ProcessosView.vue'
import UsuariosView from '../views/UsuariosView.vue'
import VisibilidadeView from '../views/VisibilidadeView.vue'
import CalendarioView from '../views/CalendarioView.vue'
import PublicacoesView from '../views/PublicacoesView.vue'
import CapturaView from '../views/CapturaView.vue'
import PainelView from '../views/PainelView.vue'
import AtendimentoView from '../views/AtendimentoView.vue'
import FinanceiroView from '../views/FinanceiroView.vue'
import RelatoriosView from '../views/RelatoriosView.vue'
import ModelosView from '../views/ModelosView.vue'
import SegurancaView from '../views/SegurancaView.vue'
import SistemaView from '../views/SistemaView.vue'
import NaoEncontradaView from '../views/NaoEncontradaView.vue'

export function criarRoteador(historico: RouterHistory = createWebHistory(import.meta.env.BASE_URL)) {
  const roteador = createRouter({
    history: historico,
    routes: [
      { path: '/login', name: 'login', component: LoginView, meta: { publica: true, titulo: 'Entrar' } },
      {
        path: '/',
        component: LayoutApp,
        children: [
          { path: '', name: 'inicio', component: InicioView, meta: { titulo: 'Início' } },
          { path: 'agenda', name: 'agenda', component: AgendaView, meta: { titulo: 'Agenda' } },
          { path: 'prazos', name: 'prazos', component: PrazosView, meta: { titulo: 'Prazos' } },
          { path: 'conferencia', name: 'conferencia', component: ConferenciaView, meta: { titulo: 'Conferência' } },
          { path: 'clientes', name: 'clientes', component: ClientesView, meta: { titulo: 'Clientes' } },
          { path: 'processos', name: 'processos', component: ProcessosView, meta: { titulo: 'Processos' } },
          { path: 'usuarios', name: 'usuarios', component: UsuariosView, meta: { titulo: 'Contas e OABs', perfis: ['SOCIA'] } },
          { path: 'visibilidade', name: 'visibilidade', component: VisibilidadeView, meta: { titulo: 'Visibilidade', perfis: ['SOCIA'] } },
          { path: 'calendario', name: 'calendario', component: CalendarioView, meta: { titulo: 'Calendário' } },
          { path: 'publicacoes', name: 'publicacoes', component: PublicacoesView, meta: { titulo: 'Publicações' } },
          { path: 'captura', name: 'captura', component: CapturaView, meta: { titulo: 'Captura', perfis: ['SOCIA'] } },
          { path: 'painel', name: 'painel', component: PainelView, meta: { titulo: 'Painel das sócias', perfis: ['SOCIA'] } },
          { path: 'atendimento', name: 'atendimento', component: AtendimentoView, meta: { titulo: 'Atendimento' } },
          { path: 'financeiro', name: 'financeiro', component: FinanceiroView, meta: { titulo: 'Financeiro', perfis: ['SOCIA'] } },
          { path: 'relatorios', name: 'relatorios', component: RelatoriosView, meta: { titulo: 'Relatórios' } },
          { path: 'modelos', name: 'modelos', component: ModelosView, meta: { titulo: 'Modelos de peças' } },
          { path: 'sistema', name: 'sistema', component: SistemaView, meta: { titulo: 'Sistema', perfis: ['SOCIA'], mestre: true } },
          { path: 'seguranca', name: 'seguranca', component: SegurancaView, meta: { titulo: 'Minha conta' } },
          { path: ':resto(.*)*', name: 'nao-encontrada', component: NaoEncontradaView, meta: { titulo: 'Página não encontrada' } },
        ],
      },
    ],
  })

  roteador.beforeEach(async (para) => {
    const { carregar } = useSessao()
    const eu = await carregar()
    if (!para.meta.publica && !eu) {
      return { name: 'login', query: para.fullPath === '/' ? {} : { volta: para.fullPath } }
    }
    if (para.name === 'login' && eu) {
      return { name: 'inicio' }
    }
    const permitidos = para.meta.perfis as string[] | undefined
    if (permitidos && eu && !permitidos.includes(eu.perfil)) {
      return { name: 'inicio' }
    }
    if (para.meta.mestre && eu && !eu.mestre) {
      return { name: 'inicio' }
    }
    return true
  })

  roteador.afterEach((para) => {
    document.title = (para.meta.titulo ? para.meta.titulo + ' · ' : '') + 'Crispim & Campos'
  })

  return roteador
}
