# TASKS-004 — Escala e concorrência do worker

Estado da lição: **COMPLETED**. Implementado e executado; evidência E1–E18 escrita. Três rodadas de
revisão independente: `CHANGES_REQUESTED` (5 blockers de evidência/redação), `CHANGES_REQUESTED`
(1 blocker, `items[0]` em E11) e **`APPROVED`** sem blockers. Promovida sem commit, push ou PR.

## Fase 1 — Chão factual (concluída antes de qualquer afirmação de API)

- [x] **Correção registrada:** `/actuator/health/status` e `/actuator/health` respondem `404`;
      prontidão medida por `docker compose ps` (`orchestration` e `connectors` healthy) — E1
- [x] Sondar `POST /v2/jobs/activation` com corpo vazio; `400 INVALID_ARGUMENT` nomeia os 3 campos — E2
- [x] Completar o corpo com `worker`, `type`, `timeout`, `maxJobsToActivate`; `200 {"jobs":[]}` — E4
- [x] Declarar se o campo `worker` volta no Job ativado: **volta** — E7
- [x] Pré-checagem: `totalItems: 0` para `lesson-004-scale` — E3
- [x] Capturar `docker compose ps`: um `orchestration`, um `connectors`, nenhum container de worker — E1

## Fase 2 — Perguntas abertas do probe (concluída)

- [x] `maxJobsToActivate: 4` devolveu exatamente 4 — E7
- [x] Os 2 Jobs restantes continuaram ativáveis até a terceira chamada — E7
- [x] Latência de índice medida: search imediato mostrou `CREATED`; após ~3 s, `COMPLETED` — E9
- [x] Backpressure: `N/A` com motivo (tema da 005, sem carga para `429`/`503`)
- [x] Concorrência declarada como lógica, não simultaneidade temporal — E7, E11

## Fase 3 — Implementação (concluída)

- [x] BPMN em `processes/004-escala-concorrencia-worker/lesson-004-scale.bpmn`
- [x] Deploy com captura aninhada da `processDefinitionKey` — E5
- [x] 10 instâncias com `runId` único e captura das keys — E6
- [x] Ativações `w4-a`/`w4-b`/`w4-drain` com lotes `4`/`4`/`2` — E7
- [x] Completar os 10 Jobs no mesmo script; 10 instâncias `COMPLETED` — E8
- [x] Requests de coleção com asserções de conjunto de chaves e do delta `4+4+2` — E11
- [x] Diagrama C4 L2 com Diagram Review: 11 blocos, 0 falhas na cópia isolada; no repositório real
      o script reporta 51 blocos e 2 falhas em READMEs de `node_modules` (cópia isolada; repositório
      real 51/2 — E13). Veredito do Diagram Review: `WARN`, não `PASS`

## Fase 4 — Evidence e lesson (concluída)

- [x] `evidence.md` com E1–E18, corpos verbatim, `docker compose ps` e busca final
- [x] `lesson.md` preenchido, sem afirmação não executada
- [x] Perguntas de entrevista com a prova que as sustenta
- [x] README da coleção atualizado com a pasta `60` e as duas novas variáveis

## Fase 5 — Revisão (concluída)

- [x] Primeira revisão independente executada: **CHANGES_REQUESTED**, 5 blockers — E14
- [x] B1 — "clientes Java" trocado por clientes lógicos; README e evidence falam em três clientes
      (`w4-a`, `w4-b` em disputa + `w4-drain`), não dois
- [x] B2 — "`Timeout` medido na 003" corrigido para Fato 8.9 com medição `N/A` na 003; vazão por
      unidade de tempo complementada com intervalo de polling (014)
- [x] B3 — "O motor não empurra" escopado ao REST/polling; *job streaming* da própria doc 8.9 citado
      como fora de escopo
- [x] B4 — Diagram Review reescrito no formato do `diagram-reviewer.md`, com fonte por afirmação e
      `WARN` de ferramenta; E13 registra o `51/2` real e o `11/0` da cópia isolada
- [x] B5 — desenho marcado como executado e filtro `state: CREATED` colocado ao lado da versão
      refutada por E10; mesma nota no `spec.md`
- [x] Achado extra encontrado ao medir: E11 dizia que o deploy do runner criou versão nova; a busca
      mostrou uma única versão — corrigido para "reenvio idêntico é idempotente"
- [x] E15 — prova direta de que `state: CREATED` seleciona Job vivo, com limpeza no mesmo comando
- [x] Segunda revisão: **CHANGES_REQUESTED**, 1 blocker — `items[0]` em E11 corrigido com `total: 1`
- [x] WARNs da segunda revisão fechados: veredito `WARN`, E1–E18, ressalva `11/0`, três clientes,
      `runId: null` explicado em E16, nome do teste trocado com runner reexecutado
- [x] Terceira revisão: **`APPROVED`**, sem blockers e sem WARNs relevantes — E14
- [x] Promoção a `completed` sem commit, push ou PR

## Ajustes de roadmap aplicados neste branch (sem renumerar)

- [x] `docs/learning-roadmap.md` — 005 renomeada para "Ciclo de vida do Job e backpressure";
      fronteira 004/005/014 escrita no lugar do "sem duplicar" genérico; 014 renomeada para
      "Configuração e observabilidade de workers Java/Spring Boot"
- [x] `docs/modules/01-foundation/README.md` — título da 005 alinhado
- [x] `docs/modules/03-workers/README.md` — título da 014 alinhado em PT-BR descritivo

## Correções descobertas durante a execução

- [x] Health check trocado por `docker compose ps` (E1)
- [x] Pré-checagem da coleção passou a filtrar `state: CREATED` (E10)
- [x] `header` do `pm.sendRequest` trocado de string para objeto (E10)

## Fora de escopo, para não crescer durante a execução

- Particionamento, múltiplos Brokers, `HashMod`, roteamento por partição
- `maxJobsActive`, `pollInterval`, thread pool, `graceful shutdown`, `Job.YIELD`, métricas
- Estados do Job e backpressure como tese; retries e incidentes
- Idempotência de efeito colateral; autenticação, multi-tenancy, Kubernetes, Kafka, Connectors
- Benchmark, timing, contagem fixa de asserções
- Novo módulo Java, modificação de artefato de lesson revisada, edição do Compose vendorizado
