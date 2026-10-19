# Estado atual

- Verificado em: 2026-08-11, America/Sao_Paulo.
- Projeto: site da Crispim & Campos Advogados, apelidado localmente de `lele-site`.
- Branch observada: `main`, rastreando `origin/main`, sem alterações antes da criação desta RAG.
- Publicação indicada pelo checkout: GitHub Pages com domínio `www.crispimcamposadvogados.com.br` via `CNAME`.
- Runtime remoto, DNS, Supabase e agenda Google não foram verificados nesta rodada.

## Entregue no checkout

- Landing page responsiva em HTML/CSS estáticos.
- Conteúdo institucional, áreas de atuação, equipe, FAQ, contato e comentários moderados manualmente.
- Triagem em três etapas montada no navegador e encaminhada ao WhatsApp.
- Agenda Google liberada na sessão do navegador após conclusão da triagem.
- Protótipo `testeLogin/` com autenticação Supabase, painel do cliente e painel administrativo para casos e atualizações.
- SQL administrativo com perfil, função de administrador e políticas RLS.
- Seção de equipe com Letícia Crispim Mello, Lorraynny Campos e Andressa Morais Magalhães; fotos locais identificadas e vinculadas aos respectivos perfis.

## Limites e riscos conhecidos

- Não há pipeline de testes do frontend nem dependências gerenciadas no checkout.
- O formulário de comentários abre o cliente de e-mail; não existe backend de moderação.
- A triagem contém informações potencialmente sensíveis e as envia pelo WhatsApp; não persiste esses dados no site.
- `testeLogin/supabase/admin-setup.sql` referencia `public.cases` e `public.case_updates`, mas não cria essas tabelas. A reconstrução do banco não está completa no repositório.
- O SQL contém IDs e e-mails de contas de teste. Não acrescentar dados reais e confirmar antes de manter ou promover o protótipo.
- A chave Supabase no JavaScript é publishable. Nunca substituir por chave `service_role`; revisar RLS antes de qualquer lançamento da área autenticada.
- O link de agenda, contatos, OABs e textos jurídicos são dados de negócio e exigem confirmação antes de alteração.

## Próxima etapa recomendada

Confirmar com o usuário o objetivo da atualização visual/funcional e se `testeLogin/` deve virar produto. Antes de promover a área autenticada, versionar o schema completo, testar RLS com cliente/admin e validar o runtime real.
