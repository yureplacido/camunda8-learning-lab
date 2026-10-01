# Módulo 01 — Foundation

Pré-requisito: [Módulo 00 — Orientation](../00-orientation/README.md).

## Lessons

| Lesson | Tópico | Status |
| --- | --- | --- |
| 001 | [Onde esses conceitos vivem dentro do Camunda 8](../../lessons/001-camunda7-8-mental-model/lesson.md) | `ready` |
| 002 | Modelo de execução do Zeebe | planejada |
| 003 | Execução BPMN | planejada |
| 004 | Instâncias de processo e variáveis | planejada |
| 005 | Jobs e job workers | planejada |
| 006 | Retries, incidentes e recuperação | planejada |

## Entregável

O aluno explica onde cada conceito do Módulo 0 vive dentro do Camunda 8, com o que cada
componente conversa, em que direção, e o que cada um **não** faz.

## Estrutura interna da Lesson 001

A lesson segue uma ordem deliberada, e a ordem é o argumento:

1. BPMN no contexto do runtime — o diagrama vira o quê;
2. Camunda 8 Self-Managed — o que é isso que estamos rodando (e por que não é Camunda 8 Run);
3. Zeebe → Gateway → Broker — quem faz o quê;
4. Estado de execução, Job, Worker — onde as coisas moram e quem executa;
5. Aplicação cliente — o caminho completo, do comando ao efeito colateral;
6. Componentes operacionais e UI — e por que "UI não é engine";
7. Ambiente real, Docker Compose e H2 — o que está de fato rodando;
8. O modelo Camunda 7 → 8 — a comparação, por último.

Persistência aparece **depois** dos componentes, porque só faz sentido depois que existe algo
concreto onde ela se pendura.

Esse entregável só é alcançável depois do Módulo 00, que define o que é um workflow engine, o que é
BPMN, e a cadeia *service task* → Job → *job worker*.

## Evidência

[evidence da Lesson 001](../../lessons/001-camunda7-8-mental-model/evidence.md) — saída verbatim
do cluster 8.9.22, incluindo as falhas de coleta e como foram corrigidas.

[Mapa de componentes](../../camunda/camunda-8-local-components.md) — 5 componentes lógicos,
1 processo, 2 containers, 2 volumes.
