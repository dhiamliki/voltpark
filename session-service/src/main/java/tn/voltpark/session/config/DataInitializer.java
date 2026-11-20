package tn.voltpark.session.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import tn.voltpark.session.domain.model.Charger;
import tn.voltpark.session.domain.model.Driver;
import tn.voltpark.session.domain.model.Station;
import tn.voltpark.session.repository.ChargerRepository;
import tn.voltpark.session.repository.DriverRepository;
import tn.voltpark.session.repository.StationRepository;

import java.math.BigDecimal;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final DriverRepository driverRepository;
    private final StationRepository stationRepository;
    private final ChargerRepository chargerRepository;

    public DataInitializer(DriverRepository driverRepository,
                           StationRepository stationRepository,
                           ChargerRepository chargerRepository) {
        this.driverRepository = driverRepository;
        this.stationRepository = stationRepository;
        this.chargerRepository = chargerRepository;
    }

    @Override
    public void run(String... args) {
        if (driverRepository.count() == 0) {
            driverRepository.save(new Driver("Rafaa Aloui", "rafaa@voltpark.tn", "TAG-001", "+216 20 123 456"));
            driverRepository.save(new Driver("Dhia Mliki", "dhia@voltpark.tn", "TAG-002", "+216 21 654 321"));
            driverRepository.save(new Driver("Test Occupied Driver", "occ@voltpark.tn", "TAG-OCC", "+216 22 111 222"));
            driverRepository.save(new Driver("Blocked User", "blocked@voltpark.tn", "TAG-BLOCKED", "+216 23 333 444"));
            log.info("Preloaded default drivers in session-service");
        }

        if (stationRepository.count() == 0) {
            // Station Tunis Centre
            Station tunisStation = new Station(
                    "VoltPark Tunis Centre - Avenue Habib Bourguiba",
                    "Avenue Habib Bourguiba, Tunis",
                    36.8002, 10.1865,
                    new BigDecimal("0.450"), // 0.450 TND / kWh
                    new BigDecimal("0.550"), // 0.550 TND / kWh heures pleines
                    new BigDecimal("0.350"), // 0.350 TND / kWh heures creuses
                    new BigDecimal("0.100")  // 0.100 TND / min frais d'occupation
            );
            Station savedTunis = stationRepository.save(tunisStation);

            // Station Lac 2
            Station lacStation = new Station(
                    "VoltPark Les Berges du Lac 2",
                    "Rue du Lac Biwa, Les Berges du Lac 2, Tunis",
                    36.8378, 10.2395,
                    new BigDecimal("0.500"),
                    new BigDecimal("0.600"),
                    new BigDecimal("0.400"),
                    new BigDecimal("0.120")
            );
            Station savedLac = stationRepository.save(lacStation);

            // Bornes
            chargerRepository.save(new Charger("CP-TUN-001", savedTunis, Charger.ChargerType.SOAP, Charger.ChargerStatus.AVAILABLE, 50.0));
            chargerRepository.save(new Charger("CP-TUN-002", savedTunis, Charger.ChargerType.SOAP, Charger.ChargerStatus.AVAILABLE, 22.0));
            chargerRepository.save(new Charger("CP-TUN-003", savedLac, Charger.ChargerType.SOAP, Charger.ChargerStatus.AVAILABLE, 150.0));

            log.info("Preloaded default stations and chargers in session-service");
        }
    }
}
