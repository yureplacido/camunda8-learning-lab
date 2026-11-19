package com.camunda8.lab.lifecycleworker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.camunda.client.api.worker.JobClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * A decisão do worker é pura: não depende de cluster, nem de mock de builder. O que se
 * testa aqui é a tese da Lesson 005 — a primeira ativação não completa, a segunda
 * (mesma {@code jobKey}) completa, e {@code retries} não muda essa decisão.
 */
class LifecycleJobWorkerDecisionTest {

    @ParameterizedTest(name = "chaveJaVista={0}, retries={1} -> {2}")
    @CsvSource({
        "false, 3, DEIXAR_EXPIRAR",
        "true, 3, COMPLETAR",
        "true, 2, COMPLETAR"
    })
    @DisplayName("a decisão depende da identidade da chave, não do contador de retries")
    void decisaoDependeDaChaveNaoDosRetries(boolean chaveJaVista, int retries, LifecycleJobWorker.Decisao esperada) {
        LifecycleJobWorker.Plano plano = LifecycleJobWorker.decidir(chaveJaVista, retries);

        assertThat(plano.decisao()).isEqualTo(esperada);
        assertThat(plano.motivo()).contains(String.valueOf(retries));
    }

    @Test
    @DisplayName("a primeira vez que uma chave aparece não é 'já vista'; a segunda é")
    void identidadeDaChave() {
        LifecycleJobWorker worker = new LifecycleJobWorker(mock(JobClient.class));

        assertThat(worker.chaveJaVista(2251799813720346L)).isFalse();
        assertThat(worker.chaveJaVista(2251799813720346L)).isTrue();
        assertThat(worker.chaveJaVista(2251799813720347L)).isFalse();
    }

    @Test
    @DisplayName("duas chaves diferentes não são confundidas")
    void chavesDiferentesSaoIndependentes() {
        LifecycleJobWorker worker = new LifecycleJobWorker(mock(JobClient.class));

        worker.chaveJaVista(1L);
        assertThat(worker.chaveJaVista(2L)).isFalse();
        assertThat(worker.chaveJaVista(1L)).isTrue();
    }
}
