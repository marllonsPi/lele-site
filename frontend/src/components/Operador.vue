<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { carregarPrazos } from '../api/agenda'
import { executarCaptura, ligarAgendamento, listarPublicacoes } from '../api/publicacoes'
import { hojeIso } from '../utils/datas'
import { buscar, comandosPara, responder, type Comando } from '../operador/comandos'
import { DICAS_DA_TELA, VERBETES } from '../operador/conhecimento'
import { sugestoesPara } from '../operador/sugestoes'
import { useFeedback } from '../composables/useFeedback'
import { useSessao } from '../composables/useSessao'
import { useTema } from '../composables/useTema'
import Feedback from './Feedback.vue'

const roteador = useRouter()
const rotaAtual = useRoute()
const { eu, sair } = useSessao()
const { alternar } = useTema()
const { erro, aviso, executar, ocupado } = useFeedback()

const aberto = ref(false)
const consulta = ref('')
const indice = ref(0)
const pendente = ref<Comando | null>(null)
const campo = ref<HTMLInputElement | null>(null)

const todos = computed(() => (eu.value ? comandosPara(eu.value) : []))
const aClassificar = ref(0)
const paraConferir = ref(0)
const fataisProximos = ref(0)
const sugestoes = computed(() =>
  sugestoesPara({ rota: rotaAtual.path, aClassificar: aClassificar.value, paraConferir: paraConferir.value, fataisProximos: fataisProximos.value }, todos.value),
)
const respostas = computed(() => responder(VERBETES, consulta.value))
const dicas = computed(() => DICAS_DA_TELA[rotaAtual.path] ?? [])
const dispensada = ref('')
const balao = computed(() => (!aberto.value && sugestoes.value[0] && dispensada.value !== sugestoes.value[0].texto ? sugestoes.value[0] : null))

async function atualizarPendencias() {
  if (!eu.value) return
  try {
    if (eu.value.perfil === 'SOCIA') aClassificar.value = (await listarPublicacoes('A_CLASSIFICAR')).length
    const prazos = await carregarPrazos()
    const amanha = hojeIso(1)
    if (eu.value.perfil === 'SOCIA' || eu.value.perfil === 'ADVOGADO') {
      paraConferir.value = prazos.filter((p) => p.status === 'AGUARDANDO_CONFERENCIA_PRAZO' && p.lancadoPor !== eu.value!.id).length
    }
    fataisProximos.value = prazos.filter(
      (p) => p.responsavelId === eu.value!.id && !['FINALIZADO', 'CONFERIDO', 'EM_CONFERENCIA_SUPERVISOR', 'EM_CONFERENCIA_SOCIA'].includes(p.status) && p.prazoFatal <= amanha,
    ).length
  } catch {
    // o assistente nunca atrapalha a tela
  }
}

function aceitar(comando: string) {
  const c = todos.value.find((x) => x.id === comando)
  if (!c) return
  aberto.value = true
  consulta.value = ''
  escolher(c)
}

watch(() => rotaAtual.path, atualizarPendencias)

const opcoes = computed(() => buscar(todos.value, consulta.value))

watch(consulta, () => {
  indice.value = 0
  pendente.value = null
})

async function abrir() {
  aberto.value = true
  consulta.value = ''
  pendente.value = null
  await nextTick()
  campo.value?.focus()
}

function fechar() {
  aberto.value = false
  pendente.value = null
}

async function rodar(c: Comando) {
  const a = c.acao
  if (a.tipo === 'rota') {
    await roteador.push(a.rota)
    fechar()
  } else if (a.tipo === 'tema') {
    alternar()
    fechar()
  } else if (a.tipo === 'sair') {
    await sair()
    await roteador.replace({ name: 'login' })
    fechar()
  } else if (a.tipo === 'captura-agora') {
    if (await executar(() => executarCaptura(), 'Captura executada. Veja as publicações a classificar.')) fechar()
  } else if (await executar(() => ligarAgendamento(a.ligar), a.ligar ? 'Captura diária ligada.' : 'Captura diária desligada.')) {
    fechar()
  }
  pendente.value = null
}

async function escolher(c: Comando) {
  if (c.confirmar && pendente.value?.id !== c.id) {
    pendente.value = c
    return
  }
  await rodar(c)
}

function teclas(e: KeyboardEvent) {
  if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k') {
    e.preventDefault()
    aberto.value ? fechar() : abrir()
  }
}

function navegar(e: KeyboardEvent) {
  if (e.key === 'ArrowDown') {
    e.preventDefault()
    indice.value = Math.min(indice.value + 1, opcoes.value.length - 1)
  } else if (e.key === 'ArrowUp') {
    e.preventDefault()
    indice.value = Math.max(indice.value - 1, 0)
  } else if (e.key === 'Enter') {
    e.preventDefault()
    const c = pendente.value ?? opcoes.value[indice.value]
    if (c) escolher(c)
  } else if (e.key === 'Escape') {
    pendente.value ? (pendente.value = null) : fechar()
  }
}

onMounted(() => {
  window.addEventListener('keydown', teclas)
  atualizarPendencias()
})
onBeforeUnmount(() => window.removeEventListener('keydown', teclas))
defineExpose({ abrir })
</script>

<template>
  <div class="assistente">
    <div v-if="balao" class="assistente-balao" role="status">
      <p>{{ balao.texto }}</p>
      <button type="button" class="botao-primario" @click="aceitar(balao.comando)">Sim, faça</button>
      <button type="button" class="botao-link" @click="dispensada = balao.texto">Agora não</button>
    </div>
    <button type="button" class="assistente-botao" title="Assistente (Ctrl+K)" aria-label="Abrir o assistente" @click="abrir">
      <span aria-hidden="true">✦</span>
    </button>
  </div>
  <div v-if="aberto" class="operador-veu" @click.self="fechar">
    <div class="operador" role="dialog" aria-modal="true" aria-label="Assistente do sistema">
      <input
        ref="campo"
        v-model="consulta"
        class="operador-campo"
        type="text"
        placeholder="Pergunte ou peça: como lanço um prazo? capturar publicações, ir para clientes…"
        autocomplete="off"
        aria-controls="operador-opcoes"
        @keydown="navegar"
      />
      <Feedback :erro="erro" :aviso="aviso" />
      <div v-if="pendente" class="operador-confirma">
        <p><b>{{ pendente.titulo }}</b> — {{ pendente.confirmar }}</p>
        <button type="button" class="botao-primario" :disabled="ocupado" @click="rodar(pendente)">Confirmar</button>
        <button type="button" class="botao-secundario" @click="pendente = null">Voltar</button>
      </div>
      <template v-else>
        <div v-for="v in respostas" :key="v.id" class="operador-resposta">
          <b>{{ v.titulo }}</b>
          <p>{{ v.resposta }}</p>
          <ol v-if="v.passos"><li v-for="p in v.passos" :key="p">{{ p }}</li></ol>
          <button v-if="v.comando" type="button" class="botao-secundario" @click="aceitar(v.comando)">Fazer isso para mim</button>
        </div>
        <div v-if="!consulta.trim() && dicas.length" class="operador-dicas">
          <b class="suave">Nesta tela</b>
          <ul><li v-for="d in dicas" :key="d">{{ d }}</li></ul>
        </div>
        <div v-if="!consulta.trim() && sugestoes.length" class="operador-sugestoes">
          <button v-for="g in sugestoes" :key="g.comando" type="button" class="botao-secundario" @click="aceitar(g.comando)">{{ g.texto }}</button>
        </div>
      <ul id="operador-opcoes" class="operador-lista" role="listbox">
        <li
          v-for="(c, i) in opcoes"
          :key="c.id"
          role="option"
          :aria-selected="i === indice"
          :class="{ ativa: i === indice }"
          @mouseenter="indice = i"
          @click="escolher(c)"
        >
          <b>{{ c.titulo }}</b>
          <span class="suave">{{ c.descricao }}</span>
        </li>
        <li v-if="opcoes.length === 0 && !consulta.trim()" class="suave">Digite o que você quer fazer.</li>
      </ul>
      </template>
      <p class="suave operador-dica">↑↓ escolhem · Enter executa · Esc fecha. Ações com efeito pedem confirmação.</p>
    </div>
  </div>
</template>

<style scoped>
.assistente { position: fixed; right: 1rem; bottom: 1rem; z-index: 90; display: flex; flex-direction: column; align-items: flex-end; gap: 0.5rem; }
.assistente-botao { width: 3.25rem; height: 3.25rem; border-radius: 50%; border: 0; font-size: 1.4rem; cursor: pointer; background: var(--cor-primaria, #7a5a1e); color: var(--cor-primaria-texto, #fff); box-shadow: 0 6px 18px rgb(0 0 0 / 0.3); }
.assistente-balao { max-width: 18rem; background: var(--cor-superficie, #fffdf9); color: var(--cor-texto, #2a241c); border: 1px solid var(--cor-borda, #ddd3c3); border-radius: 12px; padding: 0.75rem; box-shadow: 0 6px 22px rgb(0 0 0 / 0.3); display: flex; flex-wrap: wrap; gap: 0.5rem; align-items: center; }
.assistente-balao p { margin: 0; flex-basis: 100%; }
.operador-resposta { background: rgb(127 127 127 / 0.12); border-radius: 8px; padding: 0.6rem 0.75rem; margin: 0.5rem 0; }
.operador-resposta p { margin: 0.3rem 0; }
.operador-resposta ol { margin: 0.3rem 0 0.5rem 1.2rem; padding: 0; }
.operador-dicas ul { margin: 0.2rem 0 0.4rem 1.2rem; padding: 0; }
.operador-sugestoes { display: flex; flex-direction: column; gap: 0.4rem; margin: 0.5rem 0; }
.operador-veu { position: fixed; inset: 0; background: rgb(0 0 0 / 0.45); display: flex; justify-content: center; align-items: flex-start; padding: 12vh 1rem 1rem; z-index: 100; }
.operador { background: var(--cor-superficie, #fffdf9); color: var(--cor-texto, #2a241c); border: 1px solid var(--cor-borda, #ddd3c3); width: min(640px, 100%); border-radius: 12px; padding: 1rem; box-shadow: 0 12px 40px rgb(0 0 0 / 0.35); }
.operador-campo { width: 100%; font-size: 1.05rem; padding: 0.75rem; box-sizing: border-box; }
.operador-lista { list-style: none; margin: 0.5rem 0; padding: 0; max-height: 50vh; overflow: auto; }
.operador-lista li { display: flex; flex-direction: column; padding: 0.55rem 0.75rem; border-radius: 8px; cursor: pointer; }
.operador-lista li.ativa { background: rgb(127 127 127 / 0.2); }
.operador input, .operador-campo { background: var(--cor-fundo, #f6f1e8); color: var(--cor-texto, #2a241c); border: 1px solid var(--cor-borda, #ddd3c3); border-radius: 8px; }
.operador-confirma { padding: 0.75rem 0; display: flex; gap: 0.5rem; align-items: center; flex-wrap: wrap; }
.operador-dica { font-size: 0.8rem; margin: 0; }
</style>
