package com.camunda8.lab.lifecycleworker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Ponto de entrada do worker mínimo da Lesson 005. */
@SpringBootApplication
public class LifecycleWorkerApplication {

    public static void main(String[] args) {
        SpringApplication.run(LifecycleWorkerApplication.class, args);
    }
}
