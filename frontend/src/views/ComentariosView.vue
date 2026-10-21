<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import {
  aprovarComentario,
  listarComentarios,
  rejeitarComentario,
  ROTULO_ESTADO_COMENTARIO,
  type ComentarioSite,
  type EstadoComentario,
} from '../api/comentarios'
import Feedback from '../components/Feedback.vue'
import SeletorOrdem from '../components/SeletorOrdem.vue'
import { useFeedback } from '../composables/useFeedback'
import { useOrdenacao } from '../composables/useOrdenacao'
import { formatarDataHora } from '../utils/datas'

const { erro, aviso, executar } = useFeedback()

const comentarios = ref<ComentarioSite[]>([])
const carregando = ref(true)
const filtro = ref<EstadoComentario | ''>('PENDENTE')

const ordem = useOrdenacao<ComentarioSite>('comentarios', [
  { chave: 'recentes', rotulo: 'Mais recente', valor: (c) => -Date.parse(c.criadoEm) },
  { chave: 'nome', rotulo: 'Nome', valor: (c) => c.nome },
  { chave: 'situacao', rotulo: 'Situação', valor: (c) => ROTULO_ESTADO_COMENTARIO[c.estado] },
], [{ chave: 'situacao', rotulo: 'Situação', valor: (c) => ROTULO_ESTADO_COMENTARIO[c.estado] }])
const gruposLista = computed(() => ordem.grupos(comentarios.value))
const pendentes = computed(() => comentarios.value.filter((c) => c.estado === 'PENDENTE').length)

async function recarregar() {
  await executar(async () => {
    comentarios.value = await listarComentarios(filtro.value || undefined)
  }, '', true)
  carregando.value = false
}

async function decidir(c: ComentarioSite, aprovar: boolean) {
  const ok = await executar(
    () => (aprovar ? aprovarComentario(c.id) : rejeitarComentario(c.id)),
    aprovar ? 'Comentário aprovado: já aparece no site.' : 'Comentário rejeitado: não aparece no site.',
  )
  if (ok) {
    await recarregar()
  }
}

onMounted(recarregar)
</script>

<template>
  <section>
    <h1>Comentários do site</h1>
    <p class="suave">
      Quem comenta no site aparece aqui e as sócias recebem um e-mail. Aprovado (aqui ou pelo link do e-mail), o comentário entra no site
      automaticamente; a primeira decisão vale, mas qualquer sócia pode mudar depois.
    </p>
    <div class="barra-ferramentas">
      <label>
        Mostrar
        <select v-model="filtro" @change="recarregar">
          <option value="PENDENTE">Aguardando decisão</option>
          <option value="APROVADO">Publicados no site</option>
          <option value="REJEITADO">Rejeitados</option>
          <option value="">Todos</option>
        </select>
      </label>
    </div>
    <SeletorOrdem :ordem="ordem" :total="comentarios.length" />
    <Feedback :erro="erro" :aviso="aviso" />
    <p v-if="carregando">Carregando...</p>
    <p v-else-if="comentarios.length === 0" class="suave">Nenhum comentário {{ filtro === 'PENDENTE' ? 'aguardando decisão' : 'nesta lista' }}.</p>
    <p v-if="filtro === '' && pendentes > 0" class="alerta-incompleto" role="status">{{ pendentes }} comentário(s) aguardando decisão.</p>

    <template v-for="g in gruposLista" :key="g.titulo">
      <h2 v-if="g.titulo" class="grupo-titulo">{{ g.titulo }} <span class="suave">({{ g.itens.length }})</span></h2>
      <article v-for="c in g.itens" :key="c.id" class="cartao">
        <header class="cartao-topo">
          <b>{{ c.nome }}</b>
          <span class="selo" :class="{ 'selo-pendente': c.estado === 'PENDENTE' }">{{ ROTULO_ESTADO_COMENTARIO[c.estado] }}</span>
          <span class="cartao-quando">{{ formatarDataHora(c.criadoEm) }}</span>
        </header>
        <p class="texto-livre">{{ c.mensagem }}</p>
        <p class="suave">
          {{ c.email }}
          <template v-if="c.estado === 'PENDENTE' && !c.avisoEnviado"> · e-mail às sócias ainda não enviado (SMTP não configurado)</template>
          <template v-if="c.decisaoVia"> · decidido {{ c.decisaoVia === 'EMAIL' ? 'pelo link do e-mail' : 'no sistema' }}</template>
        </p>
        <div class="cartao-acoes">
          <button v-if="c.estado !== 'APROVADO'" type="button" class="botao-primario" @click="decidir(c, true)">Aprovar e publicar</button>
          <button v-if="c.estado !== 'REJEITADO'" type="button" class="botao-secundario" @click="decidir(c, false)">
            {{ c.estado === 'APROVADO' ? 'Tirar do site' : 'Rejeitar' }}
          </button>
        </div>
      </article>
    </template>
  </section>
</template>
