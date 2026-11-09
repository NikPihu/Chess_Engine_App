package com.gambyte;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point of the Gambyte server.
 *
 * <p>The code is a <em>modular monolith</em>: one deployable, split into feature packages directly
 * under {@code com.gambyte} ({@code user}, {@code game}, {@code engine}, {@code puzzle}, ...). Each
 * feature owns its controllers ({@code api}), business logic ({@code service}), entities ({@code
 * domain}) and repositories ({@code repo}). Features talk to each other only through services and
 * Spring events, never through another feature's repositories; {@code ArchitectureTest} enforces
 * this. See {@code docs/ARCHITECTURE.md} for the big picture.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class GambyteApplication {

  public static void main(String[] args) {
    SpringApplication.run(GambyteApplication.class, args);
  }
}
