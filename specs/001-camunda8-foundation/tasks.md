# Tarefas do SPEC-001

Reduzido à Lesson 001 em 2026-09-29. Nada foi apagado: as tarefas que pertencem a lessons
posteriores estão preservadas em [Adiado](#adiado-não-descartado) abaixo.

## Ambiente

- [x] Fixar a versão do Camunda 8 e registrar a decisão (ADR-0002).
- [x] Vendorizar o Compose oficial *lightweight*, aparado, com script de fetch verificado por
      SHA-256 (`infra/local/fetch-compose.sh`).
- [x] Adicionar política de mise com escopo no projeto, sem instalações globais
      (`.mise.toml`, `infra/local/mise-env.sh`, `infra/local/mise.sh`).
- [x] Manter `.mise.toml` com `[tools]` **vazio** — a lesson não produz código e não precisa de JVM.
- [x] Subir o cluster e verificar que ele fica saudável.
- [x] Documentar como rodar o ambiente (`infra/local/README.md`), distinguindo Self-Managed de
      Camunda 8 Run.

## Pesquisa

- [x] Pesquisar a arquitetura do Camunda 7.
- [x] Pesquisar a arquitetura do Camunda 8 e do Zeebe.
- [x] Escrever `docs/camunda/camunda-7-vs-8.md`, com fato / observado / interpretação rotulados.
- [x] **Verificar toda URL citada por fetch.** A primeira versão da tabela de fontes continha quatro
      URLs inferidas do padrão de páginas vizinhas, e **nenhuma retornava 200**. Reescrita com a
      coluna "Verificado" distinguindo "busquei e li" de "inferi do padrão".

## Evidência

- [x] Verificar B2 — fidelidade de versão contra o pin.
- [x] Verificar B3 — onde o estado de execução vive, e para que serve o banco.
- [x] Verificar B4 — componentes e alcançabilidade de endpoints.
- [x] Registrar evidência verbatim em
      `docs/lessons/001-camunda7-8-mental-model/evidence.md`.
- [x] Corrigir a afirmação falsa "Camunda 8 não tem banco" e tornar B3 com escopo de container.
- [x] Corrigir a afirmação falsa sobre o conteúdo do primary storage
      (`MANIFEST-*` e `zeebe.metadata` não existem).
- [x] Registrar as falhas F1–F5 da coleta, incluindo o `KeyError: 0` e o falso positivo da SPA.

## Conteúdo

- [x] Escrever a lesson (`docs/lessons/001-camunda7-8-mental-model/lesson.md`) nas 14 seções da
      ordem obrigatória.
- [x] Separar o diagrama **conceitual** (caminho comando → efeito colateral) do diagrama de
      **ambiente físico** (containers e volumes), e rotulá-los como respostas a perguntas diferentes.
- [x] Explicar o H2 por seus 7 papéis, sem os dois absolutos.
- [x] Usar a **fronteira transacional ACID** como eixo da comparação C7 → 8.
- [x] Responder as perguntas de entrevista a partir do material do repositório.
- [x] Escrever `docs/camunda/camunda-8-local-components.md` — 5 componentes lógicos, 1 processo,
      2 containers, 2 volumes.

## Diagramas e validação

- [x] Desenhar a vista C4 Context.
- [x] Desenhar a vista C4 Container.
- [x] **Converter os dois diagramas C4 de `C4Context`/`C4Container` para `flowchart`.** A sintaxe C4
      falhava: `references an unknown shape` no Context, `Expecting 'RBRACE', got 'EOF'` no
      Container. Nível arquitetural preservado; notação alterada, e a decisão registrada em
      `docs/architecture/README.md`.
- [x] Mover o H2 para dentro da fronteira do cluster no diagrama L1, onde ele estava desenhado como
      sistema externo — desvio encontrado na revisão semântica.
- [x] Criar `docs/validate-mermaid.sh` e fazê-lo passar.
- [x] Criar `.opencode/agents/diagram-reviewer.md`.
- [x] Traduzir a documentação do PR para PT-BR, preservando termos oficiais em inglês.

## Verificação

- [x] `docs/validate-mermaid.sh` renderiza todos os blocos Mermaid.
- [x] Todos os links relativos resolvem.
- [x] Documentação em PT-BR.
- [x] Status permanece `ready`, nunca `completed`.
- [ ] Revisão independente da lesson.
- [ ] Verificar o ambiente a partir de um checkout limpo, em uma máquina sem nenhuma ferramenta
      Camunda pré-existente.

## Adiado (não descartado)

Exigem lessons que primeiro estabeleçam o modelo de execução, então não fazem parte da Lesson 001.
Ficam aqui para que o rastro fique visível.

- [ ] Adicionar o primeiro processo BPMN.
- [ ] Adicionar o primeiro job worker em Java.
- [ ] Fixar um toolchain Java em `.mise.toml` (primeira lesson que realmente precisa de JVM).
- [ ] Adicionar testes sobre o comportamento do worker.
- [ ] Documentar retries e idempotência em profundidade.
- [ ] Revisar a arquitetura com os revisores de BPMN e de sistemas distribuídos.
- [ ] Rodar uma simulação de interview-lead.
