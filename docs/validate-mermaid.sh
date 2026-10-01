#!/usr/bin/env bash
#
# docs/validate-mermaid.sh — renderiza todos os blocos Mermaid do repositório.
#
# POR QUE ISTO EXISTE
# -------------------
# Um diagrama que não renderiza não é evidência de nada, e o sintoma é silencioso: o Markdown
# continua legível no editor, o texto está correto, e só quem abre a página percebe que a imagem
# não existe. No histórico deste repositório, dois diagramas C4 ficaram quebrados por exatamente
# esse motivo — o texto ao redor era bom, e ninguém renderizou.
#
# O QUE ISTO FAZ, E O QUE NÃO FAZ
# -------------------------------
# FAZ:  extrai todo bloco ```mermaid e renderiza com mmdc. Falha o build se algum não renderizar.
# NÃO FAZ: não valida se o diagrama está arquiteturalmente correto. Um diagrama pode renderizar
#       perfeitamente e mentir. Para isso existe .opencode/agents/diagram-reviewer.md, que faz
#       revisão semântica contra o ambiente real.
#
# Renderizar é condição necessária, não suficiente.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

# Puppeteer (usado pelo mmdc) precisa destas flags em ambientes com containers/CI onde o
# Chromium roda como root sem user namespaces.
PUPPETEER_CONFIG="$(mktemp -t puppeteer-XXXXXX.json)"
cat >"$PUPPETEER_CONFIG" <<'JSON'
{
  "args": ["--no-sandbox", "--disable-setuid-sandbox", "--disable-dev-shm-usage"]
}
JSON
trap 'rm -f "$PUPPETEER_CONFIG"' EXIT

OUT_DIR="$(mktemp -d -t mermaid-XXXXXX)"
trap 'rm -rf "$OUT_DIR" "$PUPPETEER_CONFIG"' EXIT

# O 'mmdc' é o mermaid-cli (Node). Ele NÃO está em [tools] do .mise.toml, porque
# o pin de toolchain do repositório é só para o runtime JVM, e o mermaid-cli é
# ferramenta de validação, não dependência de execução. Instale-o no ambiente
# do próprio projeto, nunca globalmente — a política está em AGENTS.md.
#
#   npm install --no-save --prefix .opencode @mermaid-js/mermaid-cli
#   MMDC="$(pwd)/.opencode/node_modules/.bin/mmdc"
#
# Se mmdc não estiver no PATH, use o binário local acima exportando MMDC.
MMDC_BIN="${MMDC:-$(
  command -v mmdc 2>/dev/null ||
  echo "${REPO_ROOT}/.opencode/node_modules/.bin/mmdc"
)}"

if [ ! -x "$MMDC_BIN" ]; then
  cat >&2 <<'EOF'
ERRO: 'mmdc' (mermaid-cli) não encontrado.

  Prefira a instalação local ao projeto, e NUNCA global:
      npm install --no-save --prefix .opencode @mermaid-js/mermaid-cli
      ./docs/validate-mermaid.sh

  Ou aponte para um binário existente:
      MMDC=/caminho/para/mmdc ./docs/validate-mermaid.sh
EOF
  exit 127
fi

# Diretórios varridos. 'docs' e 'specs' cobrem a documentação; '.' cobre a raiz e .opencode.
mapfile -t FILES < <(
  grep -rl --include='*.md' '```mermaid' docs specs .opencode AGENTS.md README.md 2>/dev/null | sort
)

if [ "${#FILES[@]}" -eq 0 ]; then
  echo "Nenhum bloco Mermaid encontrado. Nada a validar."
  exit 0
fi

total=0
failed=0
declare -a FAILURES=()

for file in "${FILES[@]}"; do
  # Um arquivo por bloco, na ordem em que aparecem.
  count=$(grep -c '```mermaid' "$file" || true)
  for i in $(seq 1 "$count"); do
    total=$((total + 1))
    safe_name="$(echo "$file" | tr '/.' '__')_$i"
    block="$OUT_DIR/$safe_name.mmd"
    png="$OUT_DIR/$safe_name.png"

    # Extrai o n-ésimo bloco ```mermaid ... ``` (não-guloso, com âncora no início da linha).
    awk -v want="$i" '
      /^```mermaid[[:space:]]*$/ { n++; inside = (n == want); next }
      /^```[[:space:]]*$/ && inside { exit }
      inside { print }
    ' "$file" >"$block"

    if [ ! -s "$block" ]; then
      printf '  FALHA  %s (bloco %d) — bloco vazio\n' "$file" "$i"
      failed=$((failed + 1))
      FAILURES+=("$file (bloco $i): bloco vazio")
      continue
    fi

    if err="$(mmdc -i "$block" -o "$png" -p "$PUPPETEER_CONFIG" 2>&1)"; then
      printf '  ok     %s (bloco %d)\n' "$file" "$i"
    else
      printf '  FALHA  %s (bloco %d)\n' "$file" "$i"
      printf '%s\n' "$err" | head -8 | sed 's/^/           /'
      failed=$((failed + 1))
      FAILURES+=("$file (bloco $i): $(printf '%s' "$err" | head -1)")
    fi
  done
done

echo
echo "Mermaid: $total bloco(s) em ${#FILES[@]} arquivo(s), $failed falha(s)."

if [ "$failed" -ne 0 ]; then
  echo
  echo "Diagrama que não renderiza não pode ficar no repositório:"
  printf '  - %s\n' "${FAILURES[@]}"
  echo
  echo "Se o erro for do Chromium (sandbox), verifique se PUPPETEER_CONFIG está sendo aplicado."
  echo "Se o erro for de sintaxe Mermaid, o bloco está no arquivo indicado acima."
  exit 1
fi

echo "Todos os diagramas renderizam. Lembre: isso valida sintaxe, não correção arquitetural."
