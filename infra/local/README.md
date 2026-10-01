# Infraestrutura local

Ambientes locais autocontidos para o laboratório. Nada aqui instala nada globalmente.

## Conteúdo

| Caminho | O que é |
| --- | --- |
| `mise-env.sh` | Exporta `MISE_*` para que todo o estado do mise fique em `<project>/.mise/` |
| `mise.sh` | Wrapper: carrega o env e executa o mise |
| `fetch-compose.sh` | Baixa o Compose oficial do Camunda 8.9 e apara |
| `camunda-8.9/` | O Compose vendorizado e aparado do Orchestration Cluster *lightweight* |

> **`camunda-8.9/` é a distribuição local vendorizada, não código de produção desta lesson.**
> O conteúdo vem do release oficial, verificado por SHA-256, e é reproduzível com
> `fetch-compose.sh`. Ele existe para tornar o ambiente reproduzível — não para ser editado.

## Política de ferramentas: mise, com escopo neste repositório

Objetivo: **nenhuma instalação global.** Toda ferramenta que o laboratório precisar está fixada
dentro deste repositório, para que ele seja reproduzível sem tocar na configuração do host.

```bash
./infra/local/mise.sh install              # instala tudo que está fixado em .mise.toml
./infra/local/mise.sh which java
./infra/local/mise.sh exec -- java -version
```

`mise-env.sh` redireciona:

| Variável | Valor |
| --- | --- |
| `MISE_DATA_DIR` | `<project>/.mise/installs` |
| `MISE_CACHE_DIR` | `<project>/.mise/cache` |
| `MISE_STATE_DIR` | `<project>/.mise/state` |
| `MISE_CONFIG_DIR` | `<project>/.mise/config` |

`.mise/` está no gitignore. **A Lesson 001 não fixa nenhuma ferramenta**, porque não produz código e
não precisa de JVM. `.mise.toml` tem `[tools]` **vazio de propósito**. A primeira lesson que
realmente precisar de Java vai fixá-la em uma versão que o Camunda 8.9 suporta — ver
[ADR-0002](../../docs/adr/0002-camunda-8-version-pin.md).

## Cluster local Camunda 8.9.22

O Compose vendorizado é a distribuição **oficial *lightweight***
(`camunda/camunda:8.9.22` + `camunda/connectors-bundle:8.9.14`), aparado por `fetch-compose.sh`
para remover exemplos BPMN, e2e/Playwright e a stack de gerenciamento.

Este é o ambiente **Camunda 8 Self-Managed via Docker Compose**. Não é Camunda 8 Run — são
produtos diferentes. Ver
[Lesson 001 §2](../docs/lessons/001-camunda7-8-mental-model/lesson.md).

### Subir

```bash
cd infra/local/camunda-8.9
docker compose up -d
docker compose ps
```

### Inspecionar

```bash
curl -s http://localhost:8080/v2/topology
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:9600/actuator/health

# Prova de que o H2 existe e é monitorado como componente de saúde
curl -s http://localhost:9600/actuator/health | grep -o '"database":"[^"]*"'

# Prova de que o rdbms exporter está habilitado
curl -s http://localhost:9600/actuator/exporters

# Onde ficam os dois armazenamentos
docker compose exec -T orchestration ls -la /usr/local/camunda/data/raft-partition/partitions/1/
docker compose exec -T orchestration ls -la /usr/local/camunda/camunda-data/

# Prova de que nenhum banco externo está envolvido
docker compose exec -T orchestration netstat -tn | grep -E ":(5432|3306|1521|27017) " \
  || echo "nenhuma conexão com banco externo"
```

**Atenção ao `grep` do H2.** A conexão externa **não** é a mesma coisa que "não há banco". O H2 é um
arquivo dentro do container, e aparece em `/actuator/health` como `rdbmsStatus`. Um comando que só
procura portas externas produz uma conclusão correta por um motivo errado. Ver
[Lesson 001 §13](../docs/lessons/001-camunda7-8-mental-model/lesson.md).

### Parar e limpar

```bash
cd infra/local/camunda-8.9
docker compose down -v     # -v remove também os dois volumes de dados
```

### Portas

| Porta (host) | Finalidade |
| --- | --- |
| `8080` | Gateway REST, e as três UIs: `/operate`, `/tasklist`, `/admin` |
| `9600` | Actuator |
| `26500` | Gateway gRPC — **é por aqui que o Worker fala** |
| `8086` | Connectors (faz parte do *lightweight* oficial; não usado por este laboratório) |

As portas `26501`/`26502` são deliberadamente **não** publicadas.

## Restrição conhecida

O Compose oficial fixa os nomes de container `orchestration` e `connectors`, e o nome de rede
`camunda`. Duas cópias deste Compose não rodam lado a lado sem editar esses nomes. Mantido sem
modificação de propósito, para que o artefato vendorizado seja reconhecível contra o upstream.

**Achado menor, não corrigido:** a execução de 2026-09-29 deixou uma rede `camunda-89_default` do
mesmo projeto, sem nenhum container ligado. É resíduo de uma execução anterior e não afeta o
ambiente. Registrado em
[evidence §1.5](../docs/lessons/001-camunda7-8-mental-model/evidence.md) em vez de ser limpo, para
não fabricar uma observação.

## Atualizar o Compose vendorizado

```bash
./infra/local/fetch-compose.sh
```

O script fixa o release `docker-compose-8.9`, verifica o SHA-256 do asset, extrai, e mantém apenas
o subconjunto que o laboratório usa. Rode deliberadamente, e releia o diff antes de commitar.

O pin de versão de runtime fica em `camunda-8.9/.env` (`CAMUNDA_VERSION`,
`CAMUNDA_CONNECTORS_VERSION`); a decisão por trás dele está no
[ADR-0002](../../docs/adr/0002-camunda-8-version-pin.md).

Verificado em 2026-09-29: rodar o script de novo reproduz a árvore vendorizada com checksum
idêntico (`103c0f85c4b3ab2928a9cdb6c74e2ad9c00f8b9cc3ff45fc1ae419be279d8546`).

## Para onde foi a evidência

As observações da execução de 2026-09-29 estão em
[evidence da Lesson 001](../docs/lessons/001-camunda7-8-mental-model/evidence.md), e o resumo
estrutural, em
[mapa de componentes](../docs/camunda/camunda-8-local-components.md).
