# SPEC-002 — Deploy, Instância e Primeiro Job Worker

## Status
Draft

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

## DoD
- spec.md, plan.md, tasks.md, lesson.md, evidence.md presentes
- Código Java mínimo compila e roda localmente
- Evidências reais coletadas (comandos + saídas)
- Distinção lógico≠container respeitada
- Nenhum segredo comitado

## Referências
- Lesson 000, 001
- AGENTS.md
