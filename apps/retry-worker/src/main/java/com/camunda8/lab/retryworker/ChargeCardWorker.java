package com.camunda8.lab.retryworker;

import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * O service task falível da Lesson 003.
 *
 * Duas decisões de design que não são detalhe:
 *
 * <b>autoComplete = false.</b> O starter completa o Job sozinho quando o método retorna,
 * porque `JobHandlerInvokingBeans` chama `newCompleteCommand` quando `autoComplete` está
 * ligado. Se este método devolvesse normalmente depois de mandar um FAIL, o log receberia
 * os dois comandos e o Job poderia completar em vez de falhar. nesta lesson quem emite o
 * comando é o assunto, então o worker desliga o automático e decide.
 *
 * <b>O contador é do Job, não do worker.</b> `job.getRetries()` é o valor que o motor
 * gravou no log. O worker só informa o próximo valor; ele não guarda estado entre
 * tentativas, e é por isso que um worker reiniciado continua contando de onde o motor
 * deixou.
 */
@Component
public class ChargeCardWorker {

    private static final Logger log = LoggerFactory.getLogger(ChargeCardWorker.class);

    static final String FALHA = "charge-card: recusa do adquirente (simulada)";

    private final JobClient jobClient;

    public ChargeCardWorker(JobClient jobClient) {
        this.jobClient = jobClient;
    }

    /** O que o worker faz com o Job, separado do comando que ele emite. */
    public enum Decisao {
        FALHAR,
        CONCLUIR
    }

    /**
     * Decisão pura, sem I/O, para o teste cobrir o comportamento sem mock de cadeia de
     * builders.
     *
     * <p>A guarda de {@code retries > 0} não é decoração. Sem ela, um Job com zero
     * retries cairia em {@code retries = -1}, que o motor rejeita, e a falha viraria um
     * erro de comando em vez de uma decisão.
     */
    static Decisao decidir(boolean simulateFailure, int retries) {
        return simulateFailure && retries > 0 ? Decisao.FALHAR : Decisao.CONCLUIR;
    }

    @JobWorker(type = "charge-card", autoComplete = false)
    public void handleChargeCard(ActivatedJob job, @Variable(name = "simulateFailure") boolean simulateFailure) {
        log.info("=== LESSON-003: Job charge-card recebido ===");
        log.info("JobKey: {} ProcessInstanceKey: {}", job.getKey(), job.getProcessInstanceKey());
        log.info("retries que o motor gravou: {} | simulateFailure: {}", job.getRetries(), simulateFailure);

        Decisao decisao = decidir(simulateFailure, job.getRetries());
        if (decisao == Decisao.FALHAR) {
            int restantes = job.getRetries() - 1;
            log.warn("decisao: FALHAR -> commanded retries={}", restantes);
            jobClient.newFailCommand(job)
                    .retries(restantes)
                    .errorMessage(FALHA)
                    .send()
                    .join();
            return;
        }

        log.info("decisao: CONCLUIR");
        jobClient.newCompleteCommand(job)
                .variables(Map.of("cardCharged", true))
                .send()
                .join();
    }
}