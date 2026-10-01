# ADR-0001 — Princípios do learning lab

## Status
Aceito

## Contexto
O repositório é, ao mesmo tempo, código executável e material de ensino. Misturar essas duas
preocupações sem estrutura tornaria o laboratório mais difícil de evoluir e mais difícil de usar como
preparação para entrevista.

## Decisão
Manter a implementação de caráter production-like em diretórios executáveis, e o material de
aprendizado e arquitetura em `docs/`. Usar ADRs para decisões arquiteturais duráveis, e especificações
para incrementos de aprendizado planejados.

## Consequências
- Conceitos podem ser estudados de forma independente da implementação.
- Trade-offs arquiteturais permanecem documentados.
- O repositório pode crescer como um projeto de referência realista sem virar uma nota de aprendizado
  monolítica.
