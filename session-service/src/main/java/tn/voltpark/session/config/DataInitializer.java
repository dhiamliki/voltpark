package tn.voltpark.session.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tn.voltpark.session.domain.model.Charger;
import tn.voltpark.session.domain.model.ChargingSession;
import tn.voltpark.session.domain.model.Driver;
import tn.voltpark.session.domain.model.Invoice;
import tn.voltpark.session.domain.model.Station;
import tn.voltpark.session.domain.service.TariffCalculationService;
import tn.voltpark.session.repository.ChargerRepository;
import tn.voltpark.session.repository.ChargingSessionRepository;
import tn.voltpark.session.repository.DriverRepository;
import tn.voltpark.session.repository.InvoiceRepository;
import tn.voltpark.session.repository.StationRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Donnees de demonstration, inserees seulement si les tables sont vides.
 * Les identifiants des bornes sont les memes que dans charger-gateway-service (DataInitializer).
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);
    private static final ZoneId TUNIS = ZoneId.of("Africa/Tunis");

    /** Borne de demonstration : identifiant, puissance (kW), statut. */
    private record ChargerSeed(String id, double powerKw, Charger.ChargerStatus status) {
    }

    /** Station de demonstration : prix en TND/kWh (base, heures pleines, heures creuses) et TND/min d'occupation. */
    private record StationSeed(String name, String address, double latitude, double longitude,
                               String price, String peakPrice, String offPeakPrice, String idleFee,
                               List<ChargerSeed> chargers) {
    }

    /** Session passee : conducteur (index), borne, il y a N jours, heure de debut, duree, energie, part en heures pleines. */
    private record HistorySeed(int driver, String chargerId, int daysAgo, int hour, int minute,
                               int minutes, String energyKwh, String peakShare, boolean paid) {
    }

    private static final Charger.ChargerStatus AVAILABLE = Charger.ChargerStatus.AVAILABLE;
    private static final Charger.ChargerStatus OCCUPIED = Charger.ChargerStatus.OCCUPIED;
    private static final Charger.ChargerStatus FAULTED = Charger.ChargerStatus.FAULTED;
    private static final Charger.ChargerStatus UNAVAILABLE = Charger.ChargerStatus.UNAVAILABLE;

    // CP-TUN-001/002 (Tunis Centre) et CP-TUN-003 (Lac 2) restent disponibles : tests et scripts de demonstration.
    private static final List<StationSeed> STATIONS = List.of(
            new StationSeed("VoltPark Tunis Centre - Avenue Habib Bourguiba", "Avenue Habib Bourguiba, Tunis",
                    36.8002, 10.1865, "0.450", "0.550", "0.350", "0.100", List.of(
                    new ChargerSeed("CP-TUN-001", 50.0, AVAILABLE),
                    new ChargerSeed("CP-TUN-002", 22.0, AVAILABLE),
                    new ChargerSeed("CP-TUN-004", 22.0, OCCUPIED),
                    new ChargerSeed("CP-TUN-005", 7.0, AVAILABLE))),
            new StationSeed("VoltPark Les Berges du Lac 2", "Rue du Lac Biwa, Les Berges du Lac 2, Tunis",
                    36.8432, 10.2725, "0.500", "0.600", "0.400", "0.120", List.of(
                    new ChargerSeed("CP-TUN-003", 150.0, AVAILABLE),
                    new ChargerSeed("CP-TUN-006", 150.0, OCCUPIED),
                    new ChargerSeed("CP-TUN-007", 50.0, AVAILABLE),
                    new ChargerSeed("CP-TUN-008", 22.0, AVAILABLE),
                    new ChargerSeed("CP-TUN-009", 22.0, OCCUPIED),
                    new ChargerSeed("CP-TUN-010", 7.0, AVAILABLE))),
            new StationSeed("VoltPark Berges du Lac 1 - Rue du Lac Turkana", "Rue du Lac Turkana, Les Berges du Lac 1, Tunis",
                    36.8318, 10.2366, "0.480", "0.580", "0.380", "0.100", List.of(
                    new ChargerSeed("CP-TUN-011", 50.0, AVAILABLE),
                    new ChargerSeed("CP-TUN-012", 22.0, AVAILABLE),
                    new ChargerSeed("CP-TUN-013", 22.0, FAULTED))),
            new StationSeed("VoltPark La Marsa Plage", "Avenue Habib Bourguiba, La Marsa",
                    36.8784, 10.3247, "0.480", "0.580", "0.380", "0.100", List.of(
                    new ChargerSeed("CP-TUN-014", 50.0, OCCUPIED),
                    new ChargerSeed("CP-TUN-015", 22.0, AVAILABLE),
                    new ChargerSeed("CP-TUN-016", 7.0, AVAILABLE))),
            new StationSeed("VoltPark Ariana Soghra", "Avenue de l'Environnement, Ariana",
                    36.8665, 10.1647, "0.420", "0.520", "0.320", "0.080", List.of(
                    new ChargerSeed("CP-TUN-017", 22.0, AVAILABLE),
                    new ChargerSeed("CP-TUN-018", 7.0, OCCUPIED))),
            new StationSeed("VoltPark El Menzah 6", "Avenue Othman Ibn Affan, El Menzah 6, Tunis",
                    36.8475, 10.1838, "0.440", "0.540", "0.340", "0.090", List.of(
                    new ChargerSeed("CP-TUN-019", 50.0, AVAILABLE),
                    new ChargerSeed("CP-TUN-020", 22.0, AVAILABLE),
                    new ChargerSeed("CP-TUN-021", 22.0, UNAVAILABLE))),
            new StationSeed("VoltPark Sousse Corniche", "Boulevard Hédi Chaker, Sousse",
                    35.8335, 10.6400, "0.460", "0.560", "0.360", "0.100", List.of(
                    new ChargerSeed("CP-SSE-001", 150.0, AVAILABLE),
                    new ChargerSeed("CP-SSE-002", 50.0, OCCUPIED),
                    new ChargerSeed("CP-SSE-003", 22.0, AVAILABLE),
                    new ChargerSeed("CP-SSE-004", 22.0, AVAILABLE))),
            new StationSeed("VoltPark Sfax Centre", "Avenue Hédi Chaker, Sfax",
                    34.7406, 10.7603, "0.430", "0.530", "0.330", "0.080", List.of(
                    new ChargerSeed("CP-SFX-001", 50.0, AVAILABLE),
                    new ChargerSeed("CP-SFX-002", 22.0, FAULTED),
                    new ChargerSeed("CP-SFX-003", 22.0, AVAILABLE))),
            new StationSeed("VoltPark Nabeul Avenue Habib Thameur", "Avenue Habib Thameur, Nabeul",
                    36.4561, 10.7376, "0.440", "0.540", "0.340", "0.080", List.of(
                    new ChargerSeed("CP-NBL-001", 22.0, AVAILABLE),
                    new ChargerSeed("CP-NBL-002", 7.0, AVAILABLE))),
            new StationSeed("VoltPark Hammamet Yasmine", "Boulevard du 7 Novembre, Yasmine Hammamet",
                    36.3725, 10.5390, "0.470", "0.570", "0.370", "0.100", List.of(
                    new ChargerSeed("CP-HMT-001", 150.0, AVAILABLE),
                    new ChargerSeed("CP-HMT-002", 50.0, AVAILABLE),
                    new ChargerSeed("CP-HMT-003", 22.0, OCCUPIED),
                    new ChargerSeed("CP-HMT-004", 22.0, AVAILABLE),
                    new ChargerSeed("CP-HMT-005", 7.0, AVAILABLE)))
    );

    // conducteurs : index 0 = Rafaa (TAG-001), 1 = Dhia (TAG-002), 2 = Sarra (TAG-003)
    private static final List<HistorySeed> HISTORY = List.of(
            new HistorySeed(1, "CP-TUN-001", 6, 8, 40, 55, "28.400", "0.90", true),
            new HistorySeed(1, "CP-TUN-007", 5, 18, 15, 70, "31.200", "0.55", true),
            new HistorySeed(1, "CP-HMT-001", 4, 11, 5, 30, "46.800", "1.00", true),
            new HistorySeed(1, "CP-TUN-002", 3, 21, 30, 120, "19.600", "0.00", true),
            new HistorySeed(1, "CP-TUN-011", 2, 13, 20, 45, "24.300", "1.00", false),
            new HistorySeed(1, "CP-TUN-019", 1, 7, 10, 60, "22.700", "0.40", false),
            new HistorySeed(0, "CP-TUN-003", 6, 12, 0, 35, "52.100", "1.00", true),
            new HistorySeed(0, "CP-SSE-001", 4, 16, 45, 40, "48.900", "1.00", true),
            new HistorySeed(0, "CP-TUN-015", 2, 20, 30, 90, "17.800", "0.00", false),
            new HistorySeed(0, "CP-TUN-001", 0, 9, 15, 50, "26.500", "1.00", false),
            new HistorySeed(2, "CP-TUN-017", 5, 19, 0, 80, "15.300", "0.30", true),
            new HistorySeed(2, "CP-NBL-001", 3, 10, 30, 75, "18.900", "1.00", true),
            new HistorySeed(2, "CP-TUN-020", 1, 22, 0, 100, "16.400", "0.00", false)
    );

    private final DriverRepository driverRepository;
    private final StationRepository stationRepository;
    private final ChargerRepository chargerRepository;
    private final ChargingSessionRepository sessionRepository;
    private final InvoiceRepository invoiceRepository;
    private final TariffCalculationService tariffCalculationService;

    public DataInitializer(DriverRepository driverRepository,
                           StationRepository stationRepository,
                           ChargerRepository chargerRepository,
                           ChargingSessionRepository sessionRepository,
                           InvoiceRepository invoiceRepository,
                           TariffCalculationService tariffCalculationService) {
        this.driverRepository = driverRepository;
        this.stationRepository = stationRepository;
        this.chargerRepository = chargerRepository;
        this.sessionRepository = sessionRepository;
        this.invoiceRepository = invoiceRepository;
        this.tariffCalculationService = tariffCalculationService;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (driverRepository.count() == 0) {
            driverRepository.save(new Driver("Rafaa Aloui", "rafaa@voltpark.tn", "TAG-001", "+216 20 123 456"));
            driverRepository.save(new Driver("Dhia Mliki", "dhia@voltpark.tn", "TAG-002", "+216 21 654 321"));
            driverRepository.save(new Driver("Sarra Ben Ali", "sarra@voltpark.tn", "TAG-003", "+216 22 111 222"));
            // badge bloque par la passerelle des bornes : demonstration du refus (403)
            driverRepository.save(new Driver("Youssef Trabelsi", "youssef@voltpark.tn", "TAG-BLOCKED", "+216 23 333 444"));
            log.info("Preloaded default drivers in session-service");
        }

        if (stationRepository.count() == 0) {
            for (StationSeed seed : STATIONS) {
                Station station = stationRepository.save(new Station(seed.name(), seed.address(),
                        seed.latitude(), seed.longitude(),
                        new BigDecimal(seed.price()), new BigDecimal(seed.peakPrice()),
                        new BigDecimal(seed.offPeakPrice()), new BigDecimal(seed.idleFee())));
                for (ChargerSeed charger : seed.chargers()) {
                    chargerRepository.save(new Charger(charger.id(), station, Charger.ChargerType.SOAP,
                            charger.status(), charger.powerKw()));
                }
            }
            log.info("Preloaded {} stations and their chargers in session-service", STATIONS.size());
        }

        if (sessionRepository.count() == 0 && invoiceRepository.count() == 0) {
            seedHistory();
        }
    }

    /** Sessions terminees et factures des 7 derniers jours (valeurs fixes, dates relatives a aujourd'hui). */
    private void seedHistory() {
        List<Driver> drivers = driverRepository.findAll(Sort.by("id"));
        Map<String, Charger> chargers = new HashMap<>();
        chargerRepository.findAll().forEach(c -> chargers.put(c.getId(), c));
        LocalDate today = LocalDate.now(TUNIS);

        int index = 0;
        for (HistorySeed seed : HISTORY) {
            index++;
            Charger charger = chargers.get(seed.chargerId());
            if (charger == null || seed.driver() >= drivers.size()) {
                continue;
            }
            Driver driver = drivers.get(seed.driver());
            Instant startedAt = today.minusDays(seed.daysAgo()).atTime(seed.hour(), seed.minute()).atZone(TUNIS).toInstant();
            Instant stoppedAt = startedAt.plus(seed.minutes(), ChronoUnit.MINUTES);
            if (stoppedAt.isAfter(Instant.now())) {
                // seance du jour encore "dans le futur" a l'heure du demarrage : on la place la veille
                startedAt = startedAt.minus(1, ChronoUnit.DAYS);
                stoppedAt = stoppedAt.minus(1, ChronoUnit.DAYS);
            }
            BigDecimal total = new BigDecimal(seed.energyKwh());
            BigDecimal peak = total.multiply(new BigDecimal(seed.peakShare())).setScale(3, RoundingMode.HALF_UP);
            BigDecimal offPeak = total.subtract(peak);

            ChargingSession session = new ChargingSession(driver.getId(), charger.getId(), null);
            // identifiant fixe : memes donnees a chaque initialisation
            session.setId(UUID.nameUUIDFromBytes(("voltpark-demo-session-" + index).getBytes(StandardCharsets.UTF_8)).toString());
            session.setStatus(ChargingSession.SessionStatus.COMPLETED);
            session.setStartedAt(startedAt);
            session.setStoppedAt(stoppedAt);
            session.setChargeCompletedAt(stoppedAt);
            session.setTotalEnergyKwh(total);
            session.setPeakKwh(peak);
            session.setOffPeakKwh(offPeak);
            sessionRepository.save(session);

            TariffCalculationService.CalculationResult calc = tariffCalculationService.calculateInvoice(
                    charger.getStation(), total, peak, offPeak, stoppedAt, stoppedAt);
            Invoice invoice = new Invoice(session.getId(), driver.getId(), calc.totalEnergyKwh(), calc.peakKwh(),
                    calc.offPeakKwh(), calc.peakEnergyCost(), calc.offPeakEnergyCost(), calc.energyCost(),
                    calc.idleDurationMinutes(), calc.idleFee(), calc.totalAmount(), "TND");
            invoice.setIssuedAt(stoppedAt);
            if (seed.paid()) {
                invoice.setStatus(Invoice.InvoiceStatus.PAID);
            }
            invoiceRepository.save(invoice);
        }
        log.info("Preloaded {} completed sessions with invoices (last 7 days)", HISTORY.size());
    }
}
