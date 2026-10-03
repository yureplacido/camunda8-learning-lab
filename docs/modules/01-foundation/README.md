# Módulo 01 — Foundation

Pré-requisito: [Módulo 00 — Orientation](../00-orientation/README.md).

## Lessons

| Lesson | Tópico | Status |
| --- | --- | --- |
| [001](../../lessons/001-camunda7-8-mental-model/lesson.md) | [Onde esses conceitos vivem dentro do Camunda 8](../../lessons/001-camunda7-8-mental-model/lesson.md) | `completed` |
| [002](../../lessons/002-deploy-instance-worker/lesson.md) | [Deploy, Instância e Primeiro Job Worker](../../lessons/002-deploy-instance-worker/lesson.md) | `completed` |
| 003 | Retries, incidentes e recuperação | planejada |
| 004 | Escala e concorrência do worker | planejada |
| 005 | Ciclo de vida e backpressure | planejada |

> **Numeração reenquadrada.** O módulo foi planejado com 002 Modelo de execução do Zeebe, 003
> Execução BPMN, 004 Instâncias e variáveis, 005 Jobs e job workers, 006 Retries e incidentes. A
> Lesson 002 implementada cobriu o conteúdo de 003, 004 e 005 numa única rodada, com evidência de
> execução real, então o que era 006 virou 003. A ordem de aprendizado é a mesma; o rótulo passou a
> descrever o que foi estudado. Ver o registro em
> [learning-roadmap.md](../../learning-roadmap.md).

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
