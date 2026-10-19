import { createRouter, createWebHistory, type RouterHistory } from 'vue-router'
import { useSessao } from '../composables/useSessao'
import LoginView from '../views/LoginView.vue'
import LayoutApp from '../components/LayoutApp.vue'
import InicioView from '../views/InicioView.vue'
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
    return true
  })

  roteador.afterEach((para) => {
    document.title = (para.meta.titulo ? para.meta.titulo + ' · ' : '') + 'Crispim & Campos'
  })

  return roteador
}
