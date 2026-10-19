import { ref } from 'vue'

export type Tema = 'auto' | 'claro' | 'escuro'

const CHAVE = 'tema'
const ORDEM: Tema[] = ['auto', 'claro', 'escuro']

function lerSalvo(): Tema {
  try {
    const v = localStorage.getItem(CHAVE)
    return v === 'claro' || v === 'escuro' ? v : 'auto'
  } catch {
    return 'auto'
  }
}

const tema = ref<Tema>(lerSalvo())

function aplicar(): void {
  const raiz = document.documentElement
  if (tema.value === 'auto') {
    delete raiz.dataset.tema
  } else {
    raiz.dataset.tema = tema.value
  }
}

function alternar(): void {
  tema.value = ORDEM[(ORDEM.indexOf(tema.value) + 1) % ORDEM.length] as Tema
  try {
    if (tema.value === 'auto') {
      localStorage.removeItem(CHAVE)
    } else {
      localStorage.setItem(CHAVE, tema.value)
    }
  } catch {
    // sem armazenamento: o tema vale so nesta visita
  }
  aplicar()
}

export function useTema() {
  return { tema, aplicar, alternar }
}
