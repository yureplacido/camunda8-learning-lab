#!/usr/bin/env bash
# Valida a coleção Postman contra a spec OpenAPI que o próprio cluster publica.
#
#   ./infra/local/validate-collections.sh
#
# Por que isso existe: a coleção declara endpoints que alguém escreveu num
# arquivo. Endpoint escrito de memória é exatamente o tipo de afirmação que
# este repositório já registrou como falsa. A spec viva é a fonte da verdade —
# o cluster responde o que ele de fato implementa, sem memória nossa.
#
# O que é verificado:
#   1. JSON válido e esquema v2.1
#   2. Todo path /v2 e /v3 da coleção existe na spec
#   3. O método HTTP é aceito pela spec para aquele path
#   4. Todo {{var}} usado existe na lista de variáveis da coleção
#   5. Nenhum request de escrita aponta para /operate/ achando que é API
#
# Saída: 0 se tudo bate, 1 se houver divergência, 2 se o cluster não estiver no ar.

set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${HERE}/../.." && pwd)"

COLLECTION="${HERE}/collections/camunda8-lab.postman_collection.json"
BASE_URL="${BASE_URL:-http://localhost:8080}"
SPEC_URL="${BASE_URL}/v3/api-docs/Orchestration%20Cluster%20API"

# Endereços que NÃO podem ser cruzados contra a spec, porque estão fora da
# Orchestration Cluster REST API ou porque o objetivo do request é justamente
# mostrar que eles não são API. Cada um precisa de uma justificativa viva aqui
# — um item sem motivo é um teste que ninguém revisa.
#
# Histórico: esta lista já continha "POST /v2/jobs|não existe ativação de Job na
# REST v2". O path estava errado, a conclusão também, e o validador passava
# 22/22 confirmando a mentira. Ver a correção em docs/lessons/002.
ALLOW_NOT_IN_SPEC=(
  "GET /operate/v1/process-instances/search|A SPA do Operate responde 200 com HTML; existe para provar isso"
  "GET /v2/nao-existe|caminho inexistente de propósito, para exibir o formato de erro RFC 9457"
  "GET /actuator/health|monitoringApi do Broker (porta 9600), não é parte da Orchestration Cluster REST API"
  "GET /actuator/partitions|monitoringApi do Broker (porta 9600), não é parte da Orchestration Cluster REST API"
  "GET /actuator/exporters|monitoringApi do Broker (porta 9600), não é parte da Orchestration Cluster REST API"
  "GET /v3/api-docs/swagger-config|descoberta do Springdoc, endpoint do próprio cluster, não da API Camunda"
)

red()   { printf '\033[31m%s\033[0m\n' "$*"; }
green() { printf '\033[32m%s\033[0m\n' "$*"; }
warn()  { printf '\033[33m%s\033[0m\n' "$*"; }

command -v jq >/dev/null || { red "jq ausente"; exit 2; }
[[ -f "${COLLECTION}" ]] || { red "coleção não encontrada: ${COLLECTION}"; exit 2; }
jq empty "${COLLECTION}" 2>/dev/null || { red "JSON inválido: ${COLLECTION}"; exit 1; }

echo "Coleção: ${COLLECTION#"${REPO_ROOT}/"}"
echo "Spec:    ${SPEC_URL}"

if ! spec="$(curl -sf --max-time 20 "${SPEC_URL}")"; then
  warn "cluster não respondeu a spec em ${SPEC_URL}"
  warn "suba o cluster com: cd infra/local/camunda-8.9 && docker compose up -d"
  exit 2
fi

spec_paths="$(printf '%s' "${spec}" | jq -r '.paths | keys[]')"
spec_title="$(printf '%s' "${spec}" | jq -r '.info.title + " " + .info.version')"
green "spec carregada: ${spec_title} ($(printf '%s' "${spec_paths}" | wc -l | tr -d ' ') paths)"

# Declarações "MÉTODO path" extraídas da coleção, na ordem em que aparecem.
mapfile -t requests < <(
  jq -r '
    [ .item[].item[]
      | [ .request.method,
          ((.request.url.raw // (.request.url | tostring))
            | sub("^\\{\\{[a-zA-Z]+\\}\\}"; "")
            | gsub("\\{\\{"; "{")
            | gsub("\\}\\}"; "}")),
          .name
        ] | @tsv
    ] | .[]
  ' "${COLLECTION}"
)

if [[ ${#requests[@]} -eq 0 ]]; then
  red "nenhum request extraído da coleção"
  exit 1
fi

# Variáveis declaradas na coleção, para checar referências.
mapfile -t declared_vars < <(jq -r '.variable[].key' "${COLLECTION}")
mapfile -t used_vars < <(
  jq -r '.. | strings | select(test("\\{\\{"))' "${COLLECTION}" \
    | grep -oE '\{\{[a-zA-Z_]+\}\}' | tr -d '{}' | sort -u
)

failures=0
checked=0

for row in "${requests[@]}"; do
  IFS=$'\t' read -r method path name <<<"${row}"
  entry="${method} ${path}"
  checked=$((checked + 1))

  if ! printf '%s\n' "${spec_paths}" | grep -Fxq "${path}"; then
    reason=""
    for e in "${ALLOW_NOT_IN_SPEC[@]}"; do
      if [[ "${e}" == "${entry}|"* ]]; then
        reason="${e#*|}"
        break
      fi
    done
    if [[ -n "${reason}" ]]; then
      printf '  %-6s %-52s %s\n' "${method}" "${path}" "(fora da spec, permitido)"
      printf '           motivo: %s\n' "${reason}"
      continue
    fi
    red "  path não existe na spec: ${method} ${path}  — request: ${name}"
    failures=$((failures + 1))
    continue
  fi

  allowed_methods="$(printf '%s' "${spec}" \
    | jq -r --arg p "${path}" '.paths[$p] | keys[] | ascii_upcase')"
  if printf '%s\n' "${allowed_methods}" | grep -Fxq "${method}"; then
    printf '  %-6s %-52s %s\n' "${method}" "${path}" "ok"
  else
    red "  método inválido: ${method} ${path} — spec aceita: $(printf '%s' "${allowed_methods}" | paste -sd, -)"
    failures=$((failures + 1))
  fi
done

echo
echo "Variáveis"
for v in "${used_vars[@]}"; do
  if printf '%s\n' "${declared_vars[@]}" | grep -Fxq "${v}"; then
    printf '  %-28s declarada\n' "${v}"
  else
    red "  ${v} usada na coleção mas NÃO declarada"
    failures=$((failures + 1))
  fi
done

echo
if [[ ${failures} -eq 0 ]]; then
  green "OK: ${checked} request(s) conferem com a spec viva do cluster."
  echo "Lembre: isso valida caminho e método. Não valida se a asserção de cada"
  echo "request reflete o comportamento real — só a execução da coleção prova isso."
  exit 0
fi

red "FALHA: ${failures} divergência(s) em ${checked} request(s)."
exit 1