package com.camunda8.lab.retryworker;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.IntStream;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

/**
 * O BPMN e o worker Java precisam concordar sobre o nome do Job, e nada no compilador liga
 * os dois. Mesma ligação da Lesson 002, com duas@Service Tasks em vez de uma.
 *
 * <p>Aqui a divergência custa mais caro que na 002: um {@code type} errado não deixa o
 * processo parado, ele deixa o Job parado <em>depois</em> de já ter criado incidente. A
 * lição fala de falha e recuperação, e um nome errado se disfarça de bug do conceito.
 */
class BpmnContractTest {

    private static final String BPMN_RELATIVE_PATH =
            "processes/003-retries-incidentes-recuperacao/order-fulfillment.bpmn";

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

    private List<String> bpmnJobTypes() throws Exception {
        NodeList nodes = (NodeList) xpath.evaluate("//*[local-name()='taskDefinition']/@type", bpmn, XPathConstants.NODESET);
        return IntStream.range(0, nodes.getLength())
                .mapToObj(i -> nodes.item(i).getNodeValue())
                .toList();
    }

    private String annotationType(Class<?> workerClass, String methodName, Class<?>... params)
            throws NoSuchMethodException {
        Method method = workerClass.getMethod(methodName, params);
        JobWorker annotation = method.getAnnotation(JobWorker.class);
        assertThat(annotation).isNotNull();
        return annotation.type();
    }

    @Test
    @DisplayName("o BPMN chama exatamente os tipos de Job que os workers assinam")
    void bpmnTaskTypesMatchWorkers() throws Exception {
        assertThat(bpmnJobTypes())
                .containsExactlyInAnyOrder(
                        annotationType(ChargeCardWorker.class, "handleChargeCard", ActivatedJob.class, boolean.class),
                        annotationType(ShipOrderWorker.class, "handleShipOrder", ActivatedJob.class, boolean.class));
    }

    @Test
    @DisplayName("o retries do service task falível é 3, como a lição afirma")
    void retriesIsThreeOnTheFailingTask() throws Exception {
        assertThat(query("//*[local-name()='taskDefinition'][@type='charge-card']/@retries"))
                .isEqualTo("3");
    }

    @Test
    @DisplayName("o worker que falha tem autoComplete desligado")
    void failingWorkerDoesNotAutoComplete() throws NoSuchMethodException {
        Method method = ChargeCardWorker.class.getMethod("handleChargeCard", ActivatedJob.class, boolean.class);
        JobWorker annotation = method.getAnnotation(JobWorker.class);
        assertThat(annotation.autoComplete()).containsExactly(false);
    }

    @Test
    @DisplayName("o worker lê a variável simulateFailure, que é a causa que o operador corrige")
    void readsSimulateFailureVariable() throws NoSuchMethodException {
        Method method = ChargeCardWorker.class.getMethod("handleChargeCard", ActivatedJob.class, boolean.class);
        Variable[] variables = method.getParameters()[1].getAnnotationsByType(Variable.class);
        assertThat(variables).hasSize(1);
        assertThat(variables[0].name()).isEqualTo("simulateFailure");
    }

    @Test
    @DisplayName("o processo tem dois service tasks: o falível e o que prova a conclusão")
    void twoServiceTasks() throws Exception {
        Object count = xpath.evaluate("count(//*[local-name()='serviceTask'])", bpmn, XPathConstants.NUMBER);
        assertThat(((Number) count).intValue()).isEqualTo(2);
    }

    @Test
    @DisplayName("o charge-card vem antes do ship-order, senão a lição não prova recovery")
    void failingTaskPrecedesShipping() throws Exception {
        assertThat(query("//*[local-name()='sequenceFlow'][@sourceRef='ServiceTask_ChargeCard']/@targetRef"))
                .isEqualTo("ServiceTask_ShipOrder");
    }

    @Test
    @DisplayName("o processId é lesson-003-retries-incidentes, como a evidência registra")
    void processIdIsStable() throws Exception {
        assertThat(query("//*[local-name()='process']/@id")).isEqualTo("lesson-003-retries-incidentes");
    }

    @Test
    @DisplayName("o arquivo referenciado pelo teste é o mesmo do versionado no repositório")
    void bpmnIsTheRepoFile() {
        File file = findBpmn().toFile();
        assertThat(file).exists();
        assertThat(file.getPath()).endsWith("003-retries-incidentes-recuperacao" + File.separator + "order-fulfillment.bpmn");
    }
}