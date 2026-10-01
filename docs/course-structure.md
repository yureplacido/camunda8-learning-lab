# Estrutura do curso

O laboratório é um caminho de aprendizado completo de **Camunda 8 Developer Lead**.

**Ciclo de vida:** SPEC → LESSON → IMPLEMENTAÇÃO → TESTE/EXPERIMENTO → FALHA → INVESTIGAÇÃO →
CORREÇÃO → ADR → REVISÃO DE ENTREVISTA

As lessons são intencionalmente esqueléticas até serem estudadas. O código é adicionado apenas
quando o conceito correspondente é alcançado.

## Módulos

| # | Módulo | Lessons |
| --- | --- | --- |
| 0 | Orientation: o que é Camunda, o que é BPMN, para que servem as peças | 000 |
| 1 | Foundation: onde o Camunda 8 executa, e o modelo Camunda 7 → 8 | 001–006 |
| 2 | BPMN e execução | 007–011 |
| 3 | Workers com Java/Spring Boot | 012–014 |
| 4 | Sistemas distribuídos e confiabilidade | 015–020 |
| 5 | Integração, mensagens e tarefas humanas | 021–022 |
| 6 | Plataforma e operações | 023–028 |
| 7 | Arquitetura de produção e Developer Lead | 029–032 |
| 8 | Capstone e simulação de entrevista | 033–036 |

O Módulo 0 existe porque o Módulo 1 assumia vocabulário que ele nunca ensinou. Tudo nos Módulos
1–8 depende dele.

## A regra de sequência dentro de uma lesson

Uma lesson de **fundação** segue esta ordem, e a ordem carrega o argumento:

1. **Conceito** — o que é.
2. **O que faz** — a responsabilidade.
3. **O que não faz** — o limite, que é onde a maior parte do erro de entendimento mora.
4. **Relações** — com que conversa, e em que direção.
5. **Evidência** — o que foi observado, ou a documentação que sustenta a afirmação.

Persistência entra **depois** dos componentes, nunca antes. Começar por "onde o estado é salvo" faz
o leitor decorar "o 7 usa banco, o 8 usa log" e continuar incapaz de responder "onde entra a minha
aplicação".

## Regra de granularidade

**Componente lógico ≠ container ≠ processo.** São três perguntas, com três respostas. Uma lesson de
fundação tem de responder as três separadamente, porque conflá-las é o que produz afirmações
verdadeiras e inúteis.

Ver [mapa de componentes](camunda/camunda-8-local-components.md).
