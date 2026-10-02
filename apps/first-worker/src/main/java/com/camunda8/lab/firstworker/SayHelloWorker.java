package com.camunda8.lab.firstworker;

import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SayHelloWorker {

    private static final Logger log = LoggerFactory.getLogger(SayHelloWorker.class);

    @JobWorker(type = "say-hello")
    public void handleSayHello(ActivatedJob job, @Variable(name = "name") String name) {
        String workerName = name != null && !name.isBlank() ? name : "World";
        log.info("=== LESSON-002: Job recebido ===");
        log.info("JobKey: {}", job.getKey());
        log.info("ProcessInstanceKey: {}", job.getProcessInstanceKey());
        log.info("Type: {}", job.getType());
        log.info("Variável 'name': {}", workerName);
        log.info("Mensagem: Hello, {}!", workerName);
        log.info("=== Job completado ===");
    }
}
