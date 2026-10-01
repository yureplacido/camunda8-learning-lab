# SPEC-000 — Conceitos de Camunda e BPMN

Status: aceito
Data: 2026-09-29

## Objetivo

Estabelcer o vocabulário que o resto do curso pressupõe, em um só lugar e sem assumir
conhecimento prévio: o que é um workflow engine, o que é BPMN, o que é Camunda, e a cadeia
*service task* → Job → *job worker*.

## Por que este SPEC existe

`docs/learning-roadmap.md` começava o Módulo 1 em `001 — modelo mental Camunda 7 → 8` e declarava
o entregável do módulo como "explicar como um processo BPMN vira trabalho executável distribuído".
Esse entregável pressupõe vocabulário que o curso nunca estabeleceu: o que é BPMN, o que é um
workflow engine, e a diferença entre *process definition* e *process instance*.

A Lesson 001 era, portanto, uma introdução a um vocabulário desconhecido. Este SPEC cria a camada
que faltava como Lesson 000.

## Escopo

**Dentro:**

- A cadeia causal: problema → workflow engine → notação BPMN → vocabulário → Camunda.
- O vocabulário central que as lessons posteriores assumem, incluindo a cadeia
  *service task* → Job → *job worker*.
- BPMN: *process definition*, *process instance*, variável, *task*, *user task*, *service task*,
  gateway, e eventos básicos de início e fim.
- O que é um Job e o que é um Worker, em termos de papel — **sem** o runtime que os hospeda.
- Cada conceito ancorado no cluster local 8.9.22, que já está em execução.

## Fora de escopo

| Excluído | Motivo |
| --- | --- |
| Implantar ou executar um processo BPMN | A notação é explicada; nada é executado. Lessons posteriores. |
| Escrever um job worker | Requer primeiro a lesson do modelo de execução |
| Mecânica de retry, incidente e recuperação | O vocabulário é introduzido; o comportamento não |
| Comparação Camunda 7 → 8 | Isso é a Lesson 001 |
| Componentes do runtime: Zeebe, Gateway, Broker, partições | É a Lesson 001 §3–§5 |
| Persistência: log, snapshots, H2, primary/secondary storage | É a Lesson 001 §6 e §13 |
| DMN, expressões FEEL, Kafka, Kubernetes, multi-tenancy | Lessons separadas |
| Qualquer Java, Spring Boot ou ferramenta de build | Esta lesson produz apenas documentação |

**Nota de escopo.** Esta lesson **não** introduz componentes do Camunda 8. A versão anterior deste
SPEC prometia explicar "quais componentes o Camunda 8 tem e por que cada um precisa existir", e a
revisão mostrou que essa promessa é irrealizável sem antes dar conta de Zeebe, Broker e partições:
explicar o Broker exige a partição, e explicar a partição exige o estado de execução. A promessa foi
movida para a Lesson 001, que tem a ordem certa para sustainedê-la.

## Requisitos

- Toda afirmação factual sobre o Camunda 8 é rotulada **Fato (8.9)** e tem fonte na documentação
  oficial da versão fixada no ADR-0002, com URL verificada.
- Toda afirmação sobre o ambiente em execução é rotulada **Observado** e sustentada por um comando
  verbatim e sua saída em `evidence.md`.
- Raciocínio que não é nenhum dos dois é rotulado **Interpretação**.
- Todo termo introduzido vem acompanhado do que quebraria sem ele.
- O aluno é avisado de quais simplificações populares estão erradas.
- Todo conceito de runtime fica para a Lesson 001, e a Lesson 000 termina com uma ponte explícita
  para lá.

## Restrições

- Nenhuma instalação global de ferramenta. Usar o Compose vendorizado existente e o mise com escopo
  no projeto.
- Somente leitura contra o cluster em execução. A Lesson 000 não implanta nada e não altera nada.
- Nenhum arquivo BPMN pode ser adicionado a `processes/`; a notação aparece apenas como diagrama
  no documento da lesson.
- Numeração aditiva: a Lesson 000 é inserida sem renumerar as existentes.

## Verificação

- Todos os links relativos dos documentos novos resolvem.
- Toda afirmação `Fato (8.9)` rastreia até uma página de documentação da versão 8.9, com URL que
  retorna 200.
- Toda afirmação `Observado` tem um comando verbatim correspondente em `evidence.md`.
- A evidência foi produzida por uma execução real, não reconstruída.
- `processes/` não contém arquivos novos.
- Nenhum bloco Mermaid quebra a renderização — `docs/validate-mermaid.sh` passa.
- A Lesson 000 permanece `ready`, não `completed`, até a revisão independente.

## Dependências

- ADR-0002, que fixa o Camunda 8.9.x.
- O ambiente local vendorizado em `infra/local/camunda-8.9/`.

## Lesson relacionada

`docs/lessons/000-camunda-bpmn-concepts/lesson.md`, que desbloqueia
`docs/lessons/001-camunda7-8-mental-model/lesson.md`.
