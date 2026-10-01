# Base de conhecimento Camunda

Este diretório reúne conceitos aprendidos durante o laboratório.

### Progressão sugerida

| # | Conceito | Onde |
| --- | --- | --- |
| 0 | O que é Camunda, e o que suas partes fazem | [Lesson 000](../lessons/000-camunda-bpmn-concepts/lesson.md) |
| 1 | Onde o Camunda 8 executa, e o que é Self-Managed vs Run | [Lesson 001 §1–§13](../lessons/001-camunda7-8-mental-model/lesson.md) |
| 2 | O modelo Camunda 7 → 8 | [Lesson 001 §14](../lessons/001-camunda7-8-mental-model/lesson.md) |
| 3 | Zeebe, arquitetura e execução | lesson futura |
| 4 | Semântica de execução BPMN | lesson futura |
| 5 | Job workers | lesson futura |
| 6 | Variáveis e mapeamentos | lesson futura |
| 7 | Erros, retries e incidentes | lesson futura |
| 8 | Mensagens e correlação | lesson futura |
| 9 | Timers | lesson futura |
| 10 | User tasks e Tasklist | lesson futura |
| 11 | Operate e observabilidade | lesson futura |
| 12 | Connectors | lesson futura |
| 13 | Escala e arquitetura de produção | lesson futura |

**Por que a ordem mudou.** A base de conhecimento começava pela comparação Camunda 7 → 8. Isso
assumia um vocabulário que ela nunca definia — o leitor era introduzido a "fronteira transacional"
antes de saber o que é um Job ou um Worker. Agora o vocabulário vem primeiro, o runtime em segundo, e
a comparação por último, quando existe algo concreto para comparar.

**Item 0 é pré-requisito do item 1**, e não do item 2. A distinção importa: a Lesson 001 não é "a
lesson de comparação", é a lesson de **onde o Camunda 8 executa**, e a comparação é a seção final
dela.

### Mapa de componentes

[camunda-8-local-components.md](camunda-8-local-components.md) — o mapa de componentes do ambiente
local: 5 componentes lógicos, 1 processo, 2 containers, 2 volumes. É a referência para "quantos
componentes o Camunda 8 tem", que depende da granularidade.

### Comparação com Camunda 7

[camunda-7-vs-8.md](camunda-7-vs-8.md) — a comparação, com fato, observado e interpretação separados.

### Regra de citação

Afirmações sensíveis a versão devem citar a documentação oficial do Camunda na nota de estudo
correspondente, com a URL real e verificada. Uma URL que não retorna 200 não está verificada, mesmo
que o padrão da URL pareça correto.
