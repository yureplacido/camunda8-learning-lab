package com.camunda8.lab.firstworker;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.annotation.JobWorker;
import java.lang.reflect.Method;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * O worker produz log, e a evidência da Lesson 002 cita exatamente essas linhas.
 * Se o teste passasse sem olhar o log, ele não provaria nada do que a lição
 * afirma. Por isso o teste captura os eventos emitidos e exige as mesmas
 * strings — assim evidência e teste não podem divergir em silêncio.
 */
class SayHelloWorkerTest {

    private final SayHelloWorker worker = new SayHelloWorker();
    private final Logger logger = (Logger) LoggerFactory.getLogger(SayHelloWorker.class);

    private ListAppender<ILoggingEvent> appender;
    private Level previousLevel;

    @BeforeEach
    void attachAppender() {
        appender = new ListAppender<>();
        appender.start();
        previousLevel = logger.getLevel();
        logger.setLevel(Level.INFO);
        logger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(appender);
        logger.setLevel(previousLevel);
        appender.stop();
    }

    private ActivatedJob job() {
        ActivatedJob activatedJob = org.mockito.Mockito.mock(ActivatedJob.class);
        org.mockito.Mockito.when(activatedJob.getKey()).thenReturn(2251799813758982L);
        org.mockito.Mockito.when(activatedJob.getProcessInstanceKey()).thenReturn(2251799813758981L);
        org.mockito.Mockito.when(activatedJob.getType()).thenReturn("say-hello");
        return activatedJob;
    }

    private String output() {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage)
                .reduce("", (a, b) -> a + b + "\n");
    }

    @Test
    @DisplayName("o Job é completado automaticamente ao voltar do método sem exceção")
    void completesWithoutThrowing() {
        worker.handleSayHello(job(), "Mundo");

        assertThat(output())
                .contains("=== LESSON-002: Job recebido ===")
                .contains("Type: say-hello")
                .contains("=== Job completado ===");
    }

    @Test
    @DisplayName("a variável name é lida e aparece na mensagem")
    void logsVariable() {
        worker.handleSayHello(job(), "Mundo");

        assertThat(output())
                .contains("Variável 'name': Mundo")
                .contains("Mensagem: Hello, Mundo!");
    }

    @Test
    @DisplayName("name ausente não derruba o Job: o worker usa World")
    void fallsBackWhenVariableMissing() {
        worker.handleSayHello(job(), null);

        assertThat(output())
                .contains("Variável 'name': World")
                .contains("Mensagem: Hello, World!");
    }

    @Test
    @DisplayName("name em branco também cai no fallback")
    void fallsBackWhenVariableBlank() {
        worker.handleSayHello(job(), "   ");

        assertThat(output()).contains("Mensagem: Hello, World!");
    }

    @Test
    @DisplayName("o tipo do Job vem da anotação, não de uma constante solta")
    void jobTypeComesFromAnnotation() throws NoSuchMethodException {
        Method method = SayHelloWorker.class.getMethod("handleSayHello", ActivatedJob.class, String.class);
        JobWorker annotation = method.getAnnotation(JobWorker.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.type()).isEqualTo("say-hello");
    }
}