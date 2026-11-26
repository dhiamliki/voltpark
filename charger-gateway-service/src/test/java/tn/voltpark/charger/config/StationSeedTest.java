package tn.voltpark.charger.config;

import org.junit.jupiter.api.Test;
import tn.voltpark.charger.ws.ChargerStatus;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StationSeedTest {

    @Test
    void oneChargerPerChargingPointOfTheSharedFile() {
        Map<String, ChargerStatus> chargers = DataInitializer.chargersFromFile();

        assertThat(chargers).hasSize(31);
        assertThat(chargers).containsKeys("CP-460375-1", "CP-460375-2", "CP-175672-3", "CP-175673-4");
        assertThat(chargers).doesNotContainKey("CP-460375-3");
    }

    @Test
    void simulatedStatusesMatchSessionService() {
        Map<String, ChargerStatus> chargers = DataInitializer.chargersFromFile();

        assertThat(chargers.get("CP-460375-1")).isEqualTo(ChargerStatus.AVAILABLE);
        assertThat(chargers.get("CP-175672-2")).isEqualTo(ChargerStatus.AVAILABLE);
        assertThat(chargers.get("CP-175672-3")).isEqualTo(ChargerStatus.AVAILABLE);
        assertThat(chargers.get("CP-460375-2")).isEqualTo(ChargerStatus.OCCUPIED);
        assertThat(chargers.get("CP-175677-3")).isEqualTo(ChargerStatus.FAULTED);
        assertThat(chargers.values().stream().filter(s -> s == ChargerStatus.AVAILABLE)).hasSize(23);
    }
}
