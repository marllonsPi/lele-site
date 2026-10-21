<script setup lang="ts">
import { nextTick, onBeforeUnmount, ref, watch } from 'vue'

const props = withDefaults(
  defineProps<{
    aberto: boolean
    titulo: string
    /** id do <form> do corpo: liga o botao de salvar do rodape ao envio do formulario. */
    formId?: string
    salvarRotulo?: string
    larg?: 'md' | 'lg'
    erro?: string
    /** Sem rodape (conteudo com botoes proprios, como abas). */
    semRodape?: boolean
  }>(),
  { salvarRotulo: 'Salvar', larg: 'md' },
)
const emit = defineEmits<{ (e: 'fechar'): void }>()

const caixa = ref<HTMLElement | null>(null)
let anterior: HTMLElement | null = null
const FOCAVEIS = 'a[href],button:not([disabled]),input:not([disabled]):not([type=hidden]),select:not([disabled]),textarea:not([disabled]),[tabindex]:not([tabindex="-1"])'

function travarRolagem(travar: boolean) {
  document.body.style.overflow = travar ? 'hidden' : ''
}

watch(
  () => props.aberto,
  async (aberto) => {
    travarRolagem(aberto)
    if (aberto) {
      anterior = document.activeElement as HTMLElement | null
      await nextTick()
      const primeiro = caixa.value?.querySelector<HTMLElement>('.modal-corpo input:not([disabled]):not([type=hidden]),.modal-corpo select,.modal-corpo textarea')
      ;(primeiro ?? caixa.value)?.focus()
    } else {
      anterior?.focus?.()
      anterior = null
    }
  },
  { immediate: true },
)

onBeforeUnmount(() => travarRolagem(false))

function aoTeclar(e: KeyboardEvent) {
  if (e.key === 'Escape') {
    e.stopPropagation()
    emit('fechar')
    return
  }
  if (e.key !== 'Tab' || !caixa.value) {
    return
  }
  const itens = [...caixa.value.querySelectorAll<HTMLElement>(FOCAVEIS)].filter((x) => x.offsetParent !== null || x === document.activeElement)
  if (itens.length === 0) {
    return
  }
  const primeiro = itens[0]!
  const ultimo = itens[itens.length - 1]!
  if (e.shiftKey && (document.activeElement === primeiro || document.activeElement === caixa.value)) {
    e.preventDefault()
    ultimo.focus()
  } else if (!e.shiftKey && document.activeElement === ultimo) {
    e.preventDefault()
    primeiro.focus()
  }
}
</script>

<template>
  <Teleport to="body">
    <div v-if="aberto" class="modal-fundo" @mousedown.self="emit('fechar')">
      <div
        ref="caixa"
        class="modal"
        :class="'modal-' + larg"
        role="dialog"
        aria-modal="true"
        :aria-label="titulo"
        tabindex="-1"
        @keydown="aoTeclar"
      >
        <header class="modal-topo">
          <h2>{{ titulo }}</h2>
          <button type="button" class="modal-fechar" aria-label="Fechar" @click="emit('fechar')">×</button>
        </header>
        <div class="modal-corpo">
          <p v-if="erro" class="erro" role="alert">{{ erro }}</p>
          <slot />
        </div>
        <footer v-if="!semRodape" class="modal-rodape">
          <button type="button" class="botao-secundario" @click="emit('fechar')">Cancelar</button>
          <button type="submit" class="botao-primario" :form="formId">{{ salvarRotulo }}</button>
        </footer>
      </div>
    </div>
  </Teleport>
</template>
