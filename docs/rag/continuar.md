# Continuar o trabalho (outra maquina ou outra sessao)

Atualizado em 2026-10-21. Complementa `estado.md`; nao repete o que la esta. Sem segredo, host de producao nem dado de cliente: valores reais ficam no cofre da usuaria e no `.env` da VM.

## 1. Subir o ambiente do zero
1. Clonar a RAG particular (`marllonsPi/ai`, privado) e rodar `bash tools/bootstrap.sh --projeto crispim-campos`; o playbook completo e `playbooks/retomar-em-outra-maquina.md` dela. Ler `CLAUDE.md`, `index.md` e `invariantes.md` deste diretorio.
2. Toolchain: JDK 21 (`export JAVA_HOME=...`; o `java` padrao pode ser 1.8), Node 20+ (usado 22) e `npm` com `legacy-peer-deps`. Maven offline (`./mvnw -o`) exige cache `~/.m2`; no primeiro build, com internet.
3. Validar: `cd backend && ./mvnw -o test` (258 testes; Postgres embutido zonky, sem Docker) e `cd frontend && npx vue-tsc --noEmit -p tsconfig.app.json && npx vitest run && npm run build` (117 testes). Falha preexistente: `PostgresRealTest.relatoriosInternosRespeitamPerfilEGeramPdf` (espera 2 itens na agenda de amanha, recebe 3: depende da data).
4. Teste manual com dados ficticios: `docs/11-guia-de-teste.md`.

Endereco da VM: os scripts `deploy/oci/*.sh` exigem `VM=usuario@host` (variavel de ambiente ou arquivo local `deploy/oci/local.env`, ignorado pelo Git; o IP nao e versionado: ver console OCI). Chave: `~/.ssh/crispim_ed25519`.

## 2. Onde esta o que
| Assunto | Onde |
|---|---|
| Producao (VM, Compose, HTTPS, backups, rollback) | `estado.md`, `docs/08`, `docs/09`, `docs/12`, `deploy/oci/*.sh` |
| Implantar | `bash deploy/oci/implantar.sh` (`--so-ler` apenas diagnostica); so a pedido da usuaria, com backup antes |
| Importacao planilha/Drive | `docs/14-importacao.md`, modulo `importacao`, API `/api/importacoes/*`, tela `/importacao` (so acesso mestre) |
| Honorarios | `docs/15-honorarios.md`, modulo `honorarios`, tela `/honorarios` |
| Google (Gmail/Drive) | `docs/13-conexao-google.md`, `deploy/configurar-google.sh` |
| Preferencias de interface | modal para criar/editar, `SeletorOrdem` nas listas, conta mestre sempre oculta (ver `decisions.md`) |

## 3. Proximos passos (por prioridade)
1. Confirmar se V25 (comentarios) e V26 (Drive do escritorio) ja estao na VM (`implantar.sh --so-ler`); se nao, implantar a pedido.
2. Google: a usuaria grava `GOOGLE_*` na VM com `deploy/configurar-google.sh` (a chave secreta anterior foi perdida: gerar nova no projeto `crispim-campos`); reiniciar o backend; cada socia conecta em `/importacao`.
3. SMTP (sem ele os alertas e a moderacao de comentarios ficam pendentes) e ligar captura/alertas agendados.
4. Conferir com as socias: duplicidades de clientes com nome parecido; situacao "Em andamento" atribuida a 11 processos sem prova no documento; clientes sem area (cadastro incompleto); tabela de honorarios OAB/SP (so ha a de DF).
5. Pendencias `docs/05-pendencias.md` itens 17 (destino do remoto Git) e 43 a 56.
6. Tornar o teste `relatoriosInternosRespeitamPerfilEGeramPdf` independente da data.

## 4. Fora do Git (levar por cofre, nunca por Git ou chat)
Chave SSH dedicada da VM; passphrase dos backups (`~/.crispim-backup.pass`, copia na VM; sem ela os `*.enc` nao abrem); `.env` de producao na VM (inclui `GOOGLE_TOKEN_KEY`: perdida, todas as socias reconectam); acesso ao projeto Google Cloud, ao registro.br e a conta OCI. Planilhas `*.xlsx`, JSON extraidos do Drive e relatorios de importacao contem dados reais: ficam fora do repositorio; a fonte e o Drive do escritorio e o proprio sistema.

## 5. Armadilhas
- Importar so pelo modulo do sistema (previa -> decisao -> confirmacao), nunca por script de gravacao paralelo. Os scripts unicos `finalizar-importacao`, `atualizar-cadastros`, `completar-*` ja foram executados pela usuaria e nao sao versionados (continham nomes reais); nao precisam ser refeitos.
- Contratos do Drive reaproveitam CPF/endereco/telefone de outros (modelo copiado): conferir antes de cadastrar.
- Em modo automatico o assistente nao consegue SSH nem ler segredos: a usuaria roda o script (digita senhas no terminal) ou autoriza explicitamente.
- Outra sessao pode ter mudancas nao commitadas na mesma arvore: nao reverter; commit so com `git add <arquivos>`.
- O remoto `origin` e o repositorio **publico** do site: nao enviar nada sem decidir o destino (pendencia 17) e revisar o diff (nada de host/IP, chave ou dado de cliente).
