# ADR-0002 — Pin de versão do Camunda 8

## Status
Aceito

Data: 2026-09-29

## Contexto
O laboratório documenta e exercita uma versão específica do Camunda 8. A plataforma se move rápido o
suficiente para que um curso sem pin se degrada em silêncio: comportamento, configuração e
APIs mudam entre releases minor, e os fatos afirmados no laboratório deixariam de corresponder ao
software que o aluno realmente executa.

Em 2026-09-29, a documentação oficial expõe a **8.9** como versão estável atual, a 8.10 como não
lançada, e 8.6 e anteriores como não mantidas. Passar de 8.9 para 8.10 envolve mudanças já
anunciadas:

- O **Camunda Java Client** substituiu o Zeebe Java Client na 8.8; o Zeebe Java Client é removido na
  8.10. O Camunda Java Client usa REST por padrão, com gRPC configurável.
- As métricas `zeebe.client.worker.job.activated` e `zeebe.client.worker.job.handled` estão
  **depreciadas** e são removidas na 8.10, substituídas por `camunda.client.worker.job.*`.
- A partir do patch **8.9.12**, a Camunda deixou de produzir as imagens Docker `camunda/zeebe`,
  `camunda/operate` e `camunda/tasklist`; passa a ser usada a imagem unificada `camunda/camunda`.
- A partir da 8.9, os Helm charts não implantam mais sub-charts de infraestrutura por padrão, e na
  8.10 esses sub-charts são removidos.

As regras de engenharia do repositório exigem Java 21 ou superior, e os componentes do Orchestration
Cluster do Camunda 8 são documentados como exigindo **OpenJDK 21–25**.

## Decisão
O laboratório fixa o curso em **Camunda 8.9.x**.

- Toda afirmação sensível a versão produzida pelo laboratório é rotulada com a versão da
  documentação contra a qual foi verificada, e esse rótulo é `8.9`, salvo indicação em contrário.
- O ambiente local usado para produzir evidência observada é uma distribuição 8.9.x, iniciada
  explicitamente naquela versão.
- A atualização para a 8.10 está **adiada, não agendada**. Quando acontecer, será um incremento
  deliberado, e não um efeito colateral — e as mudanças incompatíveis acima serão tratadas como
  material didático, não como quebra incidental.

## Consequências

- Os fatos afirmados no laboratório podem ser conferidos contra uma versão de documentação
  específica e atualmente suportada, e o leitor consegue dizer a qual versão cada afirmação pertence.
- A atualização para a 8.10 tem destino definido. Vai precisar de escopo próprio: a troca do Java
  client, as métricas de worker renomeadas e a remoção dos sub-charts de infraestrutura no Helm são
  **três tópicos distintos**, não um.
- Lessons escritas contra a 8.9 precisarão de revisão quando o pin se mover. Esse é um custo
  conhecido e aceito de não se mover antes.
- Qualquer afirmação do laboratório que **não** esteja presa a uma versão deve ser rotulada como
  interpretação, e não apresentada como comportamento do Camunda.

## Nota de verificação

Uma afirmação deste ADR que **não** foi verificada por fetch e foi corrigida: a URL das release
notes foi inicialmente registrada como `docs.camunda.io/docs/release-notes/890/`, que retorna
**404**. A URL real é
`docs.camunda.io/docs/reference/announcements-release-notes/890/890-release-notes`. O padrão de URL
foi inferido de uma página vizinha, e a inferência estava errada — ver
[evidence da Lesson 001 §9, F5](../lessons/001-camunda7-8-mental-model/evidence.md).

Consequência para este ADR: a lista de mudanças de 8.10 acima vem das **release announcements e notas
de release indexadas**, e as afirmações de URL devem ser rechecadas por fetch antes de serem
citadas como verificadas.
