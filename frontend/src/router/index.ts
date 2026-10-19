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
          { path: 'usuarios', name: 'usuarios', component: UsuariosView, meta: { titulo: 'Usuários e OABs', perfis: ['SOCIA'] } },
          { path: 'visibilidade', name: 'visibilidade', component: VisibilidadeView, meta: { titulo: 'Visibilidade', perfis: ['SOCIA'] } },
          { path: 'calendario', name: 'calendario', component: CalendarioView, meta: { titulo: 'Calendário' } },
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
    return true
  })

  roteador.afterEach((para) => {
    document.title = (para.meta.titulo ? para.meta.titulo + ' · ' : '') + 'Crispim & Campos'
  })

  return roteador
}
