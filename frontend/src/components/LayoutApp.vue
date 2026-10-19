<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ROTULO_PERFIL } from '../api/sessao'
import { useSessao } from '../composables/useSessao'
import { useTema } from '../composables/useTema'
import { itensPara } from '../router/navegacao'

const { eu, sair } = useSessao()
const { tema, alternar } = useTema()
const roteador = useRouter()
const menuAberto = ref(false)

const itens = computed(() => (eu.value ? itensPara(eu.value.perfil) : []))
const rotuloTema = computed(() => ({ auto: 'Tema: automático', claro: 'Tema: claro', escuro: 'Tema: escuro' })[tema.value])

async function encerrar() {
  await sair()
  await roteador.replace({ name: 'login' })
}
</script>

<template>
  <div class="layout">
    <header class="topo">
      <button
        type="button"
        class="botao-icone menu-toggle"
        :aria-expanded="menuAberto"
        aria-controls="menu-principal"
        @click="menuAberto = !menuAberto"
      >
        <span class="sr-only">Menu</span>
        <span aria-hidden="true">☰</span>
      </button>
      <span class="marca">Crispim &amp; Campos</span>
      <nav id="menu-principal" class="menu" :class="{ aberto: menuAberto }" aria-label="Principal">
        <router-link v-for="item in itens" :key="item.rota" :to="item.rota" @click="menuAberto = false">
          {{ item.rotulo }}
        </router-link>
      </nav>
      <div class="usuario" v-if="eu">
        <span class="usuario-nome">{{ eu.nome }} · {{ ROTULO_PERFIL[eu.perfil] }}</span>
        <button type="button" class="botao-icone" :title="rotuloTema" :aria-label="rotuloTema" @click="alternar">
          <span aria-hidden="true">{{ tema === 'escuro' ? '☾' : tema === 'claro' ? '☀' : '◐' }}</span>
        </button>
        <button type="button" class="botao-secundario" @click="encerrar">Sair</button>
      </div>
    </header>
    <main id="conteudo" class="conteudo" tabindex="-1">
      <router-view />
    </main>
  </div>
</template>
