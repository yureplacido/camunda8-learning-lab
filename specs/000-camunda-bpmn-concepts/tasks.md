# Tarefas do SPEC-000

## Evidência

- [x] Capturar a stack em execução, as versões de imagem e a saúde.
- [x] Capturar `/v2/topology` para separar broker de gateway.
- [x] Capturar `/actuator/partitions` para expor posição do log, snapshot e fase do exporter.
- [x] Amostrar a posição do log ao longo do tempo para mostrar o log avançando.
- [x] Capturar `/actuator/exporters` para mostrar o exporter habilitado.
- [x] Capturar `/actuator/cluster` para mostrar a estratégia de roteamento de partições.
- [x] Capturar os dois volumes de armazenamento para mostrar a separação.
- [x] Confirmar ausência de conexão com banco externo.
- [x] Confirmar zero instâncias de processo pela API de busca v2.
- [x] Registrar a tentativa falha de `/v1/*` e o falso positivo da SPA, para que o método correto
      em v2 fique documentado em vez de ser redescoberto.

## Conteúdo

- [x] Escrever o problema que motiva um workflow engine.
- [x] Definir workflow engine e explicar por que o estado precisa sobreviver ao código.
- [x] Explicar BPMN como padrão OMG, e não como invenção da Camunda.
- [x] Construir a tabela de vocabulário: definition, instance, task, user task, service task, job,
      job worker, variável.
- [x] Escrever explicitamente a cadeia *service task* → Job → *job worker*.
- [x] Explicar o que é Camunda e como 7 e 8 se relacionam como gerações de engine.
- [x] Adicionar a seção "termos que não devem ser confundidos" e o conjunto de perguntas de
      entrevista.
- [x] Encerrar com uma ponte explícita para a Lesson 001.

## Movido para a Lesson 001 na revisão de 2026-09-29

Estas tarefas estavam no escopo do SPEC-000 e foram transferidas, porque dependem de
vocabulário de runtime que a Lesson 000 deliberadamente não introduz.

- [x] Tabela de componentes, com o que cada um faz e o que **não** faz.
- [x] Explicar o log e o que uma posição significa.
- [x] Definir primary versus secondary storage com a terminologia da Camunda.
- [x] Registrar a tensão entre o enquadramento de marketing da Camunda e a realidade observada.

## Registro

- [x] Adicionar o Módulo 0 em `docs/learning-roadmap.md`.
- [x] Adicionar o Módulo 0 em `docs/course-structure.md`.
- [x] Adicionar o item 0 à progressão da base de conhecimento em `docs/camunda/README.md`.
- [x] Criar `docs/modules/00-orientation/README.md`.
- [x] Marcar a Lesson 001 como dependente da Lesson 000.

## Verificação

- [x] Todos os links relativos resolvem.
- [x] Toda afirmação factual carrega o rótulo 8.9 e uma URL verificada.
- [x] Toda afirmação observada tem um comando verbatim em `evidence.md`.
- [x] Nenhum arquivo BPMN adicionado em `processes/`.
- [x] `docs/validate-mermaid.sh` renderiza todos os blocos.
- [x] Documentação em PT-BR, com termos oficiais preservados em inglês.
- [ ] Revisão independente da lesson.
