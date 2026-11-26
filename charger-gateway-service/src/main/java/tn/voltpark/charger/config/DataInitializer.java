package tn.voltpark.charger.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tn.voltpark.charger.model.AuthorizedBadge;
import tn.voltpark.charger.model.ChargerStation;
import tn.voltpark.charger.repository.AuthorizedBadgeRepository;
import tn.voltpark.charger.repository.ChargerStationRepository;
import tn.voltpark.charger.ws.ChargerStatus;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Donnees de demonstration, inserees seulement si les tables sont vides.
 * Bornes : fichier partage data/stations-tn.json (bornes reelles, Open Charge Map), avec les memes
 * identifiants et les memes statuts simules que session-service (DataInitializer).
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);
    static final String STATIONS_FILE = "data/stations-tn.json";

    @JsonIgnoreProperties(ignoreUnknown = true)
    record StationData(long ocmId, List<ConnectionData> connections) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ConnectionData(int quantity) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record StationsFile(List<StationData> stations) {
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
            Map<String, ChargerStatus> chargers = chargersFromFile();
            chargers.forEach((id, status) -> chargerRepository.save(new ChargerStation(id, status)));
            log.info("Preloaded {} chargers (Open Charge Map stations) into charger-db", chargers.size());
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

    /** Une borne par point de charge : CP-(id Open Charge Map)-(numero dans la station, a partir de 1). */
    static Map<String, ChargerStatus> chargersFromFile() {
        Map<String, ChargerStatus> chargers = new LinkedHashMap<>();
        for (StationData station : readStations()) {
            int index = 0;
            for (ConnectionData connection : station.connections()) {
                for (int i = 0; i < connection.quantity(); i++) {
                    index++;
                    String id = "CP-" + station.ocmId() + "-" + index;
                    chargers.put(id, simulatedStatus(id, index));
                }
            }
        }
        return chargers;
    }

    private static List<StationData> readStations() {
        try (InputStream in = new ClassPathResource(STATIONS_FILE).getInputStream()) {
            return new ObjectMapper().readValue(in, StationsFile.class).stations();
        } catch (IOException e) {
            throw new UncheckedIOException("Lecture impossible de " + STATIONS_FILE, e);
        }
    }

    /**
     * Statut simule, deterministe (meme regle dans session-service) : la premiere borne de chaque station
     * est libre ; pour les autres, le hashCode de l'identifiant donne 6 chances sur 10 d'etre libre,
     * 3 d'etre occupee et 1 d'etre hors service.
     */
    static ChargerStatus simulatedStatus(String chargerId, int index) {
        if (index == 1) {
            return ChargerStatus.AVAILABLE;
        }
        int bucket = Math.floorMod(chargerId.hashCode(), 10);
        if (bucket <= 5) {
            return ChargerStatus.AVAILABLE;
        }
        return bucket <= 8 ? ChargerStatus.OCCUPIED : ChargerStatus.FAULTED;
    }
}
