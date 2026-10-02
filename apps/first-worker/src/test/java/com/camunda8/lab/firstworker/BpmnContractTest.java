package com.camunda8.lab.firstworker;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import java.io.File;
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
 * O BPMN e o worker Java são dois artefatos que precisam concordar sobre o nome
 * do Job. Nada no compilador liga os dois: o Java compila com `say-hello`
 * errado e o BPMN faz deploy com `say-helo` sem reclamar de nada. O processo
 * simplesmente nunca avança e o Job fica parado esperando ativação.
 *
 * Este teste é essa ligação. Ele lê o BPMN versionado no repositório e compara
 * com a anotação real do método, que é a única fonte de verdade do lado Java.
 */
class BpmnContractTest {

    private static final String BPMN_RELATIVE_PATH = "processes/002-first-worker/first-worker.bpmn";

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

    /**
     * O Maven roda com o diretório do módulo, então o BPMN fica três níveis acima.
     * Subir a árvore evita escrever um caminho relativo que quebra quando o teste
     * é disparado de outro lugar.
     */
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

    private String workerJobType() throws NoSuchMethodException {
        Method method = SayHelloWorker.class.getMethod("handleSayHello", ActivatedJob.class, String.class);
        JobWorker annotation = method.getAnnotation(JobWorker.class);
        assertThat(annotation).isNotNull();
        return annotation.type();
    }

    private String workerVariableName() throws NoSuchMethodException {
        Method method = SayHelloWorker.class.getMethod("handleSayHello", ActivatedJob.class, String.class);
        Variable[] variables = method.getParameters()[1].getAnnotationsByType(Variable.class);
        assertThat(variables).hasSize(1);
        return variables[0].name();
    }

    @Test
    @DisplayName("o BPMN chama exatamente o tipo de Job que o worker assina")
    void bpmnTaskTypeMatchesWorker() throws Exception {
        assertThat(query("//*[local-name()='taskDefinition']/@type"))
                .isEqualTo(workerJobType());
    }

    @Test
    @DisplayName("o retries do BPMN é 3, como a lição afirma")
    void retriesIsThree() throws Exception {
        assertThat(query("//*[local-name()='taskDefinition']/@retries")).isEqualTo("3");
    }

    @Test
    @DisplayName("o taskHeader worker.version=v1 viaja no Job")
    void taskHeaderIsPresent() throws Exception {
        assertThat(query("//*[local-name()='header'][@key='worker.version']/@value")).isEqualTo("v1");
    }

    @Test
    @DisplayName("o processId é lesson-002-first-worker, como a evidência registra")
    void processIdIsStable() throws Exception {
        assertThat(query("//*[local-name()='process']/@id")).isEqualTo("lesson-002-first-worker");
    }

    @Test
    @DisplayName("a variável lida pelo worker é a mesma que a coleção envia na criação")
    void workerVariableMatchesCollectionContract() throws Exception {
        assertThat(workerVariableName()).isEqualTo("name");
    }

    @Test
    @DisplayName("o processo tem um único service task: um Job por instância")
    void singleServiceTask() throws Exception {
        Object count = xpath.evaluate("count(//*[local-name()='serviceTask'])", bpmn, XPathConstants.NUMBER);
        assertThat(((Number) count).intValue()).isEqualTo(1);
    }

    @Test
    @DisplayName("o arquivo referenciado pelo teste é o mesmo do versionado no repositório")
    void bpmnIsTheRepoFile() throws Exception {
        File file = findBpmn().toFile();
        assertThat(file).exists();
        assertThat(file.getPath()).endsWith("002-first-worker" + File.separator + "first-worker.bpmn");
    }
}