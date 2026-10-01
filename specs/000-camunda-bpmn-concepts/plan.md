# Plano do SPEC-000

1. Coletar evidência **somente leitura** do cluster 8.9.22 já em execução, priorizando endpoints que
   tornam um conceito visível, e não endpoints que apenas devolvem 200.
2. Escrever a lesson em **ordem causal**, para que cada seção use apenas o vocabulário que a
   seção anterior introduziu.
3. Desenhar o diagrama da notação BPMN **inline** no documento da lesson. Não criar BPMN
   implantável.
4. Manter todo o runtime — componentes do Camunda 8, log, armazenamento — **fora** desta lesson, e
   terminar com uma ponte explícita para a Lesson 001.
5. Registrar a Lesson 000 na trilha, na estrutura do curso e no índice da base de conhecimento.
6. Marcar a Lesson 001 como dependente da Lesson 000.
7. Verificar links, rótulos de versão e correspondência com a evidência.

## Restrição de ordenação

**Evidência primeiro, prosa depois.** O objetivo anti-mágia da lesson depende de toda afirmação estar
ancorada em algo real.

**Segunda restrição de ordenação, vinda da revisão:** o conteúdo de runtime foi movido para a Lesson
001, porque explicar o Broker exige a partição, e explicar a partição exige o estado de execução. A
ordem original prometia o que não conseguia sustentar.

## O que mudou na revisão de 2026-09-29

| Antes | Depois | Motivo |
| --- | --- | --- |
| Explicar "quais componentes o Camunda 8 tem" na Lesson 000 | Explicar os componentes na Lesson 001 §3–§11 | Dependência circular: Broker → partição → estado |
| Incluir o log e primary/secondary storage | Movido para Lesson 001 §6 e §13 | Persistência depois dos componentes |
| Tabela de componentes com "o que / por que deve existir / prova ao vivo" | Removida da 000; recriada com granularidade explícita em `camunda-8-local-components.md` | Componente lógico ≠ container |
| Lesson escrita em inglês | Reescrita em PT-BR | Regra do repositório |

## Verificação de diagramas

A lesson tem um bloco Mermaid. `docs/validate-mermaid.sh` renderiza **todos** os blocos do
repositório, e é ele que impede que um diagrama quebrado passe despercebido. Renderizar é condição
necessária, não suficiente — ver `.opencode/agents/diagram-reviewer.md`.
