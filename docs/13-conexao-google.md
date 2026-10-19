# Conexao Google por pessoa (OAuth 2.0) e captura por e-mail

Decisao de 2026-10-19 (pedido da usuaria): cada pessoa conecta a PROPRIA conta Google; o sistema le o Gmail dela para capturar intimacoes. Nunca se guarda senha do Google (o Google bloqueia IMAP por senha).

## Como funciona
- Fluxo authorization code + PKCE (S256). Escopos: `openid email` e `gmail.readonly`. `gmail.send` NAO e pedido: o envio de alertas continua pela conta SMTP unica (docs/12, Fase E).
- API (todas exigem login, CSRF nos POST/DELETE, nenhuma recebe id de usuario: cada pessoa so ve e mexe na propria conexao):
  - `GET /api/google/status` -> `{disponivel, conectado, estado ATIVA|EXPIRADA, email, desde}`
  - `POST /api/google/conectar` -> `{url}`: o frontend redireciona o navegador para ela
  - `GET /api/google/callback` (URI registrada no Google) -> 302 para `/configuracoes?google=ok|negado|erro`
  - `DELETE /api/google` -> revoga no Google e apaga o token local (mesmo que a revogacao falhe)
- Estado do consentimento (`oauth_estado`): aleatorio de 256 bits, guardado so como SHA-256, preso ao usuario da sessao, vale 10 min, uso unico. O verifier PKCE fica cifrado.
- Refresh token em `conexao_google.refresh_token_cifrado`: AES-256-GCM, IV aleatorio, amarrado ao usuario (AAD), chave em `GOOGLE_TOKEN_KEY` (base64 de 32 bytes, so ambiente). O access token vive so em memoria (cache ate expirar). Sem a variavel o recurso fica desligado (`disponivel=false`).
- Auditoria (so insercao): `GOOGLE_CONECTAR`, `GOOGLE_DESCONECTAR`, `GOOGLE_EXPIRADA`; sem e-mail da conta nem conteudo.
- `invalid_grant` do Google marca a conexao `EXPIRADA`; a pessoa ve "reconectar".
- Captura (`GmailFonte`, `Fonte.EMAIL`): na execucao diaria existente, para cada pessoa conectada, busca as mensagens que casam com `GOOGLE_GMAIL_QUERY` (padrao `from:jus.br`) no periodo, ate 200 por passada. O titular e quem conectou (invariante 1); idempotente pelo id da mensagem (4); o que o filtro ignora vai para `item_ignorado` (3). Numero CNJ: primeiro com digito verificador valido; sem ele, a publicacao entra por hash. Texto limitado a 20 mil caracteres; so mensagens da consulta sao lidas/guardadas. O formato real dos e-mails push dos tribunais ainda precisa ser verificado (pendencia 12): ajustar a consulta e o parser com e-mails reais, sem copiar dado real para testes.

## O que a usuaria faz no Google Cloud
1. https://console.cloud.google.com -> novo projeto (ex.: "Crispim Campos Gestao").
2. APIs e servicos -> Biblioteca -> ativar **Gmail API**.
3. Tela de consentimento OAuth: tipo **Externo** (ou **Interno** se o escritorio usa Google Workspace: dispensa verificacao e o token nao vence em 7 dias), status **Em teste**. Nome do app, e-mail de suporte. Escopos: `openid`, `email`, `.../auth/gmail.readonly`. **Usuarios de teste**: o Gmail de cada pessoa (ate 100).
4. Credenciais -> Criar credenciais -> ID do cliente OAuth -> **Aplicativo da Web**. URIs de redirecionamento autorizadas: `https://www.crispimcamposadvogados.com.br/api/google/callback` (e `http://localhost:8080/api/google/callback` so para teste local).
5. Na VM (`deploy/.env`, permissao 600; nunca no Git nem no chat): `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `GOOGLE_REDIRECT_URI` (a URI acima), `GOOGLE_TOKEN_KEY` (gerar com `openssl rand -base64 32`; guardar copia no cofre/backup: perdida a chave, todos reconectam). O compose precisa repassar essas variaveis ao backend (tarefa do deploy).

## Riscos e limites
- `gmail.readonly` e escopo **restrito**. Em modo de teste funciona sem verificacao, mas: cada pessoa ve o aviso "app nao verificado" e, para contas @gmail.com, o **refresh token expira em 7 dias** (reconexao semanal; o sistema avisa pelo estado `EXPIRADA`). Verificacao do Google (inclui avaliacao de seguranca paga) so e exigida se o app for **publico**; para uso interno nao se pretende publicar. Alternativas: Workspace + app Interno (sem o limite), ou publicar "em producao" sem verificar (limite de 100 usuarios e aviso permanente; tokens deixam de vencer em 7 dias). Escolha da usuaria.
- E-mail tem sigilo de terceiros (clientes, contrapartes): ler so o que casa com a consulta de intimacoes; revisar a consulta antes de ligar.
- Vazamento do banco nao expoe tokens sem a chave; vazamento da chave + banco expoe leitura do Gmail: a chave fica so no `.env` da VM e no cofre.
- Rotacao da chave: ainda nao automatizada (`versao_chave` ja gravada); trocar a chave exige todos reconectarem.
- A revogacao pela pessoa tambem pode ser feita em myaccount.google.com/permissions.
