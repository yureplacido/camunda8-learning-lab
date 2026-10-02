# TASKS-002

- [x] SPEC-002: revisar/ajustar escopo — três requisitos acrescentados, ver spec.md
- [x] PLAN-002: validar fases
- [x] Criar BPMN (processes/) mínimo
- [x] Criar app Java (worker) mínimo
- [x] Deploy BPMN + criar instance + executar worker
- [x] Coletar evidências reais
- [x] Escrever lesson.md + evidence.md
- [x] Verificar DoD
- [x] Marcar review/completed

## Notas de fechamento

O escopo do SPEC cresceu durante a execução: o eixo C7 → 8 pela fronteira transacional, a janela
do secondary storage e os dois regimes do `awaitCompletion` foram ensinados de fato e entraram
como requisitos 7–9. Os itens 5 e 6 acima só fecharam depois da correção do registro anterior,
que descrevia um `SimpleWorker` inexistente.

**Fica aberto, deliberadamente:** `retries="3"` não foi exercitado com worker que lança exceção.
Está declarado como não-escopo na lesson e no SPEC, não como prova.