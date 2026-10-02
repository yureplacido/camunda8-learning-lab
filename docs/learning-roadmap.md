# Trilha de aprendizado — Camunda 8 Developer Lead

## Módulo 0 — Orientation

**000 — O que é Camunda, e o que suas partes fazem**

Justificativa: a trilha original começava em `001`, cujo entregável pressuponha que o leitor já
sabia o que é um workflow engine, o que é BPMN, e a diferença entre *process definition* e
*process instance*. A Lesson 000 estabelece esse vocabulário antes de qualquer coisa comparar ou
construir em cima dele.

Escopo: BPMN, workflow engine, *process definition*, *process instance*, variável, *task*,
*user task*, *service task*, Job, Worker e completion. Não escreve código e não implanta nada.

## Módulo 1 — Foundation

**001 — Onde esses conceitos vivem dentro do Camunda 8**

Sequência interna, nesta ordem: BPMN no runtime → Self-Managed → Zeebe → Gateway → Broker →
Estado de execução → Job → Worker → aplicação cliente → componentes operacionais e UI →
ambiente local real → Docker Compose → H2 → modelo Camunda 7 → 8.

Requer: Módulo 0. A Lesson 001 consome o vocabulário da 000 e não o repete.

**Entregável:** explicar onde cada conceito vive no Camunda 8, com o que cada componente
responde e o que ele **não** faz.

Observação de escopo: a Lesson 001 **não** é "a lesson de comparação C7 → 8". É a lesson de
**onde o Camunda 8 executa**, e a comparação é sua última seção, quando já existe algo concreto
para comparar.

| Lesson | Tópico | Status |
| --- | --- | --- |
| [001](lessons/001-camunda7-8-mental-model/lesson.md) | Onde esses conceitos vivem dentro do Camunda 8 | `completed` |
| [002](lessons/002-deploy-instance-worker/lesson.md) | Deploy, Instância e Primeiro Job Worker | `completed` |
| 003 | Retries, incidentes e recuperação | planejada |
| 004 | Escala e concorrência do worker | planejada |
| 005 | Ciclo de vida e backpressure | planejada |

**Ajuste de numeração, registrado em vez de sobrescrito.** Este módulo foi planejado como
001 Onde os conceitos vivem · 002 Modelo de execução do Zeebe · 003 Execução BPMN · 004 Instâncias
e variáveis · 005 Jobs e job workers · 006 Retries, incidentes e recuperação.

A Lesson 002 implementada executou em uma só rodada o conteúdo que estava planejado para 003, 004
e 005 — deploy, execução do BPMN, criação de instância com variáveis, e o primeiro Job Worker
consumindo um Job —, cada um com evidência de execução real. Ensinar "Execução BPMN" de novo,
como uma 003, repetiria material já provado.

Então os números foram reenquadrados para refletir o que foi de fato estudado: **003 passa a ser
Retries, incidentes e recuperação**, o tema que era o 006. A ordem de aprendizado não muda — a
fronteira transacional vem antes de retries, e retries vem antes de escala. Muda o rótulo.

Os temas antigos 004 e 005 (escala, backpressure) entram como 004 e 005, e as lessons que
interessam ao Módulo 3 — workers Java, testes de worker, ciclo de vida — permanecem nos módulos
seguintes, sem duplicar.

## Módulo 2 — BPMN e comportamento de workflow

007 Mensagens e correlação · 008 Timers · 009 Erros e boundary events · 010 Subprocessos e call
activities · 011 Padrões avançados de BPMN

## Módulo 3 — Java/Spring Boot

012 Workers com Java/Spring Boot · 013 Testes de worker · 014 Ciclo de vida do worker,
concorrência e backpressure

## Módulo 4 — Sistemas distribuídos

015 Idempotência e entrega duplicada · 016 Consistência e fronteiras transacionais · 017
Outbox/inbox e padrões de integração · 018 Integração com Kafka · 019 REST e connectors · 020
Timeouts, retries e compensação

## Módulo 5 — Workflow humano

021 User tasks e Tasklist · 022 Atribuição, autorização e latência humana

## Módulo 6 — Plataforma e operações

023 Observabilidade · 024 Operate e troubleshooting · 025 Escala e particionamento · 026 Segurança
e Identity · 027 Kubernetes/OpenShift/AWS · 028 Prontidão para produção

## Módulo 7 — Arquitetura e liderança

029 Migração Camunda 7 → 8 · 030 Orquestração vs coreografia · 031 Caso de arquitetura de
produção · 032 Governança e decisões de arquitetura

## Módulo 8 — Capstone e entrevista

033 Capstone de originação de empréstimo bancário · 034 Revisão de arquitetura como Developer
Lead · 035 Simulação de troubleshooting · 036 Simulação de entrevista de Developer Lead

## Regra do curso

A estrutura do repositório é estabelecida primeiro. A implementação segue a sequência de
aprendizado.

**A lesson nunca implementa uma lição futura.** Onde uma lesson encontra uma lacuna, ela registra
a lacuna e deixa a implementação para a lesson que vai ensiná-la.

A regra original dizia que `.mise.toml` ficaria com `[tools]` vazio até a primeira lesson que
precissasse de JVM. Isso deixou de valer na Lesson 002, que fixou Java 21 e Maven para o
`first-worker`. O arquivo está fixado, e a justificativa está no próprio `.mise.toml`.
