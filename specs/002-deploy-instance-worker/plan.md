# PLAN-002 — Deploy, Instância e Primeiro Job Worker

Status: concluído

## Fase 1 — Skeleton
- [x] Criar estrutura specs + lesson
- [x] Definir modelo BPMN mínimo

## Fase 2 — Implementação
- [x] Criar app Java mínimo (worker)
- [x] BPMN com service task type
- [x] Deploy + start instance + worker executar

## Fase 3 — Evidências
- [x] Coletar saídas reais (REST/gRPC, actuator, docker compose, logs)
- [x] Preencher evidence.md

## Fase 4 — Revisão
- [x] Validar DoD, PT-BR, distinções
- [x] Lesson ready → review → completed

## Desvio do plano, registrado

O plano previa "criar app Java mínimo" e foi cumprido — mas a primeira versão da lesson e da
evidence já Affirmava um `SimpleWorker` com `ZeebeClient`/`usePlaintext()` que **não existia** no
repositório, e um `-F "deployment-name=..."` que o contrato v2 rejeita. Nenhuma execução tinha
sido feita. A Fase 3 só produziu evidência real depois dessa correção.

Também surgiram três behaviors fora do escopo original, absorvidos como requisitos 7–9 no SPEC: o
eixo C7 → 8 pela fronteira transacional, a janela do secondary storage e os dois regimes do
`awaitCompletion`.