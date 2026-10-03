# Plano do SPEC-001

Reduzido à Lesson 001 em 2026-09-29. Os passos 1–8 estão feitos; 9–12 foram movidos para um SPEC
posterior.

## Feito

1. Fixar uma versão suportada do Camunda 8 e registrar a decisão (ADR-0002).
2. Pesquisar a arquitetura do Camunda 7 e do Camunda 8 na documentação oficial, rotulando cada
   afirmação sensível a versão.
3. Construir um ambiente local autocontido: Compose oficial *lightweight* vendorizado e verificado
   por SHA-256, mais mise com escopo no projeto. Nenhuma instalação global.
4. Subir o cluster e registrar evidência verbatim: versão, componentes, endpoints.
5. Verificar a afirmação de persistência inspecionando o sistema em execução, e não a
   documentação — o que **forçou a correção de duas afirmações falsas**.
6. Documentar o modelo C7 → 8, separando fato / observado / interpretação.
7. Desenhar as vistas C4 Context e C4 Container a partir da topologia observada.
8. Responder as perguntas de entrevista apenas com material do repositório.

## Reordenações da revisão de 2026-09-29

A lesson original abria com a comparação C7 → 8. Isso invertia a causalidade — comparava dois
sistemas antes de descrever qualquer um deles. As mudanças:

| Antes | Agora | Motivo |
| --- | --- | --- |
| C7 → 8 na §1 | C7 → 8 na **§14** | Descrever antes de comparar |
| Persistência nas primeiras seções | Persistência na **§6** e **§13** | Só depois dos componentes |
| "database → log" como a mudança | **Fronteira transacional ACID** como a mudança | "Database → log" é consequência, não causa |
| Uma tabela comparativa | Seção com 12 perguntas de entrevista | Uma tabela não é prepare para interview |
| H2 omitido | **Seção 13 inteira**, com 7 papéis | A versão anterior afirmava que não havia banco |
| Diagrama único | **Dois** diagramas: conceitual e ambiente físico | Respondem a perguntas diferentes |
| Em inglês | PT-BR | Regra do repositório |

## Passos 9–12, adiados

9. Criar um processo BPMN mínimo.
10. Implementar o menor worker Java útil.
11. Adicionar testes sobre o comportamento do worker.
12. Documentar a semântica de falha e retry.

Os itens adiados são preservados, não apagados. Eles exigem lessons que primeiro estabeleçam o
modelo de execução que este SPEC verifica.

## Adicionado na revisão

13. Criar `docs/validate-mermaid.sh`, que renderiza todo bloco Mermaid do repositório.
14. Criar `.opencode/agents/diagram-reviewer.md`, que verifica correção arquitetural.
15. Criar `docs/camunda/camunda-8-local-components.md`, o mapa de componentes por granularidade.
16. Converter os diagramas C4 de `C4Context`/`C4Container` para `flowchart`, porque a sintaxe C4
    falhava ao renderizar neste ambiente.
17. Auditar as URLs citadas, o que expôs quatro URLs inventadas na primeira versão desta tabela de
    fontes. A coluna **Verificado** da tabela distingue `fetch 200`, `curl 200`, `busca` e
    `leitura local`. Uma URL não verificada por request fica marcada como tal, e a afirmação
    correspondente é rebaixada de **Fato** para hipótese, ou limitada ao que a leitura sustenta.
    Registrar que `000` do `curl` indica falha de conexão do ambiente, não URL inválida — ver F6 na
    evidence da Lesson 001.
