# Domínio

O laboratório modela um processo simplificado de originação de crédito bancário.

## Fluxo principal

Cliente → Pedido de Crédito → Análise de Crédito → Análise de Fraude → Aprovação → Contrato →
Desembolso

## Propósito

O domínio é intencionalmente simples. O valor dele está em criar problemas de workflow e de
sistemas distribuídos realistas:

- integrações síncronas e assíncronas;
- passos humanos e automatizados;
- retries e falhas;
- correlação;
- idempotência;
- timeouts;
- compensação;
- observabilidade.

O domínio deve permanecer simples o bastante para que os conceitos da Camunda continuem sendo o
foco.
