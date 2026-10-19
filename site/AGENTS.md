# Instruções para agentes de IA

Este arquivo é a entrada obrigatória para qualquer IA que trabalhe neste repositório.

## Leitura inicial

1. Leia `project-rag/README.md` para selecionar apenas o contexto necessário.
2. Leia `project-rag/memory/current-state.md` antes de assumir o estado do site.
3. Inspecione os arquivos afetados; o checkout atual é a fonte de verdade.
4. Preserve dados pessoais, credenciais e conteúdo jurídico sensível.

## Hierarquia de fontes

1. pedido atual do usuário;
2. código e configuração do checkout atual;
3. `project-rag/memory/current-state.md`;
4. documentos em `project-rag/context/`;
5. registros históricos.

## Segurança e publicação

- Nunca registre senhas, tokens, sessões, dados de clientes ou conteúdo de casos na RAG.
- A chave publishable do Supabase é pública por natureza; a segurança depende das políticas RLS. Nunca adicione `service_role` ao frontend.
- Não altere telefones, e-mails, OABs, domínio, textos jurídicos ou links de agenda sem confirmação do usuário.
- Não execute SQL no Supabase, publique no GitHub Pages ou modifique DNS sem autorização explícita.
- Trate `testeLogin/` como funcionalidade experimental até confirmação do runtime e das tabelas remotas.

## Continuidade

Após mudança relevante, atualize a memória indicada por `project-rag/README.md` e execute:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/validate-project-rag.ps1
```

Quando houver trabalho incompleto, registre o handoff em `project-rag/memory/handoff.md`.
