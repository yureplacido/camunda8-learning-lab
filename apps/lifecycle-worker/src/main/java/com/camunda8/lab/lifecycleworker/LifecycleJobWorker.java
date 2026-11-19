package com.camunda8.lab.lifecycleworker;

import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import io.camunda.client.annotation.JobWorker;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * O worker mínimo da Lesson 005 — ele existe para provar uma afirmação, não para
 * configurar um cliente.
 *
 * <p><b>O que este worker demonstra.</b> Ativação é um empréstimo com prazo, não uma
 * conclusão. Na primeira vez que vê uma {@code jobKey}, ele apenas registra os dados do
 * empréstimo ({@code jobKey}, {@code deadline}, {@code retries}) e <b>não</b> envia
 * {@code complete}. O prazo expira, o motor devolve o mesmo Job à fila e, na segunda
 * ativação da mesma {@code jobKey}, o worker envia o {@code complete}.
 *
 * <p><b>autoComplete = false é obrigatório aqui.</b> Com o padrão {@code true}, o starter
 * enviaria {@code complete} logo após o retorno do método — inclusive na primeira
 * ativação — e o {@code TIMED_OUT} nunca aconteceria. Quem emite o comando é o worker.
 *
 * <p><b>O que este worker NÃO faz (Lesson 014).</b> Não configura {@code maxJobsActive},
 * {@code pollInterval}, concorrência de handler, graceful shutdown nem métricas. O
 * {@code timeout = 8000} é curto apenas para que o experimento termine em segundos e é
 * uma propriedade do anúncio deste Job, não uma configuração de operação do cliente. O
 * conjunto de chaves é memória do experimento: ele guarda identidade, não estado de
 * negócio, e um worker reiniciado aprenderia a mesma coisa com a {@code jobKey} que o
 * motor devolve.
 */
@Component
public class LifecycleJobWorker {

    private static final Logger log = LoggerFactory.getLogger(LifecycleJobWorker.class);

    /** Tipo próprio para não competir com o tipo REST da pasta 62 da coleção. */
    static final String TIPO = "lesson-005-lifecycle-java";

    /** Em ms, para o {@code TIMED_OUT} acontecer enquanto o experimento está sendo lido. */
    static final long TIMEOUT_MS = 8000L;

    private final JobClient jobClient;
    private final Set<Long> chavesVistas = ConcurrentHashMap.newKeySet();

    public LifecycleJobWorker(JobClient jobClient) {
        this.jobClient = jobClient;
    }

    /** O que o worker faz com o Job, separado do comando que ele emite. */
    public enum Decisao {
        DEIXAR_EXPIRAR,
        COMPLETAR
    }

    /**
     * Decisão pura, sem I/O, para o teste cobrir o comportamento sem mockar a cadeia de
     * builders do {@code complete}.
     *
     * <p>{@code retries} entra na assinatura de propósito: a decisão depende da
     * <b>identidade</b> da chave, não do contador. O timeout não consome retries — um Job
     * com {@code retries = 3} na primeira ativação volta com {@code retries = 3} na
     * segunda. O campo aparece no {@code motivo} para o teste provar isso sem depender de
     * log.
     */
    static Plano decidir(boolean chaveJaVista, int retries) {
        return chaveJaVista
                ? new Plano(Decisao.COMPLETAR, "mesma jobKey voltou; retries preservado=" + retries)
                : new Plano(Decisao.DEIXAR_EXPIRAR, "primeira ativacao; retries=" + retries);
    }

    /** Par de decisão e justificativa observável, sem efeito colateral. */
    record Plano(Decisao decisao, String motivo) {}

    /** Verdadeiro se esta chave já tinha sido ativada antes. */
    boolean chaveJaVista(long jobKey) {
        return !chavesVistas.add(jobKey);
    }

    @JobWorker(type = TIPO, timeout = TIMEOUT_MS, autoComplete = false)
    public void handleLifecycle(ActivatedJob job) {
        boolean jaVista = chaveJaVista(job.getKey());
        Plano plano = decidir(jaVista, job.getRetries());

        log.info("=== LESSON-005 (Java): ACTIVATION ===");
        log.info("jobKey={} processInstanceKey={} worker={}", job.getKey(), job.getProcessInstanceKey(), job.getWorker());
        log.info("deadline={} retries={} chaveJaVista={}", job.getDeadline(), job.getRetries(), jaVista);
        log.info("decisao={} motivo={}", plano.decisao(), plano.motivo());

        if (plano.decisao() == Decisao.DEIXAR_EXPIRAR) {
            log.warn("sem complete: o prazo de {} ms deve expirar e o motor devolver a MESMA jobKey", TIMEOUT_MS);
            return;
        }

        log.info("enviando complete para a jobKey={}", job.getKey());
        jobClient.newCompleteCommand(job).send().join();
        log.info("complete aceito para a jobKey={}", job.getKey());
    }
}
