package tn.voltpark.session.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tn.voltpark.session.domain.model.Charger;
import tn.voltpark.session.domain.model.ChargingSession;
import tn.voltpark.session.domain.model.Driver;
import tn.voltpark.session.domain.model.Invoice;
import tn.voltpark.session.domain.model.Reservation;
import tn.voltpark.session.domain.model.Station;
import tn.voltpark.session.domain.service.TariffCalculationService;
import tn.voltpark.session.repository.ChargerRepository;
import tn.voltpark.session.repository.ChargingSessionRepository;
import tn.voltpark.session.repository.DriverRepository;
import tn.voltpark.session.repository.InvoiceRepository;
import tn.voltpark.session.repository.ReservationRepository;
import tn.voltpark.session.repository.StationRepository;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
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
import java.util.Objects;
import java.util.UUID;

/**
 * Donnees de demonstration, inserees seulement si les tables sont vides.
 * Stations et bornes : fichier partage data/stations-tn.json (bornes reelles, Open Charge Map),
 * lu aussi par charger-gateway-service avec les memes regles d'identifiants et de statuts.
 * Simule : statut des bornes et prix.
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);
    private static final ZoneId TUNIS = ZoneId.of("Africa/Tunis");
    static final String STATIONS_FILE = "data/stations-tn.json";

    /** Station du fichier partage (champs utilises ici). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record StationData(long ocmId, String name, String address, String town, double latitude, double longitude,
                       String operator, String corridor, List<ConnectionData> connections) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ConnectionData(String type, Double powerKw, int quantity) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record StationsFile(List<StationData> stations) {
    }

    /** Prix simules en TND/kWh (base, heures pleines, heures creuses) et TND/min d'occupation. */
    private record Prices(String base, String peak, String offPeak, String idleFee) {
    }

    /** Session passee : conducteur (index), borne, il y a N jours, heure de debut, duree, energie, part en heures pleines. */
    private record HistorySeed(int driver, String chargerId, int daysAgo, int hour, int minute,
                               int minutes, String energyKwh, String peakShare, boolean paid) {
    }

    // conducteurs : index 0 = Rafaa (TAG-001), 1 = Dhia (TAG-002), 2 = Sarra (TAG-003)
    private static final List<HistorySeed> HISTORY = List.of(
            new HistorySeed(1, "CP-460375-1", 6, 8, 40, 55, "28.400", "0.90", true),   // Shell Sidi Khelifa (A1)
            new HistorySeed(1, "CP-175677-2", 5, 18, 15, 70, "31.200", "0.55", true),  // TotalEnergies L'Aouina
            new HistorySeed(1, "CP-175675-1", 4, 11, 5, 30, "23.900", "1.00", true),   // TotalEnergies Grombalia (A1)
            new HistorySeed(1, "CP-175677-1", 3, 21, 30, 120, "12.800", "0.00", true), // TotalEnergies L'Aouina
            new HistorySeed(1, "CP-203502-1", 2, 13, 20, 45, "24.300", "1.00", false), // Shell Grombalia (A1)
            new HistorySeed(1, "CP-305758-1", 1, 7, 10, 60, "20.700", "0.40", false),  // Station Agil
            new HistorySeed(0, "CP-175672-3", 6, 12, 0, 35, "52.100", "1.00", true),   // El Jem (A1)
            new HistorySeed(0, "CP-175673-3", 4, 16, 45, 40, "31.500", "1.00", true),  // Borjine (A1)
            new HistorySeed(0, "CP-305758-1", 2, 20, 30, 90, "17.800", "0.00", false), // Station Agil
            new HistorySeed(0, "CP-460463-1", 0, 9, 15, 50, "17.400", "1.00", false),  // Shell Bir El Kassaa (A1)
            new HistorySeed(2, "CP-175670-1", 5, 19, 0, 80, "15.300", "0.30", true),   // TotalEnergies RN1 Sfax
            new HistorySeed(2, "CP-189069-1", 3, 10, 30, 75, "18.900", "1.00", true),  // TotalEnergies Sousse
            new HistorySeed(2, "CP-460271-1", 1, 22, 0, 100, "16.400", "0.00", false)  // Municipalite de Sfax
    );

    private final DriverRepository driverRepository;
    private final StationRepository stationRepository;
    private final ChargerRepository chargerRepository;
    private final ChargingSessionRepository sessionRepository;
    private final InvoiceRepository invoiceRepository;
    private final ReservationRepository reservationRepository;
    private final TariffCalculationService tariffCalculationService;

    public DataInitializer(DriverRepository driverRepository,
                           StationRepository stationRepository,
                           ChargerRepository chargerRepository,
                           ChargingSessionRepository sessionRepository,
                           InvoiceRepository invoiceRepository,
                           ReservationRepository reservationRepository,
                           TariffCalculationService tariffCalculationService) {
        this.driverRepository = driverRepository;
        this.stationRepository = stationRepository;
        this.chargerRepository = chargerRepository;
        this.sessionRepository = sessionRepository;
        this.invoiceRepository = invoiceRepository;
        this.reservationRepository = reservationRepository;
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
            seedStations();
        }

        if (sessionRepository.count() == 0 && invoiceRepository.count() == 0 && reservationRepository.count() == 0) {
            seedHistory();
            seedUpcomingReservations();
        }
    }

    /** Stations reelles et leurs bornes : une borne par point de charge (quantite des connecteurs). */
    private void seedStations() {
        List<StationData> stations = readStations();
        int chargerCount = 0;
        for (StationData data : stations) {
            Prices prices = pricesFor(data);
            Station station = new Station(data.name(), addressOf(data), data.latitude(), data.longitude(),
                    new BigDecimal(prices.base()), new BigDecimal(prices.peak()),
                    new BigDecimal(prices.offPeak()), new BigDecimal(prices.idleFee()));
            station.setCode(stationCode(data.ocmId()));
            station.setOperator(data.operator());
            station.setCorridor(data.corridor());
            station = stationRepository.save(station);

            int index = 0;
            for (ConnectionData connection : data.connections()) {
                for (int i = 0; i < connection.quantity(); i++) {
                    index++;
                    String id = chargerId(data.ocmId(), index);
                    Charger charger = new Charger(id, station, Charger.ChargerType.SOAP,
                            simulatedStatus(id, index), connection.powerKw());
                    charger.setConnectorType(connection.type());
                    chargerRepository.save(charger);
                    chargerCount++;
                }
            }
        }
        log.info("Preloaded {} stations (Open Charge Map) and {} chargers in session-service", stations.size(), chargerCount);
    }

    static List<StationData> readStations() {
        try (InputStream in = new ClassPathResource(STATIONS_FILE).getInputStream()) {
            return new ObjectMapper().readValue(in, StationsFile.class).stations();
        } catch (IOException e) {
            throw new UncheckedIOException("Lecture impossible de " + STATIONS_FILE, e);
        }
    }

    static String stationCode(long ocmId) {
        return "ST-OCM-" + ocmId;
    }

    /** Identifiant de borne : CP-(id Open Charge Map)-(numero dans la station, a partir de 1). */
    static String chargerId(long ocmId, int index) {
        return "CP-" + ocmId + "-" + index;
    }

    /**
     * Statut simule, deterministe (meme regle dans charger-gateway-service) : la premiere borne de chaque
     * station est libre ; pour les autres, le hashCode de l'identifiant donne 6 chances sur 10 d'etre libre,
     * 3 d'etre occupee et 1 d'etre hors service.
     */
    static Charger.ChargerStatus simulatedStatus(String chargerId, int index) {
        if (index == 1) {
            return Charger.ChargerStatus.AVAILABLE;
        }
        int bucket = Math.floorMod(chargerId.hashCode(), 10);
        if (bucket <= 5) {
            return Charger.ChargerStatus.AVAILABLE;
        }
        return bucket <= 8 ? Charger.ChargerStatus.OCCUPIED : Charger.ChargerStatus.FAULTED;
    }

    /** Prix simules selon la puissance maximale de la station : normale (jusqu'a 22 kW), rapide (jusqu'a 50 kW), ultra-rapide. */
    private static Prices pricesFor(StationData data) {
        double maxPower = data.connections().stream()
                .map(ConnectionData::powerKw).filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue).max().orElse(0);
        if (maxPower <= 22) {
            return new Prices("0.400", "0.480", "0.320", "0.050");
        }
        if (maxPower <= 50) {
            return new Prices("0.500", "0.600", "0.400", "0.100");
        }
        return new Prices("0.600", "0.720", "0.480", "0.150");
    }

    private static String addressOf(StationData data) {
        String address = data.address() == null ? "" : data.address();
        if (data.town() != null && !address.contains(data.town())) {
            address = address.isEmpty() ? data.town() : address + ", " + data.town();
        }
        return address;
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

            // une session sur deux a ete faite sur reservation (terminee avec la session)
            Long reservationId = null;
            if (index % 2 == 1) {
                Reservation reservation = new Reservation(driver.getId(), charger.getId(),
                        startedAt.minus(10, ChronoUnit.MINUTES), stoppedAt.plus(10, ChronoUnit.MINUTES));
                reservation.setStatus(Reservation.ReservationStatus.COMPLETED);
                reservationId = reservationRepository.save(reservation).getId();
            }
            ChargingSession session = new ChargingSession(driver.getId(), charger.getId(), reservationId);
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

    /**
     * Reservations a venir (demain et apres-demain) et une reservation annulee.
     * Bornes non utilisees par les tests ni par les scripts de demonstration
     * (CP-460375-1, CP-175672-2 et CP-175672-3).
     */
    private void seedUpcomingReservations() {
        List<Driver> drivers = driverRepository.findAll(Sort.by("id"));
        if (drivers.size() < 3) {
            return;
        }
        LocalDate today = LocalDate.now(TUNIS);
        Instant tomorrow = today.plusDays(1).atTime(9, 0).atZone(TUNIS).toInstant();
        Instant afterTomorrow = today.plusDays(2).atTime(18, 30).atZone(TUNIS).toInstant();
        Instant yesterday = today.minusDays(1).atTime(17, 0).atZone(TUNIS).toInstant();

        reservationRepository.save(new Reservation(drivers.get(1).getId(), "CP-175673-3", tomorrow, tomorrow.plus(1, ChronoUnit.HOURS)));
        reservationRepository.save(new Reservation(drivers.get(0).getId(), "CP-175670-2", afterTomorrow, afterTomorrow.plus(90, ChronoUnit.MINUTES)));
        reservationRepository.save(new Reservation(drivers.get(2).getId(), "CP-460271-1", tomorrow, tomorrow.plus(1, ChronoUnit.HOURS)));
        Reservation cancelled = new Reservation(drivers.get(1).getId(), "CP-203502-1", yesterday, yesterday.plus(1, ChronoUnit.HOURS));
        cancelled.setStatus(Reservation.ReservationStatus.CANCELLED);
        reservationRepository.save(cancelled);
        log.info("Preloaded upcoming and cancelled reservations");
    }
}
