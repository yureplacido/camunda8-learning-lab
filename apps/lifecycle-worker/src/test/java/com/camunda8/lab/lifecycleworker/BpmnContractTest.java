package com.camunda8.lab.lifecycleworker;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.annotation.JobWorker;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

/**
 * Mesma ligação dos módulos 002 e 003: o compilador não avisa se o tipo no BPMN e o tipo
 * na anotação divergirem. Aqui também se verifica o contrato que a lesson afirma —
 * {@code autoComplete = false} e o prazo curto — lidos da anotação real, não de texto.
 */
class BpmnContractTest {

    private static final String BPMN_RELATIVE_PATH =
            "processes/005-ciclo-vida-job-backpressure/lesson-005-lifecycle-java.bpmn";

    private static Document bpmn;
    private static XPath xpath;

    @BeforeAll
    static void parse() throws Exception {
        Path file = findBpmn();
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        bpmn = factory.newDocumentBuilder().parse(file.toFile());
        xpath = XPathFactory.newInstance().newXPath();
    }

    private static Path findBpmn() {
        Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (dir != null) {
            Path candidate = dir.resolve(BPMN_RELATIVE_PATH);
            if (Files.exists(candidate)) {
                return candidate;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("BPMN não encontrado a partir de " + System.getProperty("user.dir"));
    }

    private String query(String expression) throws Exception {
        return (String) xpath.evaluate(expression, bpmn, XPathConstants.STRING);
    }

    private int count(String expression) throws Exception {
        return ((Number) xpath.evaluate(expression, bpmn, XPathConstants.NUMBER)).intValue();
    }

    private JobWorker annotation() throws NoSuchMethodException {
        Method method = LifecycleJobWorker.class.getMethod("handleLifecycle",
                io.camunda.client.api.response.ActivatedJob.class);
        JobWorker annotation = method.getAnnotation(JobWorker.class);
        assertThat(annotation).isNotNull();
        return annotation;
    }

    @Test
    @DisplayName("o BPMN chama exatamente o tipo de Job que o worker assina")
    void bpmnTaskTypeMatchesWorker() throws Exception {
        assertThat(query("//*[local-name()='taskDefinition']/@type")).isEqualTo(annotation().type());
    }

    @Test
    @DisplayName("o tipo Java é próprio da 005 e não compete com o tipo REST da pasta 62")
    void workerTypeIsIsolated() throws NoSuchMethodException {
        assertThat(annotation().type()).isEqualTo("lesson-005-lifecycle-java");
    }

    @Test
    @DisplayName("o retries do BPMN é 3, preservado através do timeout")
    void retriesIsThree() throws Exception {
        assertThat(query("//*[local-name()='taskDefinition']/@retries")).isEqualTo("3");
    }

    @Test
    @DisplayName("o worker desliga o autoComplete: sem isso não haveria TIMED_OUT")
    void autoCompleteIsDisabled() throws NoSuchMethodException {
        assertThat(annotation().autoComplete()).containsExactly(false);
    }

    @Test
    @DisplayName("o timeout do anúncio é 8000 ms, curto de propósito")
    void timeoutIsEightSeconds() throws NoSuchMethodException {
        assertThat(annotation().timeout()).isEqualTo(8000L);
    }

    @Test
    @DisplayName("o processId é lesson-005-ciclo-vida-job-java, separado do BPMN REST")
    void processIdIsStable() throws Exception {
        assertThat(query("//*[local-name()='process']/@id")).isEqualTo("lesson-005-ciclo-vida-job-java");
    }

    @Test
    @DisplayName("o processo tem um único service task: um Job por instância")
    void singleServiceTask() throws Exception {
        assertThat(count("count(//*[local-name()='serviceTask'])")).isEqualTo(1);
    }

    @Test
    @DisplayName("o arquivo referenciado é o BPMN Java versionado no repositório")
    void bpmnIsTheRepoFile() throws Exception {
        assertThat(findBpmn().getFileName().toString()).isEqualTo("lesson-005-lifecycle-java.bpmn");
    }
}
