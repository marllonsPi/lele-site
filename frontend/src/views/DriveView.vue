<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { conectarGoogle } from '../api/importacao'
import { locaisNoDisco, moverLocaisParaDrive, salvarConfigDrive, statusDrive, type StatusDrive } from '../api/drive'
import Feedback from '../components/Feedback.vue'
import { useFeedback } from '../composables/useFeedback'

const rota = useRoute()
const { erro, aviso, executar } = useFeedback()

const status = ref<StatusDrive | null>(null)
const pasta = ref('')
const pendentes = ref(0)
const carregando = ref(true)
const movendo = ref(false)

const retorno = computed(() => {
  const g = rota.query.google
  return g === 'ok' ? 'Conta Google conectada ao Drive.' : g === 'negado' ? 'Você não autorizou o acesso ao Drive.' : g === 'erro' ? 'Não foi possível concluir a conexão com o Google.' : ''
})

async function recarregar() {
  await executar(async () => {
    status.value = await statusDrive()
    pasta.value = status.value.pastaRaizId
    pendentes.value = (await locaisNoDisco()).pendentes
  }, '', true)
  carregando.value = false
}

async function conectar() {
  await executar(async () => {
    window.location.href = (await conectarGoogle(true)).url
  })
}

async function usar() {
  const ok = await executar(async () => {
    status.value = await salvarConfigDrive(true, pasta.value)
  }, 'Configuração do Drive salva.')
  if (ok) {
    await recarregar()
  }
}

async function mover() {
  movendo.value = true
  try {
    for (let i = 0; i < 40; i++) {
      const r = await moverLocaisParaDrive()
      pendentes.value = r.restantes
      if (r.movidos === 0 || r.restantes === 0) {
        break
      }
    }
    aviso.value = pendentes.value === 0 ? 'Todos os anexos agora estão no Drive.' : `Restam ${pendentes.value} arquivo(s) no disco local; tente novamente.`
  } catch (e) {
    erro.value = e instanceof Error ? e.message : 'Não foi possível mover agora.'
  } finally {
    movendo.value = false
  }
}

onMounted(recarregar)
</script>

<template>
  <section>
    <h1>Drive do escritório</h1>
    <p class="suave">
      O Drive do escritório é onde ficam os documentos. Com ele ativo, <b>todo anexo novo</b> (processos, agenda, documentos do cliente) é gravado na
      pasta do cliente no Drive, e a pasta de cada cliente mostra os arquivos que já existem lá com uma sugestão do que associar. O sistema
      nunca apaga nada no Drive.
    </p>
    <Feedback :erro="erro" :aviso="aviso || retorno" />
    <p v-if="carregando">Carregando...</p>

    <template v-else-if="status">
      <p v-if="!status.googleDisponivel" class="erro" role="alert">A conexão com o Google não está configurada neste servidor.</p>

      <article class="cartao">
        <header class="cartao-topo">
          <b>1. Conectar a conta Google do escritório ao Drive</b>
          <span class="selo" :class="{ 'selo-pendente': !status.minhaContaComDrive }">{{ status.minhaContaComDrive ? 'Conectada' : 'Pendente' }}</span>
        </header>
        <p class="suave">Entre com a conta do escritório e autorize o acesso ao Drive. A senha do Google nunca passa pelo sistema.</p>
        <div class="cartao-acoes">
          <button type="button" class="botao-primario" :disabled="!status.googleDisponivel" @click="conectar">
            {{ status.minhaContaComDrive ? 'Reconectar' : 'Conectar com o Google' }}
          </button>
        </div>
      </article>

      <article class="cartao">
        <header class="cartao-topo">
          <b>2. Escolher a pasta dos clientes</b>
          <span class="selo" :class="{ 'selo-pendente': !status.ativo }">{{ status.ativo ? 'Ativo' : 'Pendente' }}</span>
        </header>
        <p class="suave">
          Cole o link (ou o ID) da pasta do Drive que tem uma subpasta por cliente, por exemplo "Documentos de Clientes". Esta conta Google passa a ser
          a conta que o sistema usa para ler e gravar.
        </p>
        <form class="grupo-botoes" @submit.prevent="usar">
          <label class="largo">
            Link ou ID da pasta
            <input v-model="pasta" placeholder="https://drive.google.com/drive/folders/..." required />
          </label>
          <button type="submit" class="botao-primario" :disabled="!status.minhaContaComDrive">Usar esta conta e esta pasta</button>
        </form>
        <p v-if="!status.minhaContaComDrive" class="suave">Conecte a conta no passo 1 antes.</p>
        <p v-if="status.contaDoDriveDefinida && !status.contaDoDriveConectada" class="erro">
          A conta definida para o Drive não está conectada com acesso ao Drive: reconecte no passo 1.
        </p>
      </article>

      <article class="cartao">
        <header class="cartao-topo">
          <b>3. Levar anexos antigos para o Drive</b>
          <span class="selo">{{ pendentes }} no disco local</span>
        </header>
        <p class="suave">
          Anexos de antes do Drive ficam no servidor. Este botão envia em lotes para a pasta do cliente/processo e passa a apontar para o Drive. A cópia
          local é mantida como segurança.
        </p>
        <div class="cartao-acoes">
          <button type="button" class="botao-secundario" :disabled="!status.ativo || pendentes === 0 || movendo" @click="mover">
            {{ movendo ? 'Enviando…' : 'Levar para o Drive' }}
          </button>
        </div>
      </article>
    </template>
  </section>
</template>
