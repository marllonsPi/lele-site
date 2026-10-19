import { createApp } from 'vue'
import App from './App.vue'
import { criarRoteador } from './router'
import { aoPerderSessao } from './composables/useSessao'
import { useTema } from './composables/useTema'
import './styles.css'

const roteador = criarRoteador()
useTema().aplicar()

// Sessao expirada no meio do uso: volta ao login e depois retorna a esta pagina.
aoPerderSessao(() => {
  const atual = roteador.currentRoute.value
  if (atual.name !== 'login') {
    roteador.push({ name: 'login', query: { volta: atual.fullPath } })
  }
})

createApp(App).use(roteador).mount('#app')
