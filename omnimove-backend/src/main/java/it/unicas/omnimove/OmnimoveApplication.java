package it.unicas.omnimove;

import it.unicas.omnimove.service.NetexImportService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;

/**
 * OMNIMOVE — Multimodal Journey Planner
 *
 * Runs on port 8081. Talks to CASSITRACK (port 8080)
 * via REST API only. No shared database or code imports.
 *
 * Two-server architecture as required by professor:
 * CASSITRACK :8080 (MAGNI fleet monitoring)
 * OMNIMOVE   :8081 (passenger journey planning)
 */

@SpringBootApplication
@EnableScheduling
public class OmnimoveApplication {

    // 1. 👈 DICHIARIAMO il servizio come variabile privata
    private final NetexImportService netexImportService;

    // 2. 👈 CREIAMO IL COSTRUTTORE per iniettarlo in OmnimoveApplication
    public OmnimoveApplication(NetexImportService netexImportService) {
        this.netexImportService = netexImportService;
    }

    public static void main(String[] args) {
        SpringApplication.run(OmnimoveApplication.class, args);
    }

    // 3. 👈 Adesso questo metodo funzionerà alla perfezione!
    @EventListener(ApplicationReadyEvent.class)
    public void runOnStartup() {
        System.out.println("Applicazione avviata! Faccio partire la sincronizzazione...");
        try {
            netexImportService.importDataFromCassitrack();
        } catch (Exception e) {
            System.err.println("Errore durante la sincronizzazione all'avvio:");
            e.printStackTrace();
        }
    }
}