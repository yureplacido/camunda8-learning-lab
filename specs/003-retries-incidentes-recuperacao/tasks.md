# TASKS-003 — Retries, incidentes e recuperação

Estado da lição: **SKELETAL**. Nenhuma tarefa de implementação marcada como feita.

## Fase 1 — Chão factual (concluída antes do SPEC)

- [x] Confirmar `newFailCommand` no Java Client 8.9.22
- [x] Confirmar ausência de reset de retries no SDK
- [x] Confirmar ausência de comando de resolução de incidente no SDK
- [x] Medir o ciclo completo por `curl`
- [x] Medir o backoff por ausência de Job ativável

## Fase 2 — Responder o que o probe deixou aberto

- [ ] A ordem reset→resolve é obrigatória? Medir invertendo e registrar o resultado
- [ ] `errorMessage`: sobrevive ao backoff ou pertence só ao Job falhado?
- [ ] Latência entre o rótulo `RETRIES_UPDATED` no índice e a ativação real no motor
- [ ] Limpar ou registrar os Jobs de probe (`probe-003`, `probe-bf-*`) como resíduo

## Fase 3 — Implementação

- [ ] `processes/003-retries-incidentes-recuperacao/` com o BPMN e `retries="3"`
- [ ] Teste de contrato BPMN↔worker para o novo processo
- [ ] `apps/retry-worker/` com o worker que falha sob comando
- [ ] Teste da decisão de falhar (sucesso e falha)
- [ ] Prova por mutação: renomear o `type` reprova o teste de contrato
- [ ] Requests de coleção para o ciclo completo
- [ ] `validate-collections.sh` verde com os paths novos

## Fase 4 — Evidence e lesson

- [ ] Executar os dois regimes e capturar saída real
- [ ] `evidence.md` com comandos, saídas e correções visíveis
- [ ] `lesson.md` na ordem causal
- [ ] Diagrama com Diagram Review e `validate-mermaid.sh` verde
- [ ] Entrevistas respondidas
- [ ] Seção "O que ainda não é verdade sobre esta lesson" enquanto `ready`

## Fora de escopo, para não crescer durante a execução

- Backoff exponencial no BPMN
- Idempotência do efeito colateral
- Alertamento e SLA de incidente
- Versionamento, multi-tenancy, autenticação, escala, múltiplas partições