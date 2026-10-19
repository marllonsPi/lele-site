<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import {
  definirVisibilidade,
  ESCOPOS,
  listarUsuarios,
  listarVisibilidade,
  ROTULO_ESCOPO,
  type Escopo,
  type ParVisibilidade,
  type Usuario,
} from '../api/cadastros'
import Feedback from '../components/Feedback.vue'
import { useFeedback } from '../composables/useFeedback'

const { erro, aviso, executar } = useFeedback()

const usuarios = ref<Usuario[]>([])
const pares = ref<ParVisibilidade[]>([])
const carregando = ref(true)
/** Alteracoes ainda nao salvas, por observador. */
const rascunho = ref<Record<number, Set<string>>>({})

const ativos = computed(() => usuarios.value.filter((u) => u.ativo))

const chave = (titularId: number, escopo: Escopo) => `${titularId}:${escopo}`

function carregarRascunho() {
  const r: Record<number, Set<string>> = {}
  for (const u of usuarios.value) {
    r[u.id] = new Set(pares.value.filter((p) => p.observadorId === u.id).map((p) => chave(p.titularId, p.escopo)))
  }
  rascunho.value = r
}

function marcado(observadorId: number, titularId: number, escopo: Escopo): boolean {
  return rascunho.value[observadorId]?.has(chave(titularId, escopo)) ?? false
}

function alternar(observadorId: number, titularId: number, escopo: Escopo) {
  const s = rascunho.value[observadorId]
  if (!s) {
    return
  }
  const k = chave(titularId, escopo)
  if (!s.delete(k)) {
    s.add(k)
  }
}

async function recarregar() {
  await executar(async () => {
    ;[usuarios.value, pares.value] = await Promise.all([listarUsuarios(), listarVisibilidade()])
    carregarRascunho()
  }, '', true)
  carregando.value = false
}

async function salvar(observador: Usuario) {
  const regras: ParVisibilidade[] = [...(rascunho.value[observador.id] ?? [])].map((k) => {
    const [titular, escopo] = k.split(':')
    return { observadorId: observador.id, titularId: Number(titular), escopo: escopo as Escopo }
  })
  if (await executar(() => definirVisibilidade(observador.id, regras), `Visibilidade de ${observador.nome} salva.`)) {
    await recarregar()
  }
}

onMounted(recarregar)
</script>

<template>
  <section>
    <h1>Visibilidade</h1>
    <p class="suave">
      Para cada pessoa (quem vê), marque em nome de quem ela pode ver publicações, agenda e processos. Cada pessoa
      sempre vê o que é seu; o servidor aplica a regra.
    </p>
    <Feedback :erro="erro" :aviso="aviso" />
    <p v-if="carregando">Carregando...</p>

    <article v-for="o in ativos" :key="o.id" class="cartao">
      <h2 class="titulo-cartao">{{ o.nome }} vê:</h2>
      <div class="tabela-rolavel">
        <table>
          <thead>
            <tr>
              <th scope="col">Em nome de</th>
              <th v-for="e in ESCOPOS" :key="e" scope="col">{{ ROTULO_ESCOPO[e] }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="t in ativos.filter((x) => x.id !== o.id)" :key="t.id">
              <th scope="row">{{ t.nome }}</th>
              <td v-for="e in ESCOPOS" :key="e">
                <input
                  type="checkbox"
                  :checked="marcado(o.id, t.id, e)"
                  :aria-label="`${o.nome} vê ${ROTULO_ESCOPO[e]} de ${t.nome}`"
                  @change="alternar(o.id, t.id, e)"
                />
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <div class="cartao-acoes">
        <button type="button" class="botao-primario" @click="salvar(o)">Salvar</button>
      </div>
    </article>
  </section>
</template>
