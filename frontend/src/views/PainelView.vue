<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { carregarPainel, type Painel } from '../api/publicacoes'
import CartaoAgenda from '../components/CartaoAgenda.vue'
import Feedback from '../components/Feedback.vue'
import { useFeedback } from '../composables/useFeedback'
import { useUsuarios } from '../composables/useUsuarios'

const { carregar: carregarUsuarios, nome } = useUsuarios()
const { erro, executar } = useFeedback()
const painel = ref<Painel | null>(null)

onMounted(async () => {
  await carregarUsuarios()
  await executar(async () => {
    painel.value = await carregarPainel()
  })
})

const secoes = [
  { chave: 'atrasos', titulo: 'Atrasos' },
  { chave: 'prazosDaSemana', titulo: 'Prazos da semana' },
  { chave: 'compromissosProximos', titulo: 'Audiências e atendimentos próximos (14 dias)' },
  { chave: 'aguardandoConferenciaSocia', titulo: 'Aguardando conferência das sócias' },
  { chave: 'aguardandoConferenciaPrazo', titulo: 'Prazos aguardando conferência da contagem' },
] as const
</script>

<template>
  <section>
    <h1>Painel das sócias</h1>
    <Feedback :erro="erro" />
    <p v-if="!painel && !erro">Carregando...</p>
    <template v-if="painel">
      <p>
        Fila "A classificar":
        <router-link :to="{ name: 'publicacoes' }"><b>{{ painel.aClassificar }}</b> publicação(ões)</router-link>
      </p>

      <h2>Carga por pessoa</h2>
      <div class="tabela-rolavel">
        <table>
          <thead>
            <tr><th scope="col">Pessoa</th><th scope="col">Abertos</th><th scope="col">Vencidos</th><th scope="col">Urgentes</th></tr>
          </thead>
          <tbody>
            <tr v-for="c in painel.porPessoa" :key="c.usuarioId">
              <th scope="row">{{ nome(c.usuarioId) }}</th>
              <td>{{ c.abertos }}</td>
              <td :class="{ perigo: c.vencidos > 0 }">{{ c.vencidos }}</td>
              <td>{{ c.urgentes }}</td>
            </tr>
          </tbody>
        </table>
      </div>

      <template v-for="s in secoes" :key="s.chave">
        <h2>{{ s.titulo }} <span class="suave">({{ painel[s.chave].length }})</span></h2>
        <p v-if="painel[s.chave].length === 0" class="suave">Nada aqui.</p>
        <CartaoAgenda v-for="i in painel[s.chave]" :key="s.chave + i.id" :item="i" />
      </template>
    </template>
  </section>
</template>
