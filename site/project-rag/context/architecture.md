# Arquitetura e integrações

## Site institucional

- `index.html`: conteúdo e JavaScript inline da navegação, comentários, triagem e agenda.
- `styles.css`: estilos responsivos do site institucional.
- `assets/`: identidade visual e mármores decorativos.
- `fotos/`: fotografias das sócias.
- `CNAME`: domínio customizado do GitHub Pages.

Fluxo principal: visitante preenche a triagem em três etapas; o navegador monta uma mensagem e abre o WhatsApp. Um marcador em `sessionStorage` libera a seção de agenda e o link público do Google Calendar durante a sessão.

Comentários não são persistidos. O formulário abre um `mailto:` para moderação manual, e comentários aprovados ficam em uma lista JavaScript no próprio `index.html`.

## Área autenticada experimental

- `testeLogin/index.html`, `styles.css`, `app.js`: login e acompanhamento do caso do cliente.
- `testeLogin/admin/index.html`, `styles.css`, `app.js`: login administrativo e CRUD de casos/atualizações.
- `testeLogin/supabase/admin-setup.sql`: perfis, função `is_admin`, grants e políticas RLS administrativas.

O frontend carrega `@supabase/supabase-js@2` por CDN e usa autenticação por e-mail/senha. Clientes consultam o caso mais recente; administradores gerenciam perfis, casos e linha do tempo. O schema versionado é incompleto porque as criações de `cases` e `case_updates` não estão no repositório.

## Dependências externas

- GitHub Pages e DNS do domínio customizado.
- Google Fonts.
- WhatsApp (`api.whatsapp.com`).
- Google Calendar Appointment Scheduling.
- Supabase Auth/Postgres para `testeLogin/`.
- jsDelivr para o SDK Supabase.

Nenhuma integração externa deve ser modificada ou operada por inferência.
