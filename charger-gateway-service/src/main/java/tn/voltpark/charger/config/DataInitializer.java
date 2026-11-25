package tn.voltpark.charger.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import tn.voltpark.charger.model.AuthorizedBadge;
import tn.voltpark.charger.model.ChargerStation;
import tn.voltpark.charger.repository.AuthorizedBadgeRepository;
import tn.voltpark.charger.repository.ChargerStationRepository;
import tn.voltpark.charger.ws.ChargerStatus;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Donnees de demonstration, inserees seulement si les tables sont vides.
 * Memes identifiants de bornes et memes statuts que dans session-service (DataInitializer).
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private static final Map<String, ChargerStatus> CHARGERS = new LinkedHashMap<>();

    static {
        // Tunis Centre (CP-TUN-001/002 : tests et scripts de demonstration)
        CHARGERS.put("CP-TUN-001", ChargerStatus.AVAILABLE);
        CHARGERS.put("CP-TUN-002", ChargerStatus.AVAILABLE);
        CHARGERS.put("CP-TUN-004", ChargerStatus.OCCUPIED);
        CHARGERS.put("CP-TUN-005", ChargerStatus.AVAILABLE);
        // Les Berges du Lac 2
        CHARGERS.put("CP-TUN-003", ChargerStatus.AVAILABLE);
        CHARGERS.put("CP-TUN-006", ChargerStatus.OCCUPIED);
        CHARGERS.put("CP-TUN-007", ChargerStatus.AVAILABLE);
        CHARGERS.put("CP-TUN-008", ChargerStatus.AVAILABLE);
        CHARGERS.put("CP-TUN-009", ChargerStatus.OCCUPIED);
        CHARGERS.put("CP-TUN-010", ChargerStatus.AVAILABLE);
        // Berges du Lac 1
        CHARGERS.put("CP-TUN-011", ChargerStatus.AVAILABLE);
        CHARGERS.put("CP-TUN-012", ChargerStatus.AVAILABLE);
        CHARGERS.put("CP-TUN-013", ChargerStatus.FAULTED);
        // La Marsa
        CHARGERS.put("CP-TUN-014", ChargerStatus.OCCUPIED);
        CHARGERS.put("CP-TUN-015", ChargerStatus.AVAILABLE);
        CHARGERS.put("CP-TUN-016", ChargerStatus.AVAILABLE);
        // Ariana
        CHARGERS.put("CP-TUN-017", ChargerStatus.AVAILABLE);
        CHARGERS.put("CP-TUN-018", ChargerStatus.OCCUPIED);
        // El Menzah 6
        CHARGERS.put("CP-TUN-019", ChargerStatus.AVAILABLE);
        CHARGERS.put("CP-TUN-020", ChargerStatus.AVAILABLE);
        CHARGERS.put("CP-TUN-021", ChargerStatus.UNAVAILABLE);
        // Sousse
        CHARGERS.put("CP-SSE-001", ChargerStatus.AVAILABLE);
        CHARGERS.put("CP-SSE-002", ChargerStatus.OCCUPIED);
        CHARGERS.put("CP-SSE-003", ChargerStatus.AVAILABLE);
        CHARGERS.put("CP-SSE-004", ChargerStatus.AVAILABLE);
        // Sfax
        CHARGERS.put("CP-SFX-001", ChargerStatus.AVAILABLE);
        CHARGERS.put("CP-SFX-002", ChargerStatus.FAULTED);
        CHARGERS.put("CP-SFX-003", ChargerStatus.AVAILABLE);
        // Nabeul
        CHARGERS.put("CP-NBL-001", ChargerStatus.AVAILABLE);
        CHARGERS.put("CP-NBL-002", ChargerStatus.AVAILABLE);
        // Hammamet
        CHARGERS.put("CP-HMT-001", ChargerStatus.AVAILABLE);
        CHARGERS.put("CP-HMT-002", ChargerStatus.AVAILABLE);
        CHARGERS.put("CP-HMT-003", ChargerStatus.OCCUPIED);
        CHARGERS.put("CP-HMT-004", ChargerStatus.AVAILABLE);
        CHARGERS.put("CP-HMT-005", ChargerStatus.AVAILABLE);
    }

    private final ChargerStationRepository chargerRepository;
    private final AuthorizedBadgeRepository badgeRepository;

    public DataInitializer(ChargerStationRepository chargerRepository,
                           AuthorizedBadgeRepository badgeRepository) {
        this.chargerRepository = chargerRepository;
        this.badgeRepository = badgeRepository;
    }

    @Override
    public void run(String... args) {
        if (chargerRepository.count() == 0) {
            CHARGERS.forEach((id, status) -> chargerRepository.save(new ChargerStation(id, status)));
            log.info("Preloaded {} chargers into charger-db", CHARGERS.size());
        }

        if (badgeRepository.count() == 0) {
            badgeRepository.save(new AuthorizedBadge("TAG-001", "driver-1", true));
            badgeRepository.save(new AuthorizedBadge("TAG-002", "driver-2", true));
            badgeRepository.save(new AuthorizedBadge("TAG-003", "driver-3", true));
            // badge bloque : demonstration du refus (SOAP Fault BADGE_REFUSED, 403)
            badgeRepository.save(new AuthorizedBadge("TAG-BLOCKED", "driver-4", false));

            log.info("Preloaded default authorized badges into charger-db");
        }
    }
}
