#!/usr/bin/env bash
# Ativa o 2FA (TOTP) da conta que faz login. Roda na maquina da usuaria: senha digitada oculta,
# nada vai para argumentos, historico ou chat. Mostra a URI otpauth NESTE terminal para cadastrar no
# autenticador (copie a URI ou use um gerador de QR local); depois pede o codigo de 6 digitos para confirmar.
# Antes de ativar: confira que existe backup recente e que o autenticador sera guardado com cuidado
# (nao ha codigos de recuperacao; emergencia: deploy/oci/desativar-2fa.sh).
set -euo pipefail
BASE="${BASE:-https://www.crispimcamposadvogados.com.br}"
read -r -p "E-mail de login: " EMAIL
read -r -s -p "Senha: " SENHA; echo
EMAIL="$EMAIL" SENHA="$SENHA" BASE="$BASE" python3 -I - <<'PY'
import os, sys, json, getpass, urllib.request, urllib.parse, urllib.error
from http.cookiejar import CookieJar
base = os.environ["BASE"]
jar = CookieJar()
op = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar))
def xsrf():
    return next((c.value for c in jar if c.name == "XSRF-TOKEN"), "")
def req(metodo, caminho, dados=None, form=None):
    h = {"X-XSRF-TOKEN": xsrf()}
    corpo = None
    if form is not None:
        corpo = urllib.parse.urlencode(form).encode(); h["Content-Type"] = "application/x-www-form-urlencoded"
    elif dados is not None:
        corpo = json.dumps(dados).encode(); h["Content-Type"] = "application/json"
    r = urllib.request.Request(base + caminho, data=corpo, method=metodo, headers=h)
    try:
        with op.open(r, timeout=30) as resp:
            return resp.status, resp.read()
    except urllib.error.HTTPError as e:
        return e.code, e.read()
req("GET", "/api/csrf")
def login(codigo=None):
    f = {"email": os.environ["EMAIL"], "senha": os.environ["SENHA"]}
    if codigo: f["codigo"] = codigo
    return req("POST", "/api/login", form=f)[0]
if login() != 204:
    sys.exit("Login recusado (senha errada, 2FA ja ativo ou bloqueio temporario).")
req("GET", "/api/csrf")  # o login troca o token CSRF
if json.loads(req("GET", "/api/me/2fa")[1]).get("ativo"):
    sys.exit("O 2FA ja esta ativo nesta conta.")
st, corpo = req("POST", "/api/me/2fa/iniciar")
if st != 200: sys.exit(f"Falha ao iniciar o 2FA (HTTP {st}).")
print("\nCadastre no autenticador (URI otpauth; contem o segredo, nao compartilhe):\n")
print(json.loads(corpo)["uri"])
codigo = input("\nCodigo de 6 digitos mostrado no autenticador: ").strip()
st, _ = req("POST", "/api/me/2fa/confirmar", dados={"codigo": codigo})
if st != 204: sys.exit(f"Codigo recusado (HTTP {st}). O 2FA NAO foi ativado; rode de novo.")
req("POST", "/api/logout")
print("2FA ativado. Teste agora: novo login com senha + codigo (peca um codigo novo)...")
codigo2 = input("Novo codigo de 6 digitos: ").strip()
jar.clear(); req("GET", "/api/csrf")
print("Login com 2FA:", "OK" if login(codigo2) == 204 else "FALHOU (use desativar-2fa.sh se necessario)")
PY
unset SENHA
