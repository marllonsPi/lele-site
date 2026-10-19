#!/usr/bin/env bash
# Fase D2: sobe a stack temporaria na VM, testa upload de anexo e geracao de PDF com dados FICTICIOS
# e remove tudo. Roda na maquina da usuaria (ssh). Segredos aleatorios, so da stack temporaria.
set -euo pipefail
CHAVE="${CHAVE:-$HOME/.ssh/crispim_ed25519}"
[ -f "$(dirname "$0")/local.env" ] && . "$(dirname "$0")/local.env"
VM="${VM:?defina VM=usuario@host (ou deploy/oci/local.env)}"
ssh -i "$CHAVE" -o BatchMode=yes -o IdentitiesOnly=yes "$VM" 'bash -s' <<'REMOTO'
set -uo pipefail
cd ~/crispim-campos/deploy
export TMP_DB_PASSWORD=$(openssl rand -hex 16) TMP_SOCIA_SENHA="Tmp-$(openssl rand -hex 10)"
C="docker compose -p crispimtmp -f docker-compose.tmp.yml"
livre() { free -m | awk '/Mem:/{m=$7} /Swap:/{s=$4} END{print m+s}'; }
limpar() { $C down -v >/dev/null 2>&1; docker rm -f crispimtmp-curl >/dev/null 2>&1; echo "Stack temporaria removida."; }
trap limpar EXIT
echo "RAM+swap livres antes: $(livre) MiB"
[ "$(livre)" -ge 300 ] || { echo "ABORTO: memoria insuficiente"; exit 1; }
$C up -d || exit 1
for i in $(seq 1 60); do docker logs crispimtmp-backend-1 2>&1 | grep -q 'Started .*Application' && break; sleep 5; done
docker logs crispimtmp-backend-1 2>&1 | grep -q 'Started .*Application' || { echo "backend temporario nao subiu"; docker logs --tail 20 crispimtmp-backend-1; exit 1; }
echo "Backend temporario no ar. RAM+swap livres: $(livre) MiB"
docker stats --no-stream --format "{{.Name}} {{.MemUsage}}"
# CONTORNO so na stack temporaria: o BootstrapSocia nao publica UsuarioCriado, entao a socia nasce sem regras de visibilidade
docker exec crispimtmp-db-1 psql -q -U gestao -d gestao -c "insert into regra_visibilidade (observador_id, titular_id, escopo) select 1,1,e from unnest(array['PUBLICACAO','AGENDAMENTO','PROCESSO']) e on conflict do nothing"
docker run --rm --name crispimtmp-curl --network crispimtmp_default -e SENHA="$TMP_SOCIA_SENHA" curlimages/curl:8.10.1 sh -c '
set -e
B=http://backend:8080; J=/tmp/j
curl -s -c $J $B/api/csrf -o /dev/null
X() { grep XSRF-TOKEN $J | awk "{print \$7}"; }
code=$(curl -s -b $J -c $J -o /dev/null -w "%{http_code}" -X POST -H "X-XSRF-TOKEN: $(X)" --data-urlencode email=socia@teste.invalid --data-urlencode "senha=$SENHA" $B/api/login)
echo "login: $code"
curl -s -b $J -c $J $B/api/csrf -o /dev/null
post() { curl -s -b $J -c $J -H "X-XSRF-TOKEN: $(X)" -H "Content-Type: application/json" -X POST -d "$2" $B$1; }
ME=$(curl -s -b $J $B/api/me); UID_=$(echo "$ME" | sed "s/.*\"id\":\([0-9]*\).*/\1/")
CL=$(post /api/clientes "{\"nome\":\"Cliente Ficticio\",\"documento\":\"529.982.247-25\"}"); CID=$(echo "$CL" | sed "s/.*\"id\":\([0-9]*\).*/\1/")
PR=$(post /api/processos "{\"numero\":\"0000001-73.2024.8.07.0001\",\"tribunal\":\"TJDFT\",\"clienteId\":$CID,\"advogadoId\":$UID_}"); PID=$(echo "$PR" | sed "s/.*\"id\":\([0-9]*\).*/\1/")
echo "cliente=$CID processo=$PID"
printf "%%PDF-1.4\n1 0 obj<</Type/Catalog>>endobj\ntrailer<</Root 1 0 R>>\n%%%%EOF\n" > /tmp/doc.pdf
curl -s -b $J -H "X-XSRF-TOKEN: $(X)" -F "arquivo=@/tmp/doc.pdf;filename=teste.pdf" -w "\nupload: %{http_code}\n" $B/api/processos/$PID/anexos
AID=$(curl -s -b $J $B/api/processos/$PID/anexos | sed "s/.*\"id\":\([0-9]*\).*/\1/")
curl -s -b $J -o /tmp/baixado.pdf -w "download: %{http_code} %{size_download} bytes\n" $B/api/anexos/$AID/download
head -c 200 /tmp/baixado.pdf; echo
cmp /tmp/doc.pdf /tmp/baixado.pdf && echo "download identico ao enviado"
curl -s -b $J -o /tmp/rel.pdf -w "PDF interno: %{http_code} %{size_download} bytes\n" "$B/api/relatorios/internos/AGENDA_DIA/pdf"
head -c 5 /tmp/rel.pdf; echo
RC=$(post /api/relatorios/clientes "{\"clienteId\":$CID}"); RID=$(echo "$RC" | sed "s/.*\"id\":\([0-9]*\).*/\1/")
curl -s -b $J -o /tmp/rc.pdf -w "PDF do cliente (rascunho): %{http_code} %{size_download} bytes\n" $B/api/relatorios/clientes/$RID/pdf
head -c 300 /tmp/rc.pdf; echo
' || echo "ERRO no teste"
echo "--- log (erros) ---"; docker logs crispimtmp-backend-1 2>&1 | grep -E "ERROR|Exception|Caused by" | head -8 | cut -c1-300
echo "Arquivos no volume temporario:"; docker exec crispimtmp-backend-1 sh -c 'ls -l /data/storage | head'
docker stats --no-stream --format "{{.Name}} {{.MemUsage}}"
echo "RAM+swap livres depois: $(livre) MiB"; docker ps --format "{{.Names}} {{.Status}}" | grep -v tmp
curl -s -o /dev/null -w "producao /actuator/health: %{http_code}\n" http://127.0.0.1:8081/actuator/health
REMOTO
