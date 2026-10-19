<script setup lang="ts">
import { onMounted, ref } from 'vue'
import {
  confirmarDoisFatores,
  desativarDoisFatores,
  iniciarDoisFatores,
  statusDoisFatores,
  type CadastroDoisFatores,
} from '../api/relatorios'
import { carregarMeuPerfil, salvarMeuPerfil, trocarMinhaSenha } from '../api/sistema'
import Feedback from '../components/Feedback.vue'
import { useFeedback } from '../composables/useFeedback'
import { useSessao } from '../composables/useSessao'

const { erro, aviso, executar } = useFeedback()
const { eu } = useSessao()

const perfil = ref({ telefone: '', whatsapp: '', cargo: '', emailAlertas: '', alertasEmail: true, assinatura: '' })
const senhas = ref({ atual: '', nova: '', confirmacao: '' })

const ativo = ref<boolean | null>(null)
const cadastro = ref<CadastroDoisFatores | null>(null)
const codigo = ref('')

async function carregar() {
  await executar(async () => {
    ativo.value = (await statusDoisFatores()).ativo
    const u = await carregarMeuPerfil()
    perfil.value = {
      telefone: u.telefone ?? '',
      whatsapp: u.whatsapp ?? '',
      cargo: u.cargo ?? '',
      emailAlertas: u.emailAlertas ?? '',
      alertasEmail: u.alertasEmail,
      assinatura: u.assinatura ?? '',
    }
  }, '', true)
}

async function salvarPerfil() {
  const v = perfil.value
  await executar(
    () =>
      salvarMeuPerfil({
        telefone: v.telefone || null,
        whatsapp: v.whatsapp || null,
        cargo: v.cargo || null,
        emailAlertas: v.emailAlertas || null,
        alertasEmail: v.alertasEmail,
        assinatura: v.assinatura || null,
      }),
    'Dados salvos.',
  )
}

async function trocarSenha() {
  if (senhas.value.nova !== senhas.value.confirmacao) {
    erro.value = 'A confirmação não confere com a nova senha.'
    return
  }
  if (await executar(() => trocarMinhaSenha(senhas.value.atual, senhas.value.nova), 'Senha alterada.')) {
    senhas.value = { atual: '', nova: '', confirmacao: '' }
  }
}

async function iniciar() {
  await executar(async () => {
    cadastro.value = await iniciarDoisFatores()
  })
}

async function confirmar() {
  if (await executar(() => confirmarDoisFatores(codigo.value.trim()), 'Verificação em duas etapas ativada.')) {
    cadastro.value = null
    codigo.value = ''
    await carregar()
  }
}

async function desativar() {
  if (await executar(() => desativarDoisFatores(codigo.value.trim()), 'Verificação em duas etapas desativada.')) {
    codigo.value = ''
    await carregar()
  }
}

onMounted(carregar)
</script>

<template>
  <section>
    <div class="cabecalho-pagina">
      <div>
        <h1>Minha conta</h1>
        <p class="suave">{{ eu?.nome }} · {{ eu?.email }}<template v-if="eu?.mestre"> · acesso mestre</template></p>
      </div>
    </div>
    <Feedback :erro="erro" :aviso="aviso" />

    <form class="formulario" @submit.prevent="salvarPerfil">
      <h2 class="largo">Contato e alertas</h2>
      <label>Telefone <input v-model="perfil.telefone" maxlength="30" autocomplete="tel" /></label>
      <label>WhatsApp <input v-model="perfil.whatsapp" maxlength="30" /></label>
      <label>Cargo / função <input v-model="perfil.cargo" maxlength="80" /></label>
      <label>
        E-mail para alertas (se diferente do login)
        <input v-model="perfil.emailAlertas" type="email" maxlength="254" />
      </label>
      <label class="marcar largo"><input v-model="perfil.alertasEmail" type="checkbox" /> Receber alertas de prazos e audiências por e-mail</label>
      <label class="largo">
        Assinatura (aparece nos relatórios e e-mails)
        <textarea v-model="perfil.assinatura" maxlength="500" rows="3" />
      </label>
      <button type="submit" class="botao-primario">Salvar dados</button>
    </form>

    <form class="formulario" @submit.prevent="trocarSenha">
      <h2 class="largo">Trocar senha</h2>
      <label>Senha atual <input v-model="senhas.atual" type="password" required autocomplete="current-password" /></label>
      <label>Nova senha (mínimo 12 caracteres) <input v-model="senhas.nova" type="password" required minlength="12" autocomplete="new-password" /></label>
      <label>Confirmar nova senha <input v-model="senhas.confirmacao" type="password" required minlength="12" autocomplete="new-password" /></label>
      <button type="submit" class="botao-secundario">Trocar senha</button>
    </form>

    <h2>Verificação em duas etapas</h2>
    <p v-if="ativo === null">Carregando...</p>
    <template v-else-if="ativo">
      <p>A verificação em duas etapas está <b>ativa</b>. O login pede o código do aplicativo autenticador.</p>
      <form class="formulario form-2fa" @submit.prevent="desativar">
        <label>
          Código atual do aplicativo (para desativar)
          <input v-model="codigo" inputmode="numeric" autocomplete="one-time-code" maxlength="6" pattern="[0-9]{6}" required />
        </label>
        <button type="submit" class="botao-secundario">Desativar verificação em duas etapas</button>
      </form>
    </template>

    <template v-else>
      <p>
        A verificação em duas etapas está <b>desativada</b>. Ative para proteger a conta com um código do aplicativo
        autenticador (Google Authenticator, Authy, 1Password...).
      </p>
      <button v-if="!cadastro" type="button" class="botao-primario" @click="iniciar">Ativar</button>
      <template v-else>
        <p>No aplicativo, adicione uma conta manualmente com esta chave e depois informe o código de 6 dígitos:</p>
        <p><code class="segredo">{{ cadastro.segredo }}</code></p>
        <p class="suave">Ou abra o endereço de configuração no aparelho: <code class="segredo">{{ cadastro.uri }}</code></p>
        <form class="formulario form-2fa" @submit.prevent="confirmar">
          <label>
            Código de 6 dígitos
            <input v-model="codigo" inputmode="numeric" autocomplete="one-time-code" maxlength="6" pattern="[0-9]{6}" required />
          </label>
          <button type="submit" class="botao-primario">Confirmar e ativar</button>
        </form>
      </template>
    </template>
  </section>
</template>
