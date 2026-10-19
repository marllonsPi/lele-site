<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import logo from '../assets/logo-white.png'
import { ROTULO_PERFIL } from '../api/sessao'
import { useSessao } from '../composables/useSessao'
import { useTema } from '../composables/useTema'
import { itensPara, porGrupo } from '../router/navegacao'

const { eu, sair } = useSessao()
const { tema, alternar } = useTema()
const roteador = useRouter()
const menuAberto = ref(false)

const grupos = computed(() => (eu.value ? porGrupo(itensPara(eu.value.perfil, eu.value.mestre === true)) : []))
const iniciais = computed(() =>
  (eu.value?.nome ?? '')
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((p) => p[0]!.toUpperCase())
    .join(''),
)
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
    </header>

    <div v-if="menuAberto" class="veu" @click="menuAberto = false" />
    <aside class="lateral" :class="{ aberta: menuAberto }">
      <div class="lateral-marca">
        <img :src="logo" alt="Crispim &amp; Campos Advocacia" />
      </div>
      <nav id="menu-principal" class="menu" aria-label="Principal">
        <section v-for="g in grupos" :key="g.grupo" class="menu-grupo">
          <h2 class="menu-titulo">{{ g.grupo }}</h2>
          <router-link v-for="item in g.itens" :key="item.rota" :to="item.rota" @click="menuAberto = false">
            {{ item.rotulo }}
          </router-link>
        </section>
      </nav>
      <div v-if="eu" class="usuario">
        <span class="avatar" aria-hidden="true">{{ iniciais }}</span>
        <div class="usuario-dados">
          <span class="usuario-nome">{{ eu.nome }} · {{ ROTULO_PERFIL[eu.perfil] }}</span>
          <span v-if="eu.mestre" class="selo selo-mestre">Acesso mestre</span>
        </div>
        <button type="button" class="botao-icone" :title="rotuloTema" :aria-label="rotuloTema" @click="alternar">
          <span aria-hidden="true">{{ tema === 'escuro' ? '☾' : tema === 'claro' ? '☀' : '◐' }}</span>
        </button>
        <button type="button" class="botao-secundario" @click="encerrar">Sair</button>
      </div>
    </aside>

    <main id="conteudo" class="conteudo" tabindex="-1">
      <router-view />
    </main>
  </div>
</template>
