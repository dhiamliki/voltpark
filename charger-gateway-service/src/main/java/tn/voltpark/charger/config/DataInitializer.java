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

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);
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
            chargerRepository.save(new ChargerStation("CP-TUN-001", ChargerStatus.AVAILABLE));
            chargerRepository.save(new ChargerStation("CP-TUN-002", ChargerStatus.AVAILABLE));
            chargerRepository.save(new ChargerStation("CP-TUN-003", ChargerStatus.AVAILABLE));

            ChargerStation occupied = new ChargerStation("CP-TUN-OCCUPIED", ChargerStatus.OCCUPIED);
            occupied.setCurrentTransactionId(9999);
            occupied.setCurrentIdTag("TAG-OCC");
            chargerRepository.save(occupied);

            log.info("Preloaded default chargers into charger-db");
        }

        if (badgeRepository.count() == 0) {
            badgeRepository.save(new AuthorizedBadge("TAG-001", "driver-1", true));
            badgeRepository.save(new AuthorizedBadge("TAG-002", "driver-2", true));
            badgeRepository.save(new AuthorizedBadge("TAG-OCC", "driver-3", true));
            badgeRepository.save(new AuthorizedBadge("TAG-BLOCKED", "driver-4", false));

            log.info("Preloaded default authorized badges into charger-db");
        }
    }
}
