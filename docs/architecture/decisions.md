# Decisões de Arquitetura

| ADR | Decisão | Status |
| --- | --- | --- |
| ADR-0001 | Princípios do laboratório | Aceito |
| ADR-0002 | Pin de versão do Camunda 8 (8.9.x) | Aceito |

## Decisões de notação

Estas não são ADRs formais, mas vinculam o mesmo tipo de decisão de arquitetura, e ficam
registradas aqui para que não se percam.

| # | Decisão | Status | Onde está |
| --- | --- | --- | --- |
| N-0001 | Diagramas C4 L1/L2 desenhados em `flowchart`, não em `C4Context`/`C4Container` | Aceito | [architecture/README.md](README.md) |
| N-0002 | `docs/validate-mermaid.sh` renderiza todo bloco Mermaid do repositório | Aceito | [validate-mermaid.sh](../validate-mermaid.sh) |
| N-0003 | `diagram-reviewer` revisa correção arquitetural de diagramas | Aceito | [diagram-reviewer.md](../../.opencode/agents/diagram-reviewer.md) |
| N-0004 | Documentação do projeto em PT-BR, preservando termos oficiais da Camunda em inglês | Aceito | [AGENTS.md](../../AGENTS.md) |
| N-0005 | Afirmações de componente divididas em **Fato (8.9)**, **Observado** e **Interpretação** | Aceito | [camunda-8-local-components.md](../camunda/camunda-8-local-components.md) |
| N-0006 | Diagrama conceitual e diagrama de ambiente físico são separados, nunca misturados | Aceito | [Lesson 001 §9 e §12](../lessons/001-camunda7-8-mental-model/lesson.md) |

**Por que N-0002 e N-0003 são duas decisões e não uma.** Renderizar e estar correto são
propriedades diferentes, e uma ferramenta não substitui a outra. Um diagrama pode renderizar e
mentir; um diagrama pode estar correto e não renderizar. Nenhum teste automatizado substitui
revisão semântica, e nenhum revisor humano substitui o renderizador.
