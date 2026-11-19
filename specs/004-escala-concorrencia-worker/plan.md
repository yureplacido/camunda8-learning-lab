# PLAN-004 — Escala e concorrência do worker

## Sequência de execução

A ordem é deliberada e repete a regra que salvou a 003: **medir antes de afirmar**. A 002 manchou
o repositório ao concluir que a REST não ativava Job a partir de um `404` de um path inexistente.
Aqui o risco equivalente é afirmar o formato de `activation` a partir da doc e o tamanho do lote a
partir do nome do parâmetro. Então o probe vem primeiro, e o que ele não responder fica como
tarefa ou como `N/A`.

### Fase 1 — Chão factual (a executar antes de qualquer afirmação de API)

1. Confirmar que o cluster está *healthy* por `docker compose ps` e registrar a saída.
   **Correção aplicada depois do probe:** o texto original dizia `/actuator/health/status`, que
   responde `404` na 8.9.22 — evidence E1.
2. Sondar `POST /v2/jobs/activation` com corpo vazio e confirmar que o `400` nomeia `type`,
   `timeout` e `maxJobsToActivate` — prova de que o path existe e valida corpo.
3. Completar o formato real com `worker`, `type`, `timeout` e `maxJobsToActivate`; **ler o corpo
   da resposta**, não o status.
4. Confirmar por `/v2/jobs/search` que não existe Job `lesson-004-scale` residual; se existir,
   parar e limpar antes de medir.
5. Registrar `docker compose ps` — a evidência de que há um `orchestration`, não dois workers
   em containers.

### Fase 2 — O que o probe precisa responder durante a implementação

- **O campo `worker` aparece no corpo do Job ativado?** Se aparecer, é a prova direta de qual
  cliente recebeu o quê. Se não aparecer, a prova passa a ser o agrupamento por resposta HTTP —
  e a limitação é registrada.
- **`maxJobsToActivate: 4` devolve exatamente 4?** Se devolver menos (fila menor que o lote no
  momento da chamada), ajustar o número de instâncias, nunca a afirmação.
- **Os 2 Jobs restantes continuam ativáveis?** Medir com a terceira ativação e com
  `/v2/jobs/search` antes dela.
- **Há latência de índice entre ativação e `search`?** A 003 observou esse efeito; aqui ele
  pode falsear a contagem da fila. Medir e registrar.
- **Backpressure aparece?** Previsto `N/A`: é tema da 005 e não há carga para provar `429`/`503`.

### Fase 3 — Implementação

1. `processes/004-escala-concorrencia-worker/lesson-004-scale.bpmn` — Start → Service Task
   `lesson-004-scale` → End, com `retries` explícito só para não herdar comportamento oculto.
2. Deploy e captura da `processDefinitionKey` pelo caminho aninhado já conhecido.
3. 10 instâncias com `runId` único (`004-run-<timestamp>-<i>`), capturando as 10 keys.
4. Três ativações com `worker` distinto (`w4-a`, `w4-b`, `w4-drain`) e lotes `4`, `4`, `2`.
5. Completar os 10 Jobs ativados no mesmo script; verificar 10 instâncias `COMPLETED`.
6. Requests de coleção com asserções sobre conjuntos de chaves e sobre o delta `4+4+2`.
7. Diagrama C4 L2 mostrando os dois **clientes** e o **um** Broker/Gateway; Diagram Review.

### Fase 4 — Evidence e lesson

- `evidence.md` com o corpo verbatim das três ativações, a pré-checagem, o `docker compose ps`
  e a busca final.
- `lesson.md` na ordem causal, mantido em `drafting` até o gate de execução/revisão.
- Perguntas de entrevista respondidas com a prova que as sustenta.

### Fase 5 — Revisão

- Revisão independente focada em três riscos: (1) "workers" confundidos com containers,
  (2) afirmação de limite sem corpo de resposta, (3) resíduo de Job de run anterior.

## Fora da sequência, por decisão de escopo

- Configuração de `@JobWorker`, `maxJobsActive`, `pollInterval` e threads — 014.
- Estados do Job e backpressure como tese — 005.
- Particionamento e roteamento — 025.
- Novo módulo Java, alteração em `first-worker`/`retry-worker`, edição do Compose vendorizado.
