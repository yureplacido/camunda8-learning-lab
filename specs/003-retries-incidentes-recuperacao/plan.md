# PLAN-003 — Retries, incidentes e recuperação

## Sequência de execução

A ordem é deliberada: **medir antes de escrever**. A 002 foi manchada por afirmar uma fronteira
de API a partir de um `404`, e o SPEC-003 não pode repetir o erro. Então o probe vem antes da
implementação, e o que o probe não responder fica como tarefa, nunca como afirmação.

### Fase 1 — Chão factual (feito antes do SPEC)

1. Confirmar que o Java Client fixado (8.9.22) expõe `newFailCommand`.
2. Confirmar que ele **não** expõe reset de retries nem resolução de incidente.
3. Validar por `curl` o ciclo completo: `activation` → `failure`×3 → incidente → `PATCH` →
   `resolution` → `activation` → `completion` → instância `COMPLETED`.
4. Validar o backoff por ausência de Job ativável dentro da janela.

### Fase 2 — O que falta responder

Executado durante a implementação, e registrado como `Observado` ou como `N/A` com motivo:

- **A ordem reset→resolve é obrigatória?** Medir invertendo. Se resolver primeiro deixar o Job
  com `retries=0`, ele volta a falhar ou não é ativável — e isso muda o runbook.
- **`errorMessage` sobrevive ao backoff?** O Job ativado depois da janela voltou com
  `errorMessage=null`; confirmar se o campo é do Job falhado e não do Job ativado.
- **Rótulo de estado no `search` versus motor.** `RETRIES_UPDATED` no índice secundário enquanto
  o motor já tratava o Job como ativável. Medir quanto tempo leva e por que.

### Fase 3 — Implementação

1. `processes/003-retries-incidentes-recuperacao/` — BPMN com `retries="3"`.
2. `apps/retry-worker/` — módulo novo (decisão em aberto no SPEC), worker que falha sob comando.
3. Testes: contrato BPMN↔worker, e a decisão de falhar.
4. Pasta de coleção fechando o ciclo por requests.

### Fase 4 — Evidence e lesson

1. Executar os dois regimes e capturar saída real.
2. `evidence.md` com o que rodou, o que não rodou, e as correções.
3. `lesson.md` na ordem causal: problema → categoria → notação → vocabulário → forma.

## Riscos

| Risco | Mitigação |
|---|---|
| Reincidência da 002: afirmar capacidade a partir de um path | Toda afirmação de API vem de sonda ou da spec viva, com o comando registrado |
| Asserção varrendo estado acumulado do cluster | Cada request de recovery usa a `processInstanceKey`/`jobKey` do próprio run; tipo de Job único por execução |
| Contagem de asserção virar "invariante" | Registrar delta e `0` falhas; o total é medição |
| Worker leaving Job travado affecting a próxima execução | Timeout curto e verificável; `awaitMode` explícito |

## Decisões que tomei e o porquê

- **Módulo novo em vez de estender `first-worker`:** a 002 tem contrato revisado; acoplar
  aciona revisão de novo a cada mudança da 003.
- **`curl` antes de Java no probe:** separa o comportamento do motor do comportamento do client.
  A 002 provou que são coisas diferentes — o motor falava com o REST desde sempre, e o SDK é
  só uma casca.