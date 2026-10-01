# Arquitetura

A documentação de arquitetura usa diagramas C4 e registros de decisão (ADR) concisos.

### Vistas esperadas

- Context
- Container
- Component, quando útil
- Deployment

Diagramas de integração devem tornar explícitas as fronteiras de orquestração, serviços externos,
Kafka e interações com workers.

### Vistas atuais

| Vista | Documento | Fonte |
| --- | --- | --- |
| C4 L1 Context | [c4/001-context.md](c4/001-context.md) | Execução observada, 2026-09-29 |
| C4 L2 Container | [c4/001-container.md](c4/001-container.md) | Execução observada, 2026-09-29 |

As vistas Component e Deployment ainda não foram escritas. Estão adiadas até uma lesson introduzir
job workers, Kafka e um deployment Kubernetes, momento em que as fronteiras que elas desenhariam
passam a existir de fato.

### Decisão de notação: `flowchart`, não `C4Context`/`C4Container`

Os dois diagramas acima são rotulados como C4 L1 e L2, mas desenhados em sintaxe `flowchart`.

**Motivo.** A sintaxe C4 do Mermaid falha ao renderizar estes grafos neste ambiente:

| Documento | Sintaxe tentada | Erro |
| --- | --- | --- |
| `c4/001-context.md` | `C4Context` | `C4 rel "dev" -> "sec" references an unknown shape` |
| `c4/001-container.md` | `C4Container` | `Parse error on line 22 … Expecting 'RBRACE', got 'EOF'` |

Um diagrama que não renderiza não é evidência de nada, e o defeito é silencioso: o Markdown continua
legível, o texto ao redor está correto, e só quem abre a página percebe. Por isso os diagramas
foram convertidos, e **por isso existe** [validate-mermaid.sh](../validate-mermaid.sh), que extrai e
renderiza todo bloco Mermaid do repositório.

**O que não muda.** O nível arquitetural continua sendo L1 e L2. O diagrama L1 mostra **sistemas** e
não entra nos componentes do cluster; o L2 mostra **containers, componentes lógicos e volumes**. A
notação é `flowchart`; o nível é C4.

**Limite desta decisão.** `flowchart` não é C4. Ela não valida boundary, não valida nível, e não
força a separação de responsabilidades. A conformidade com C4 é mantida **por revisão**, e é o que
[diagram-reviewer](../../.opencode/agents/diagram-reviewer.md) faz — ele verifica a granularidade e
as fronteiras que a notação deixou de exigir.

### Revisão de diagramas

| Ferramenta | O que garante | O que **não** garante |
| --- | --- | --- |
| `docs/validate-mermaid.sh` | Todo bloco Mermaid renderiza | Que o diagrama seja correto |
| `diagram-reviewer` | Cada nó e cada relação existem no ambiente real | — |

Um diagrama pode renderizar perfeitamente e afirmar que o Camunda 8 usa um banco externo. Já houve
dois diagramas quebrados neste repositório por não ter sido renderizado nenhum dos dois.
