#!/usr/bin/env bash
# Teste do SMTP: faz login como socia (senha e codigo 2FA digitados, ocultos) e pede o envio do relatorio interno
# AGENDA_DIA as socias (um e-mail com PDF para cada socia ativa). Nao gera dados novos alem da auditoria.
set -euo pipefail
BASE="${BASE:-https://www.crispimcamposadvogados.com.br}"
read -r -p "E-mail de login: " EMAIL
read -r -s -p "Senha: " SENHA; echo
read -r -p "Codigo 2FA (Enter se nao ativou): " CODIGO
EMAIL="$EMAIL" SENHA="$SENHA" CODIGO="$CODIGO" BASE="$BASE" python3 -I - <<'PY'
import os, sys, urllib.request, urllib.parse, urllib.error
from http.cookiejar import CookieJar
jar = CookieJar(); op = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar)); base = os.environ["BASE"]
def req(m, c, form=None):
    h = {"X-XSRF-TOKEN": next((x.value for x in jar if x.name == "XSRF-TOKEN"), "")}
    d = urllib.parse.urlencode(form).encode() if form is not None else (b"" if m == "POST" else None)
    try:
        with op.open(urllib.request.Request(base + c, data=d, method=m, headers=h), timeout=60) as r: return r.status, r.read()
    except urllib.error.HTTPError as e: return e.code, e.read()
req("GET", "/api/csrf")
f = {"email": os.environ["EMAIL"], "senha": os.environ["SENHA"]}
if os.environ["CODIGO"]: f["codigo"] = os.environ["CODIGO"]
if req("POST", "/api/login", f)[0] != 204: sys.exit("Login recusado.")
req("GET", "/api/csrf")
st, b = req("POST", "/api/relatorios/internos/AGENDA_DIA/enviar")
print("HTTP", st, b.decode()[:200]); print("Confira a caixa de entrada (e o spam) das socias.")
PY
