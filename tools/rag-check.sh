#!/usr/bin/env bash
# Auditoria da RAG do projeto: arquivos obrigatorios, tamanhos, links e segredos.
set -u
cd "$(dirname "$0")/.." || exit 2
fail=0
err() { echo "ERRO: $*"; fail=$((fail+1)); }
for f in CLAUDE.md README.md docs/00-especificacao.md docs/01-arquitetura.md docs/02-modelo-de-dados.md \
  docs/03-permissoes-e-regras.md docs/04-roadmap.md docs/05-pendencias.md docs/06-padroes-engenharia.md \
  docs/rag/index.md docs/rag/invariantes.md docs/rag/decisions.md docs/rag/estado.md docs/rag/licoes.md; do
  [ -f "$f" ] || err "$f ausente"
done
for f in docs/rag/*.md; do
  [ "$(wc -c < "$f")" -le 12000 ] || err "$f passou de 12000 bytes; dividir"
done
for p in $(grep -ohE 'docs/[A-Za-z0-9._/-]+\.md' docs/rag/index.md CLAUDE.md README.md | sort -u); do
  [ -f "$p" ] || err "citado e inexistente: $p"
done
for p in $(grep -oE '`[a-z]+\.md`' docs/rag/index.md | tr -d '`' | sort -u); do
  [ -f "docs/rag/$p" ] || err "docs/rag/$p citado e inexistente"
done
if grep -rEn '(password|senha|token|secret)[[:space:]]*[:=][[:space:]]*[^[:space:]$]{6,}' --include=*.md --include=*.py --include=*.env --include=*.yml --include=*.yaml --include=*.properties --exclude-dir=node_modules --exclude-dir=target --exclude-dir=dist --exclude-dir=.git --exclude-dir=site . 2>/dev/null | grep -v rag-check.sh; then
  err "possivel segredo versionado"
fi
[ "$fail" -eq 0 ] && echo "OK: 0 erros" || { echo "$fail erro(s)"; exit 1; }
