# LGPD e Seguranca (estado implementado)

Escopo: o que o sistema ja faz e o que ainda depende de decisao. Fonte funcional: `docs/00-especificacao.md` item 15.

## Dados pessoais tratados
| Categoria | Onde | Acesso |
|---|---|---|
| Cadastro do cliente (nome, CPF/CNPJ, contatos, endereco) | `cliente` | Socias; demais so quem ve processo do cliente |
| Processos e partes | `processo`, `parte_processo` | Matriz de visibilidade (escopo PROCESSO) |
| Publicacoes e intimacoes | `publicacao_bruta`, `publicacao` | Matriz (escopo PUBLICACAO) |
| Atendimentos e andamentos | `atendimento`, `andamento` | Clientes/processos visiveis |
| Relatorios enviados ao cliente (copia em PDF) | `relatorio_cliente` | Socias e advogado que gerou |
| Financeiro | `contrato_honorarios`, `parcela`, `despesa`, `honorario_variavel` | SOMENTE socias, com auditoria de consulta |
| Anexos e modelos | `STORAGE_DIR` | Visibilidade do dono (agendamento/processo) |
| Conexao Google (refresh token cifrado, e-mail da conta) | `conexao_google` | So o proprio usuario ve/desconecta; token nunca sai da API |
| Usuarios, OABs, segredo TOTP | `usuario`, `oab` | Socias gerenciam; segredo so do proprio |

## Controles implementados
- **Login individual** com sessao em cookie HttpOnly, CSRF, senha com BCrypt (custo 12), **limite de tentativas** (5 falhas em 15 min por IP+e-mail bloqueiam 15 min, HTTP 429) e **2FA TOTP opcional** por usuario.
- **Cabecalhos**: CSP restritiva, `X-Frame-Options: DENY`, `Referrer-Policy: no-referrer`, `nosniff`.
- **Visibilidade centralizada** (`Visibilidade`), recurso fora do alcance responde 404 (nao revela existencia).
- **Auditoria** somente de insercao (trigger) de logins, mudancas e consultas ao financeiro; historicos de observacao, conferencia e itens ignorados tambem imutaveis.
- **Relatorio ao cliente** so com campos da lista branca e so apos aprovacao (CHECK no banco).
- **Anexos**: extensao + assinatura do arquivo, 20 MB, nome em disco opaco (UUID), download sempre como binario.
- **Sem dado real em testes/fixtures/logs**: o seed e fictício; mensagens de alerta nao trazem nome de cliente nem texto de publicacao; falha de login nao grava o e-mail.

- **Conexao Google** (docs/13): sem senha do Google; escopo so de leitura do Gmail; refresh token AES-256-GCM com chave fora do banco; desconexao pela propria pessoa revoga no Google; so mensagens que casam com a consulta de intimacoes sao lidas e guardadas.

## Direitos do titular
- `GET /api/lgpd/clientes/{id}/exportar`: cadastro, processos, atendimentos e metadados dos relatorios (so socias, auditado).
- `POST /api/lgpd/clientes/{id}/anonimizar`: remove nome, CPF/CNPJ, contatos, endereco e observacoes, apaga o texto livre dos atendimentos e a parte "cliente" dos processos, e desativa o cadastro. Exige processos desativados. Preserva processos, relatorios enviados e auditoria (retencao legal).

## Pendente (ver `docs/05-pendencias.md`)
CPF/CNPJ e segredo TOTP em repouso sem criptografia (25, 37); 2FA obrigatorio para socias (37); antivirus nos anexos (29); backup do `STORAGE_DIR` e teste de restauracao (30, 40); prazos de retencao (42, 16); politica de privacidade e termo de consentimento do portal (19, 20); trava distribuida e limite de login compartilhado se houver mais de uma instancia (39).
