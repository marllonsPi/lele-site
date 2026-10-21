<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { listarDocumentos, enviarDocumento, ROTULO_TIPO_DOCUMENTO, urlDownloadDocumento, type DocumentoCliente } from '../api/importacao'
import { associarDoDrive, sugestoesDoDrive, type SugestoesDrive } from '../api/drive'
import { mensagemDeErro } from '../composables/useFeedback'
import { formatarDataHora, formatarTamanho } from '../utils/datas'

const props = defineProps<{ clienteId: number; podeEnviar: boolean }>()
const documentos = ref<DocumentoCliente[]>([])
const erro = ref('')
const enviando = ref(false)
const drive = ref<SugestoesDrive | null>(null)
const escolhidos = ref<Set<string>>(new Set())
const buscando = ref(false)
const aviso = ref('')
const nSelecionados = computed(() => escolhidos.value.size)

async function verDrive() {
  erro.value = ''
  aviso.value = ''
  buscando.value = true
  try {
    drive.value = await sugestoesDoDrive(props.clienteId)
    escolhidos.value = new Set(drive.value.itens.filter((i) => i.processoId !== null || i.tipo !== 'OUTRO').map((i) => i.driveId))
  } catch (e) {
    erro.value = mensagemDeErro(e)
  } finally {
    buscando.value = false
  }
}

function alternarEscolha(id: string) {
  const s = new Set(escolhidos.value)
  if (!s.delete(id)) {
    s.add(id)
  }
  escolhidos.value = s
}

async function associar() {
  if (!drive.value) {
    return
  }
  erro.value = ''
  try {
    const itens = drive.value.itens
      .filter((i) => escolhidos.value.has(i.driveId))
      .map((i) => ({ driveId: i.driveId, tipo: i.tipo, processoId: i.processoId }))
    const r = await associarDoDrive(props.clienteId, itens)
    aviso.value = `${r.associados} arquivo(s) associado(s).`
    await carregar()
    await verDrive()
  } catch (e) {
    erro.value = mensagemDeErro(e)
  }
}

async function carregar() {
  try {
    documentos.value = await listarDocumentos(props.clienteId)
  } catch (e) {
    erro.value = mensagemDeErro(e)
  }
}

async function enviar(ev: Event) {
  const campo = ev.target as HTMLInputElement
  const arquivo = campo.files?.[0]
  if (!arquivo) {
    return
  }
  erro.value = ''
  enviando.value = true
  try {
    await enviarDocumento(props.clienteId, arquivo)
    await carregar()
  } catch (e) {
    erro.value = mensagemDeErro(e)
  } finally {
    enviando.value = false
    campo.value = ''
  }
}

onMounted(carregar)
</script>

<template>
  <section class="detalhe">
    <h3>Documentos do cliente</h3>
    <p v-if="erro" class="erro" role="alert">{{ erro }}</p>
    <p v-if="documentos.length === 0" class="suave">Nenhum documento na pasta do cliente.</p>
    <ul class="lista-simples">
      <li v-for="d in documentos" :key="d.id">
        <a :href="urlDownloadDocumento(d.id)">{{ d.nome }}</a>
        <span class="suave">
          · {{ ROTULO_TIPO_DOCUMENTO[d.tipo] }} · {{ formatarTamanho(d.tamanho) }} · {{ formatarDataHora(d.criadoEm) }}
          <template v-if="d.restritoSocias"> · restrito às sócias</template>
        </span>
      </li>
    </ul>
    <div v-if="podeEnviar" class="cartao-acoes">
      <button type="button" class="botao-secundario" :disabled="buscando" @click="verDrive">
        {{ buscando ? 'Buscando…' : 'Ver arquivos deste cliente no Drive' }}
      </button>
    </div>
    <p v-if="aviso" class="ok" role="status">{{ aviso }}</p>
    <div v-if="drive" class="detalhe">
      <p v-if="!drive.driveAtivo" class="suave">O Drive do escritório ainda não está ativo: veja a tela “Drive do escritório”.</p>
      <p v-else-if="!drive.pastaEncontrada" class="suave">
        Não achei uma pasta com o nome deste cliente na pasta raiz do Drive. Os anexos novos criam essa pasta sozinhos.
      </p>
      <template v-else>
        <p class="suave">Pasta “{{ drive.pastaNome }}”: {{ drive.itens.length }} arquivo(s) ainda não associados.</p>
        <ul class="lista-simples">
          <li v-for="i in drive.itens" :key="i.driveId">
            <label class="opcao-linha">
              <input type="checkbox" :checked="escolhidos.has(i.driveId)" @change="alternarEscolha(i.driveId)" />
              <span>
                <b>{{ i.nome }}</b>
                <span class="suave">
                  · {{ ROTULO_TIPO_DOCUMENTO[i.tipo] }}
                  <template v-if="i.processoNumero"> · processo {{ i.processoNumero }}</template>
                  <template v-if="i.caminho.length"> · {{ i.caminho.join(' / ') }}</template>
                </span>
              </span>
            </label>
          </li>
        </ul>
        <button v-if="drive.itens.length" type="button" class="botao-primario" :disabled="nSelecionados === 0" @click="associar">
          Associar {{ nSelecionados }} selecionado(s)
        </button>
      </template>
    </div>
    <label v-if="podeEnviar">
      Anexar documento
      <input type="file" accept=".pdf,.docx,.doc,.png,.jpg,.jpeg" :disabled="enviando" @change="enviar" />
    </label>
  </section>
</template>
