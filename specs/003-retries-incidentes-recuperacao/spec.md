# SPEC-003 — Retries, incidentes e recuperação

## Status
Ready

## Goal

Ensinar que `retries` é um **contador no log**, que zerar não é apenas parar, e que o motor
escreve um artefato novo — o **incidente** — com ciclo de vida próprio. E que recuperar não é
repetir a tentativa: é uma operação de duas ordens, feita por um **operador**, fora do worker.

A frase que a lesson tem de fazer o leitor internalizar:

> O worker pode falhar. **Ninguém, no caminho do worker, pode se recuperar.**

## O problema primeiro (ordem causal)

Um service task que sempre falha é chato de ler e fácil de errar. A pergunta útil não é
"quantas vezes ele tenta", e sim **o que existe no sistema depois que ele para de tentar**.
Em C7, a falha some no log da aplicação e o operador precisa ir procurar. Em C8, o motor
materializa a parada num artefato consultável por API, com estado próprio.

### Eixo C7 → 8

Em C7 o job, o contador de retry e o registro de incidente eram linhas da mesma transação
que recebia o relatório de falha do engine. Uma transação ACID; ou o retry decrementava e o
incidente existia, ou nada acontecia.

Em C8, "job falhou com `retries=0`" e "incidente criado" são **dois comandos no log**. A
resolução é um terceiro. Nenhum é atômico em relação ao outro. A fronteira transacional
perdida é a causa; "o banco virou log" é a consequência, e não explica nada sozinha.

## Scope

1. BPMN com `retries="3"` num service task de tipo próprio da lesson.
2. Worker que **falha de propósito**, sob comando, e registra cada tentativa.
3. Observar a contagem decrementar até `JOB_NO_RETRIES` **por API**, não por suposição.
4. Recuperar: resetar `retries` e resolver o incidente, na ordem que a observação exigir.
5. Observar o Job voltar a ser ativável e a instância chegar a `COMPLETED`.
6. Observar backoff: janela em que o Job **não** é ativável.
7. Testes que provem o contrato BPMN↔worker e a decisão de falhar.
8. Requests de coleção que fechem o ciclo inteiro por `curl`.

## Out of scope

Declarado aqui para não crescer durante a execução:

- **Backoff exponencial** configurado no BPMN. Só foi observado o `retryBackOff` de janela
  única passada no comando de falha. A notação do Modeler fica para a lesson de lifecycle.
- **Idempotência do efeito colateral de negócio.** O worker vai ser determinístico e sem
  efeito externo; o risco de "processar duas vezes" é nomeado, não exercitado.
- **Alertamento e SLA de incidente.** Operate Cloud faz isso; aqui é cluster local.
- **Job worker versioning, multi-tenancy, autenticação, múltiplas partições, escala.**
- **Resolução automática.** Nenhum agent resolve incidente sozinho nesta lesson. A ausência é
  o ponto: a recuperação é humana por construção.

## Requisitos

1. O Job deve ser criado pelo motor com `retries=3`, lido da API, e não assumido.
2. A contagem deve ser observada decrementando por comando de falha real.
3. O incidente deve ser observado por `POST /v2/process-instances/{key}/incidents/search`,
   com `errorType` e o vínculo com `jobKey`.
4. A recuperação deve ser executada por comandos reais e o Job deve voltar a ser ativável.
5. `errorMessage` deve aparecer no Job falhado e ser lido de volta.
6. O backoff deve ser observado por **ausência** de Job ativável dentro da janela.
7. Testes devem falhar se o BPMN e o worker divergirem (mesma prova por mutação da 002).
8. Toda evidência em PT-BR, rotulada `Fato` / `Observado` / `Interpretação`.
9. Nenhum caminho de API pode ser afirmado a partir de documentação sem sondagem: foi
   exatamente esse erro que manchou a 002.

## Findings que já são Fato (Observado, 8.9.22)

Medidos antes de escrever este SPEC, porque o SPEC não pode ensinar de memória:

| Observação | Evidência |
|---|---|
| Job nasce `CREATED`, `retries=3`, com o valor do BPMN | `/v2/jobs/search` logo após criar a instância |
| Ciclo REST completo existe | `activation` → `{key}/failure` → `{key}/completion` → `search` |
| Esgotar retries cria incidente | `state=FAILED retries=0` + incidente `JOB_NO_RETRIES`, `state=ACTIVE` |
| `errorMessage` persiste no Job falhado | lido de volta no `search` |
| Reset de retries responde `204` | `PATCH /v2/jobs/{key}` com `changeset.retries` |
| Resolver incidente responde `204` | `POST /v2/incidents/{key}/resolution` |
| Resolver duas vezes responde `404` | incidente já `RESOLVED` |
| Após reset + resolução o Job volta | `activation` devolve o mesmo `jobKey` com `retries=3` |
| Completion fecha a instância | `204`, instância `COMPLETED` com `endDate` |
| Backoff bloqueia a ativação | `0` Jobs dentro da janela, `1` após |
| O campo é `retryBackOff`, inteiro | `"PT20S"` → `400 retryBackoff cannot be parsed`; `retryBackOff` é o nome |
| O SDK **não** reseta retries | `UpdateRetriesJobCommandStep1` não é retornado por nenhuma interface pública |
| O SDK **não** resolve incidentes | só existem DTOs de `search`, nenhum comando de resolução |
| **A ordem reset→resolve é obrigatória** | resolver primeiro responde `409` e o Job não volta |
| O 409 diz a ordem | `... has no retries left. Please update the job retries and retry resolving the incident` |
| `errorMessage` persiste no registro do Job | continua lá depois do backoff e de nova tentativa |
| `errorMessage` não vem na ativação | o payload de `activation` traz `null`; a busca traz o texto |
| `TIMED_OUT` é estado distinto de `FAILED` | Job ativado e não respondido tem estado próprio |

### Assimetria que é o eixo da lesson

O Java Client tem `newFailCommand(...).retries(n).retryBackoff(Duration)` e **não** tem
reset de retries nem resolução de incidente. Quem consegue falhar não consegue recuperar.
A recuperação é uma ação de operador, e por isso é um segundo passo deliberado.

## Constraints

- Java 21+, Maven, Spring Boot, cluster local 8.9.x conforme ADR-0002.
- Nenhuma instalação global; toolchain via `infra/local/mise.sh`.
- `infra/local/camunda-8.9/` é material upstream e não é editado.
- Um worker por tipo de Job: o tipo da 003 é diferente do da 002 para não competir.
- Diagramas passam `docs/validate-mermaid.sh` e trazem Diagram Review.

## Decisões em aberto (para o revisor)

1. **Módulo novo (`apps/retry-worker`) ou extensão do `first-worker`?** Recomendo módulo novo:
   a 002 já passou por revisão independente com o seu contrato, e acoplar a 003 nela faz a
   002 regredir quando a 003 mudar. Custo: mais boilerplate Maven.
2. **Nome do processo.** Recomendo `processes/003-retries-incidentes-recuperacao/`, com um
   processo de negócio curto e um service task claramente rotulado como falível.
3. **A ordem reset→resolve é obrigatória, e o motor diz isso.** Resolver com `retries=0`
   responde `409 INVALID_STATE` e o Job permanece `FAILED` e inativável. O próprio erro nomeia o
   passo que falta. É a melhor evidência possível de que "recuperar" são **dois comandos com
   ordem**, e não um comando "desfazer".

### Estados de Job observados, que não são o mesmo

`FAILED` é alguém_reportando falha (comando de falha). `TIMED_OUT` e o Job foi ativado
e o lock expirar sem resposta — o worker morreu, não errou. São comportamentos diferentes de
produção e a lesson precisa nomear os dois, porque o consome de `TIMED_OUT` não tem
`errorMessage` para mostrar.

## Verification

- `./infra/local/mise.sh exec -- mvn -f apps/<modulo>/pom.xml test` verde, com prova por mutação.
- `./infra/local/validate-collections.sh` sem divergência.
- Ciclo completo executado por requests, com os dois regimes registrados.
- `docs/validate-mermaid.sh` verde para os diagramas novos.
- `evidence.md` só com comando executado e saída real.

## Dependencies
- Lesson 002 completed (worker, contrato BPMN↔worker, coleção, runner).
- Correção da 002 (`5431834`): o ciclo REST v2 existe e é o caminho da recuperação.

## Related lesson
`docs/lessons/003-retries-incidentes-recuperacao/lesson.md`