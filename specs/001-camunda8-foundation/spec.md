# SPEC-001 — Fundação do Camunda 8

Status: aceito
Data: 2026-09-29

> **Registro de mudança de escopo.** Este SPEC originalmente descrevia uma fundação que incluía
> processos BPMN, um worker Java, testes e semântica de retry. Foi **reduzido** ao que a Lesson 001
> de fato entrega. Os resultados removidos não foram descartados — estão preservados em
> [Escopo adiado](#escopo-adiado-não-descartado) abaixo. Um SPEC futuro vai reclamá-los quando uma
> lesson estiver pronta para implementá-los.

## Objetivo

Construir o Camunda 8 mentalmente, componente por componente, **antes** de falar de persistência, e
registrar evidência verificada de como um Orchestration Cluster 8.9 real guarda estado.

## Resultados

Ao final, o aluno é capaz de:

- **Nomear** cada componente lógico do Orchestration Cluster, dizer o que ele faz e o que **não**
  faz, e apontar o comando que comprova cada afirmação.
- **Desenhar** o caminho de um comando da aplicação até o efeito colateral em um sistema externo,
  sem confundir componente lógico com container.
- **Distinguir** Camunda 8 Self-Managed de Camunda 8 Run, e dizer por que a distinção importa.
- **Localizar** o estado de execução, o Job e o Worker no runtime, e explicar por que o Job sobrevive
  a um crash.
- **Distinguir** componente lógico, processo e container, e responder "quantos componentes o Camunda
  8 tem" para cada granularidade.
- **Explicar** o papel do H2 com precisão: ele existe, é *secondary storage*, é reconstruível, e não
  é o estado de execução.
- **Explicar** a mudança Camunda 7 → 8 pela **perda da fronteira transacional ACID compartilhada**,
  e não por "database virou log".
- **Separar** fato, observação e interpretação, e recusar uma afirmação que o sistema em execução
  contradiga.
- **Fornecer** um ambiente local reproduzível e autocontido, fixado no Camunda 8.9.x, sem nenhuma
  instalação global de ferramenta.

## Estrutura obrigatória da lesson

A ordem é o argumento, e não é negociável:

| # | Seção | Pergunta que responde |
| --- | --- | --- |
| 1 | BPMN no contexto do runtime | O diagrama vira o quê? |
| 2 | Camunda 8 Self-Managed | O que é isso que estamos rodando? |
| 3 | Zeebe | O que é o Zeebe? |
| 4 | Gateway | Qual a responsabilidade do Gateway? |
| 5 | Broker | Qual a responsabilidade do Broker? |
| 6 | Estado de execução | O que realmente é o "estado"? |
| 7 | Job | Onde o Job vive? |
| 8 | Worker | Quem executa a lógica? |
| 9 | Aplicação cliente | Como minha aplicação conversa com isso? |
| 10 | Componentes operacionais e UI | O que é Operate, Tasklist, Admin? |
| 11 | Ambiente local real | O que está rodando aqui? |
| 12 | Docker Compose | Como isso sobe? |
| 13 | Onde entra o H2? | Por que há um banco, e o que ele é? |
| 14 | O modelo Camunda 7 → 8 | O que mudou conceitualmente? |

**Por que a ordem importa.** Persistência entra na seção 6, depois dos componentes das seções 3 a 5.
Começar por "onde o estado é salvo" faz o leitor decorar "o 7 usa banco, o 8 usa log" e continuar
incapaz de responder "onde entra a minha aplicação". A comparação C7 → 8 vem por último, na
seção 14, quando existe algo concreto para comparar.

**Por que a comparação é a seção 14, e não a seção 1.** A Lesson 001 original abria com C7 → 8, e
isso invertia a causalidade: comparava dois sistemas antes de descrever qualquer um deles. A
comparação agora fecha, e sua **tese** é a perda da fronteira transacional ACID — "database virou
log" é consequência visível, não causa.

## Fora de escopo

| Excluído | Motivo |
| --- | --- |
| Processos BPMN | Requer uma lesson de modelagem antes; sem valor aqui |
| Workers com Java / Spring Boot | Fixaria um toolchain de JVM sem retorno neste estágio |
| Retry, idempotência, semântica de job em profundidade | Dependem do modelo de fronteira; lesson posterior |
| FEEL, DMN, correlação de mensagens, Kafka | Lessons separadas, modelos mentais separados |
| Kubernetes / topologia de produção | Prematuro; o Compose local basta para verificar o modelo |
| Stack de observabilidade | O Compose *lightweight* já expõe o actuator |

## Critérios de aceitação

- [x] Existe uma nota de arquitetura Camunda 7 → 8.
- [x] Um fluxo de execução mínimo está documentado — **nesta lesson, o próprio cluster**: broker,
      gateway, layout de armazenamento e endpoints expostos, todos observados.
- [x] Existem diagramas de arquitetura: C4 Context e C4 Container, ambos renderizando.
- [x] O ambiente local é autocontido: nenhuma instalação global de pacote.
- [x] O escopo deste SPEC corresponde ao escopo da Lesson 001.

- [x] Afirmações sensíveis a versão têm fonte **antes** de serem tratadas como fato (ADR-0002).
- [x] Toda URL citada foi verificada por fetch com retorno 200.
- [x] Evidência observada é registrada **verbatim**, não resumida de memória.
- [x] Uma afirmação que o sistema em execução contradisse foi corrigida no registro.
- [x] `docs/validate-mermaid.sh` renderiza todos os blocos Mermaid do repositório.
- [x] Existe um revisor de diagramas que verifica correção arquitetural, não só sintaxe.
- [x] A documentação do projeto está em PT-BR, preservando termos oficiais em inglês.
- [x] Perguntas de entrevista podem ser respondidas com o material do repositório.

## Divergência deliberada do ambiente real

A aceitação original previa "Camunda 7 → 8" como o tema central da Lesson 001. A execução revelou
que a primeira versão do material continha **duas afirmações falsas**, ambas registradas e corrigidas:

| Afirmação anterior | Refutada por | Correção |
| --- | --- | --- |
| "Camunda 8 não usa banco de dados" | `rdbmsStatus.database: "H2"` no actuator, e `h2db.mv.db` em disco | O H2 existe e é *secondary storage*; o estado de execução está no log |
| Primary storage contém `MANIFEST-*` e `zeebe.metadata` | `ls` do diretório: nenhum dos dois existe | Árvore real: `*.log`, `snapshots/`, `runtime/*.sst` |

A segunda refutação é a mais instructive: a afirmação veio de **memória de documentação**, não de
observação. Daí a regra que passa a valer no repositório: nenhuma afirmação sobre nome de arquivo,
caminho interno ou conteúdo de volume sem um `ls`/`find` na evidência.

## Escopo adiado (não descartado)

Resultados válidos de uma fundação Camunda 8, adiados para um SPEC futuro:

- Definir o relacionamento entre processo BPMN, instância, job e job worker.
- Identificar quais preocupações pertencem ao workflow engine e quais aos serviços de aplicação.
- Adicionar um processo BPMN mínimo e o menor worker Java útil.
- Adicionar testes significativos sobre o comportamento do worker.
- Documentar em profundidade a semântica de falha e retry.

## Dependências

- SPEC-000, que estabelece o vocabulário consumido por esta lesson.
- ADR-0002, que fixa o Camunda 8.9.x.
- O ambiente local vendorizado em `infra/local/camunda-8.9/`.
