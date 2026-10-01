# Módulo 00 — Orientation

Apenas a Lesson 000:

- O que é Camunda, o que é BPMN, e o que é uma *service task*, um Job e um Worker

## Por que este módulo existe

A trilha começava em `001 — modelo mental Camunda 7 → 8`, cujo entregável era "explicar como um
processo BPMN vira trabalho executável distribuído". Isso pressuponha vocabulário que o curso
nunca ensinou: o que é um workflow engine, o que é BPMN, e a diferença entre *process definition* e
*process instance*. A Lesson 001 era, portanto, uma introdução a um vocabulário até então desconhecido
travestida de comparação.

A Lesson 000 fornece a base que faltava, **em ordem causal**, e ancora cada conceito no cluster
local 8.9.22. Não implanta nada e não escreve código.

## Escopo

**Dentro:** BPMN, workflow engine, *process definition*, *process instance*, variável, *task*,
*user task*, *service task*, Job, Worker e completion.

**Fora:** componentes do runtime, Zeebe, Broker, Gateway, persistência, log, H2, arquitetura
interna, e a comparação C7 → 8. Tudo isso pertence à
[Lesson 001](../01-foundation/README.md), e a Lesson 000 termina com uma ponte para lá.

## Entregável

O aluno consegue olhar para uma implantação do Camunda 8 e, para cada componente presente, dizer o
que ele faz e o que quebraria sem ele — e consegue recitar de memória a cadeia
*service task* → Job → *job worker* → completion.

## Lessons

| Lesson | Tópico | Status |
| --- | --- | --- |
| 000 | [O que é Camunda, e o que suas partes fazem](../../lessons/000-camunda-bpmn-concepts/lesson.md) | `ready` |

Evidência: [evidence](../../lessons/000-camunda-bpmn-concepts/evidence.md).
