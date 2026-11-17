package com.camunda8.lab.retryworker;

import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * O service task a jusante do que falha.
 *
 * <p>Existe por um motivo pedagógico, não por necessidade: sem ele, a instância terminaria
 * no service task falível e não haveria como mostrar que a recuperação <em>leva a instância
 * até o fim</em>. Um processo que só trava não prova que o recovery funciona.
 *
 * <p>Este worker nunca falha, então pode usar o auto-complete padrão.
 */
@Component
public class ShipOrderWorker {

    private static final Logger log = LoggerFactory.getLogger(ShipOrderWorker.class);

    @JobWorker(type = "ship-order")
    public Map<String, Object> handleShipOrder(
            ActivatedJob job, @Variable(name = "cardCharged") boolean cardCharged) {
        log.info("=== LESSON-003: Job ship-order recebido (cardCharged={}) ===", cardCharged);
        log.info("JobKey: {}", job.getKey());
        return Map.of("orderShipped", true);
    }
}