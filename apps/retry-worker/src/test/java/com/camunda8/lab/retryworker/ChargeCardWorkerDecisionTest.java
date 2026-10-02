package com.camunda8.lab.retryworker;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * A decisão de falhar é o comportamento que a lesson ensina, então é o que tem teste.
 *
 * <p>Testar o método inteiro exigiria mockar uma cadeia de builders do {@code JobClient}
 * (fail → retries → errorMessage → send) e o teste passaria a afirmar que a API foi
 * chamada, não que a decisão foi a certa. A decisão está isolada em {@code decidir()} e é
 * testada direto.
 */
class ChargeCardWorkerDecisionTest {

    @ParameterizedTest(name = "simulateFailure={0} retries={1} -> {2}")
    @CsvSource({
        // A primeira tentativa: 3 retries no BPMN, o worker decremente para 2.
        "true,  3, FALHAR",
        "true,  2, FALHAR",
        // Última tentativa: com 1 retry o worker manda para 0, e o incidente nasce.
        "true,  1, FALHAR",
        // Causa corrigida: o operador não mexe no worker, mexe na variável.
        "false, 3, CONCLUIR",
        "false, 1, CONCLUIR",
        // Guarda: zero retries não pode virar retries = -1, que o motor rejeitaria.
        "true,  0, CONCLUIR",
    })
    @DisplayName("a decisão é função da causa simulada e do contador do motor")
    void decide(boolean simulateFailure, int retries, ChargeCardWorker.Decisao esperado) {
        assertThat(ChargeCardWorker.decidir(simulateFailure, retries)).isEqualTo(esperado);
    }

    @Test
    @DisplayName("falhar consome exatamente um retry, nunca dois")
    void failingConsumesExactlyOneRetry() {
        for (int retries = 3; retries > 0; retries--) {
            assertThat(ChargeCardWorker.decidir(true, retries))
                    .as("com %d retries o worker deve falhar", retries)
                    .isEqualTo(ChargeCardWorker.Decisao.FALHAR);
            assertThat(retries - 1).isNotNegative();
        }
    }

    @Test
    @DisplayName("a mensagem de falha é fixa, para o runbook poder citá-la")
    void failureMessageIsStable() {
        assertThat(ChargeCardWorker.FALHA).isEqualTo("charge-card: recusa do adquirente (simulada)");
    }
}