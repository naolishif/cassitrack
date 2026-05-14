package it.unicas.omnimove;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * OMNIMOVE — Multimodal Journey Planner
 *
 * Runs on port 8081. Talks to CASSITRACK (port 8080)
 * via REST API only. No shared database or code imports.
 *
 * Two-server architecture as required by professor:
 *   CASSITRACK :8080 (MAGNI fleet monitoring)
 *   OMNIMOVE   :8081 (passenger journey planning)
 */
@SpringBootApplication
public class OmnimoveApplication {
    public static void main(String[] args) {
        SpringApplication.run(OmnimoveApplication.class, args);
    }
}
