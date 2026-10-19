# Arquitetura

Status: PROPOSTA (2026-10-19). Itens marcados `Ponto a confirmar` dependem de decisao da usuaria ou de verificacao em fonte oficial. Nenhuma linha de codigo existe ainda.

## 1. Principios
1. Regra de negocio no servidor, em um unico lugar (camada de dominio), nunca na tela.
2. Visibilidade e permissao centralizadas: toda consulta passa por um filtro unico (item 3). Nenhum controller ou repositorio le publicacao/agendamento/financeiro fora do servico de `acesso` (ArchUnit falha o build se isso ocorrer).
3. Tudo auditavel: quem, o que, quando, valor anterior e novo.
4. Captura idempotente: rodar duas vezes nunca duplica nem perde item.
5. Nada e apagado de verdade em dados juridicos: desativar/arquivar (soft delete).
6. Configuravel por dados, nao por codigo: usuarios, OABs, visibilidade, feriados, tipos, filtros de captura.

## 2. Stack (ADR-0001, revisada em 2026-10-19 por preferencia da usuaria: Node, Python ou Java; Java escolhido)
| Camada | Escolha | Motivo |
|---|---|---|
| Linguagem/runtime | Java 21 (LTS) | Preferencia da usuaria; tipagem forte, ecossistema maduro para auth, auditoria, agendamento e PDF |
| Backend | Spring Boot 4.1.x (4.1.1), Maven, monolito modular (Spring Modulith + ArchUnit) | Um deploy simples, fronteiras de modulo verificadas por teste; migravel a servicos so se necessario |
| API | REST JSON + OpenAPI (springdoc), erros padronizados (RFC 9457) | Contrato documentado e testavel |
| Seguranca | Spring Security: sessao com cookie seguro + CSRF, senha com Argon2/BCrypt, TOTP 2FA, `@PreAuthorize` sobre a politica de `acesso` | Login individual e LGPD |
| Persistencia | PostgreSQL 16, Spring Data JPA/Hibernate, Flyway (migracoes versionadas), Hibernate Envers + `LogAuditoria` | Transacoes, constraints, historico e auditoria |
| Agendamento | db-scheduler (ou Quartz) com bloqueio em banco (ShedLock) | Captura diaria, alertas e backup, sem Redis; seguro com mais de uma instancia |
| E-mail | Spring Mail (SMTP/transacional); leitura de caixas via Jakarta Mail (IMAP/OAuth) | Alertas e captura item 3c |
| PDF | OpenHTMLtoPDF (HTML+CSS com timbre) | Relatorios internos e ao cliente |
| Arquivos | Storage local protegido ou S3 compativel, criptografado | Pecas e documentos |
| Frontend | Vue 3 + TypeScript + Vite, PWA responsivo (mobile-first), biblioteca de componentes (Vuetify ou PrimeVue, a definir) | Celular e computador; alinhado ao Vue 3 ja usado nos demais projetos da usuaria |
| Testes | JUnit 5 + Testcontainers (Postgres real) + ArchUnit; Vitest e Playwright no frontend; matriz de permissao obrigatoria | Visibilidade e o maior risco |
| Entrega | Docker Compose (app, frontend estatico via proxy, postgres) em VPS no Brasil; CI com build, testes, SAST e imagem | Reprodutivel. `Ponto a confirmar`: hospedagem |
| Observabilidade | Actuator, logs estruturados sem dado pessoal, metricas e alerta de falha de captura/backup | Operacao confiavel |

Alternativas avaliadas: NestJS (TypeScript) e Django (Python) atendem, mas exigiriam montar mais pecas de auditoria/agendamento/seguranca ou oferecem menos garantia de tipos entre camadas. Troca de stack so por decisao da usuaria registrada em `docs/rag/decisions.md`.

Fatos do ambiente local (2026-10-19): `java` padrao e o JDK 1.8; o JDK 21 (openjdk-21) ja esta instalado e deve ser usado com `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64`. Maven 3.9.9 (o projeto usa `./mvnw`), Node 20.20.2/npm 10.8.2 (CI e Docker usam Node 22), Git presente, sem Docker nem psql (testes com Testcontainers ficam ignorados localmente e rodam no CI).

Regra do kernel para frontend (sem `?.`, sem recurso moderno nao suportado pela versao do projeto) aplicada por padrao neste projeto; `Ponto a confirmar` se a usuaria quer isso aqui.

## 3. Modulos (pacotes Spring Modulith, um por funcionalidade)
| App | Responsabilidade | Itens da spec |
|---|---|---|
| `contas` | Usuario, perfil, OAB (N por advogado), vinculo estagiario->supervisor, ativar/desativar | 1 |
| `acesso` | Politica de visibilidade (regras em tabela), filtros de consulta (Specification), checagem de acoes | 2, 8 |
| `cadastros` | Cliente, Processo, Partes | 4 |
| `agenda` | Agendamento (tipos), prazos, conferencia, conflitos, agenda individual, observacoes, anexos | 5, 6, 7, 8, 10 |
| `calendario` | Feriados nacionais/locais, suspensoes de tribunal, calculo de dia util | 6 |
| `captura` | Conectores DJEN/Domicilio/e-mail, cruzamento, fila "A classificar", log de ignorados | 3 |
| `alertas` | Regras de aviso, envio e-mail/WhatsApp, resumo diario | 9 |
| `painel` | Painel das socias | 11 |
| `financeiro` | Contrato, parcelas, custas, exito, sucumbencia | 12 |
| `atendimento` | Registro de contatos, pendentes de repasse | 13 |
| `relatorios` | Internos e relatorio ao cliente (rascunho, aprovacao, PDF, envio) | 14 |
| `modelos` | Biblioteca de pecas por tipo e area | 10 |
| `auditoria` | Log imutavel de acoes | 15 |

Dependencias: `acesso` e `auditoria` sao base; nenhum app contorna `acesso`.

## 4. Visibilidade (item 2) como dado
Tabela `RegraVisibilidade(observador, titular, escopo)` onde `titular` e o usuario em cujo nome a publicacao/agendamento esta e `observador` quem pode ver. Seed inicial reproduz a spec (ver `03-permissoes-e-regras.md`). Socias editam a tabela por tela. Um servico `Visibilidade.visiveisPara(usuario)` (Specification JPA) aplica a regra em publicacoes, agendamentos, processos e relatorios. Estagiario: regra especial (apenas itens atribuidos a ele). Financeiro nao usa essa tabela: e restrito ao perfil Socia.

## 5. Captura diaria (item 3)
Pipeline por execucao agendada:
1. Para cada OAB ativa (tabela `contas.OAB`), buscar no DJEN; buscar no Domicilio Judicial; ler a caixa de e-mail de cada advogado com conta cadastrada.
2. Normalizar para `PublicacaoBruta(fonte, id_externo, numero_processo, conteudo, data, oab/advogado)`; guardar o bruto (hash de conteudo) para reprocessar.
3. Aplicar filtros (ex.: Leticia, e-mail de TRT exceto TRT-10) -> item ignorado vai para `ItemIgnorado` com motivo e regra, nunca some.
4. Cruzar por numero de processo normalizado (CNJ, 20 digitos) + data/hash: agrupar em `Publicacao` unica com `fontes_presentes`; sinalizar "so em uma fonte".
5. Vincular a `Processo` existente ou sugerir cadastro; titular = advogado da OAB encontrada.
6. Entrar na fila "A classificar" (socia define tipo, responsavel, prazos).
Cada conector e um adaptador isolado com contrato unico, para trocar/ajustar sem tocar no resto. Falha de uma fonte nao derruba as outras; erro e registrado e alertado.

Pontos a confirmar (verificar em fonte oficial antes de codar, nao assumir): forma de acesso programatico ao DJEN (API publica e parametros), ao Domicilio Judicial Eletronico (autenticacao, certificado/gov.br, existencia de API para escritorios), e-mail por IMAP/OAuth de cada advogado e formato dos pushes dos tribunais.

## 6. Prazos e calendario (item 6)
`calendario` guarda feriados (escopo: nacional, estadual, municipal, tribunal) e suspensoes com intervalo de datas. Funcao unica `subtrair_dias_uteis(data_fatal, 3, contexto_tribunal)`; prazo interno = esse resultado. Dia util = nao sabado/domingo, nao feriado aplicavel, nao suspensao do tribunal do processo. Testes com casos de borda (feriado na semana, suspensao, virada de ano). Dupla conferencia: constraint `conferido_por <> lancado_por` no dominio e no banco (CHECK).

## 7. Alertas (item 9)
Agendador diario gera `Notificacao` (idempotente por agendamento+tipo+data) e despacha por e-mail (SMTP/transacional) e WhatsApp (`Ponto a confirmar`: provedor, ex. API oficial da Meta ou intermediario; exige modelos de mensagem aprovados). Copia as socias. Resumo diario de vencidos nao finalizados.

## 8. Relatorio ao cliente (item 14.2)
Gerador monta dados a partir de uma lista branca de campos "publicos" (numero, vara, partes, tipo, situacao, movimentacoes, pecas protocoladas, audiencias, proximos passos, financeiro opcional). Observacoes internas, prazos internos, conferencias e nomes de executores nao existem no modelo de dados do relatorio, para nao vazarem por engano. Estados: rascunho -> em revisao -> aprovado -> enviado; PDF so e gerado/enviado apos "aprovado"; guarda copia, aprovador, data, e marca andamentos como repassados.

## 9. Seguranca e LGPD (item 15)
- Login individual, senha forte, 2FA recomendado (obrigatorio para socias) `Ponto a confirmar`.
- HTTPS, cookies seguros, protecao CSRF, limite de tentativas.
- Dados pessoais (CPF/CNPJ, contatos) com campo criptografado quando viavel; minimo necessario; trilha de acesso a dados sensiveis.
- Segredos (tokens de e-mail, APIs) em variaveis de ambiente/cofre, nunca no codigo nem na RAG.
- Backup diario criptografado, copia fora do servidor, teste de restauracao periodico.
- Politica de retencao e atendimento de direitos do titular (exportar/anonimizar) documentada.
- Sigilo profissional: acesso a processo apenas conforme visibilidade; log de consultas.

## 10. Ambientes
Local (dev, Postgres via Testcontainers/Compose), homologacao (dados ficticios, nunca dados reais de clientes), producao. Migracoes versionadas e reversiveis; deploy sem perda de dados.
