# LESSON-NNN — Título

Status: skeletal

> Idioma: **PT-BR**. Termos técnicos e nomes de API permanecem em inglês, porque é assim que
> aparecem na documentação, nos logs e no código. Ver [AGENTS.md](../../AGENTS.md).
>
> Os links relativos deste template são escritos como se o arquivo estivesse em
> `docs/lessons/NNN-slug/`, que é onde a lesson vai ficar. Ao copiar, ajuste a profundidade.
>
> A distinção entre **Fato** (documentação oficial, com a versão e a URL verificada), **Observado**
> (saída real de execução) e **Interpretação** (raciocínio do autor) não é opcional: é o
> principal mecanismo de proteção deste laboratório contra afirmações inventadas.

## Como ler esta lesson

O que a lesson faz, em uma frase, e o que ela explicitamente **não** faz.

## Objetivo

O que o aprendiz deve compreender ao final. Se isso não puder ser verificado por uma pergunta de
entrevista, o objetivo está mal escrito.

## Contexto

Por que esse conceito existe, e qual problema anterior ele resolve. Sem isto, o leitor decora
termos sem saber o que eles compram.

## Conceito

O conteúdo, em ordem causal: problema → categoria → notação → vocabulário → forma. Não em ordem de
dicionário.

Para lessons de componentes, use a estrutura fixa abaixo — ela é o que impede a confusão mais
comum entre componente lógico e container:

```markdown
### O que é
### Responsabilidade
### O que NÃO faz
### Como se relaciona
### Evidência
```

## Modelo mental

Para cada termo, a pergunta que um candidato forte faz: *"o que quebraria sem isto?"*. Se a resposta
for "nada", o termo não entrou na lesson.

## Camunda 7 → 8

Onde a distinção **importa** e muda decisão de arquitetura. Se a distinção não for relevante para
esta lesson, diga isso explicitamente e registre para onde ela foi adiada — não deixe o leitor
assumindo que a comparação foi esquecida.

Para a fronteira transacional, o eixo obrigatório:

> **O que quebra é a fronteira transacional ACID compartilhada entre aplicação e engine.** O
> "banco virou log" é consequência, não causa.

## Onde esta lesson termina — e o que falta para responder

A fronteira explícita com a lesson seguinte. Ensinar aonde parar é tão importante quanto ensinar o
que ensinar.

## O que esta lesson deliberadamente NÃO ensina

Lista de exclusões. É o que mantém o escopo de uma lesson por PR.

## Exemplo mínimo

O menor exemplo executável que demonstra o conceito. Se a lesson não tiver exemplo, o leitor não tem
nada para queimar as mãos.

## Implementação

O que foi construído, e por quê. Nenhuma decisão aparece aqui sem a razão que a justifica.

## Execução

Como rodar, com o comando exato. Saída real vai para [evidence.md](evidence.md), não para aqui.

## Falha / investigação / correção

O caminho de erro é parte do aprendizado. Registre o que **parecia** verdade, o comando que
refutou, e a correção. Um beco sem saída registrado vale mais que um caminho feliz documentado.

## Implicações de arquitetura

Trade-offs e consequências. Se não há trade-off, não há implicação de arquitetura.

## Perguntas de entrevista

Pergunta, resposta, e o teste que sustentaria uma resposta melhor. A pergunta "por quê" é mais
importante que a resposta.

## Evidência

Links para [evidence.md](evidence.md), SPEC, ADR e lessons vizinhas. Regra: **só entra aqui o que foi
observado em execução real.** Se não foi executado, não está.

## Diagram Review

Checklist de [`.opencode/agents/diagram-reviewer.md`](../../../.opencode/agents/diagram-reviewer.md),
executado por renderização e por semântica:

- [ ] Sintaxe Mermaid validada por `docs/validate-mermaid.sh`
- [ ] Todos os diagramas renderizam
- [ ] Nível arquitetural correto — diagrama de notação BPMN não é C4; C4 L1 não mostra containers
- [ ] Componente lógico e container físico não estão misturados
- [ ] Todos os nós têm responsabilidade definida
- [ ] Relações válidas
- [ ] Direção das relações correta
- [ ] Sem nós órfãos
- [ ] Sem componentes não explicados
- [ ] Sem componente fictício — nenhum que não esteja no `docker compose ps` real
- [ ] Sem contradição com o texto da lesson
- [ ] Sem contradição com outros diagramas
- [ ] Alegações sensíveis a versão verificadas contra a documentação da versão-alvo
- [ ] Rótulos e documentação em PT-BR

## Critérios de conclusão

- [ ] Conceito entendido
- [ ] Modelo mental explicado
- [ ] Distinção relevante C7 → 8 documentada
- [ ] Escopo definido por SPEC
- [ ] Experimento implementado quando aplicável
- [ ] Testes executados — ou `N/A` **com a razão escrita**; nunca omitir nem inventar
- [ ] Caminho de falha investigado quando relevante
- [ ] Achados documentados a partir de execução real
- [ ] Implicações de arquitetura documentadas
- [ ] Revisão de entrevista concluída
- [ ] Revisão independente concluída

## O que ainda não é verdade sobre esta lesson

O gate que ainda falta, e as perguntas abertas honestas para o revisor. Uma lesson `ready` declara
isto; uma lesson `completed` não tem esta seção, porque passou pelo gate.

---

## Checklist do autor, antes de pedir revisão

1. Toda afirmação versionada tem URL verificada, ou está marcada como não verificada.
2. Todo caminho, nome de arquivo, porta e comando citado saiu de um comando real, não de memória.
3. Toda conclusão decorre de evidência, não de analogia com Camunda 7.
4. Nenhum componente foi contado por nome de componente lógico.
5. Nenhuma distinção foi feita entre Camunda 7 e Camunda 8 como se fossem intercambiáveis.
