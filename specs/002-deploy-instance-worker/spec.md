# SPEC-002 — Deploy, Instância e Primeiro Job Worker

## Status
Completed

## Escopo
Criar a primeira execução prática conectando vocabulário (000) e runtime (001). Objetivo: **implantar um BPMN simples, criar uma process instance, e ter um primeiro Job Worker Java consumindo um Job de Service Task**, com evidências observadas no ambiente local (8.9.x).

## Não-escopo
- Connectors, Kafka, DMN/FEEL avançado, múltiplas partições, multi-tenancy avançado
- Retry customizados complexos, timers/boundary events (serão em lessons futuras)
- Kubernetes/cloud

## Pré-requisitos
- Ambiente local 8.9 subindo (ou reutilizável) conforme `infra/local/README.md`
- Lessons 000 e 001 completed

## Requisitos
1. BPMN mínimo com pelo menos 1 Service Task (type explícito)
2. Deploy via API/cliente Java (ou ferramenta validada) com evidência
3. Criar Process Instance com variáveis mínimas, com evidência (topology/actuator e busca)
4. Primeiro Job Worker Java (Spring Boot ou Java vanilla) ativando/completando Job, com logs e evidências
5. Evidências em PT-BR (Fato/Observado/Interpretação), sem inventar saídas
6. Lesson em PT-BR, seguir template e regras AGENTS.md

## Requisitos acrescentados durante a execução

O escopo original acima previa "implantar, criar instância, rodar worker". Três comportamentos
foram ensinados de fato e não estavam aqui, então foram absorvidos para o SPEC não divergir do
que a lesson afirma. A matriz de compatibilidade de dependências (D1/D2) **não** entrou: é
material de infra, e continua registrada em evidence.md.

7. **Eixo C7 → 8 pela fronteira transacional.** Ensinar que ativação e completion são comandos
   separados no log, sem ACID compartilhado com o worker, e que at-least-once é consequência
   disso — e não "o banco virou log".
8. **Janela do secondary storage.** O leitor vai do H2 secundário, e não do log primário; a
   instância aparece `ACTIVE` antes de o Job completar. A lesson tem de pollar por estado, não
   por existência.
9. **Dois regimes do `awaitCompletion`.** Sem worker: `504` sem `processInstanceKey` e instância
   não cancelada. Com worker: `200` sem `state` no corpo. Nenhum dos dois é óbvio, e nenhum
   estava previsto.

### Requisitos de fora do escopo que continuam abertos

`retries="3"` está no BPMN mas **não** foi exercitado com worker que lança exceção. Múltiplas
partições, autenticação e escala do worker seguem declarados fora de escopo na lesson.

## DoD
- spec.md, plan.md, tasks.md, lesson.md, evidence.md presentes
- Código Java mínimo compila e roda localmente
- Evidências reais coletadas (comandos + saídas)
- Distinção lógico≠container respeitada
- Nenhum segredo comitado

## Referências
- Lesson 000, 001
- AGENTS.md
