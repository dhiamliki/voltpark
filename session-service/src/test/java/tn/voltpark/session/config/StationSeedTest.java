package tn.voltpark.session.config;

import org.junit.jupiter.api.Test;
import tn.voltpark.session.domain.model.Charger;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StationSeedTest {

    @Test
    void readsRealStationsFromSharedFile() {
        List<DataInitializer.StationData> stations = DataInitializer.readStations();

        assertThat(stations).hasSize(16);
        DataInitializer.StationData sidiKhelifa = stations.stream()
                .filter(s -> s.ocmId() == 460375).findFirst().orElseThrow();
        assertThat(sidiKhelifa.corridor()).isEqualTo("A1");
        assertThat(sidiKhelifa.latitude()).isEqualTo(36.247161607668616);
        assertThat(sidiKhelifa.connections()).extracting(DataInitializer.ConnectionData::type)
                .containsExactly("CCS", "CHAdeMO");
        assertThat(stations.stream().filter(s -> "A1".equals(s.corridor()))).hasSize(8);
    }

    @Test
    void idsAreDerivedFromOpenChargeMapId() {
        assertThat(DataInitializer.stationCode(460375)).isEqualTo("ST-OCM-460375");
        assertThat(DataInitializer.chargerId(460375, 1)).isEqualTo("CP-460375-1");
    }

    @Test
    void chargersUsedByTestsAndDemoScriptsAreAvailable() {
        // le premier point de charge d'une station est toujours libre
        assertThat(DataInitializer.simulatedStatus("CP-460375-1", 1)).isEqualTo(Charger.ChargerStatus.AVAILABLE);
        assertThat(DataInitializer.simulatedStatus("CP-175672-2", 2)).isEqualTo(Charger.ChargerStatus.AVAILABLE);
        assertThat(DataInitializer.simulatedStatus("CP-175672-3", 3)).isEqualTo(Charger.ChargerStatus.AVAILABLE);
        // statut simule mais deterministe : memes valeurs dans charger-gateway-service
        assertThat(DataInitializer.simulatedStatus("CP-460375-2", 2)).isEqualTo(Charger.ChargerStatus.OCCUPIED);
        assertThat(DataInitializer.simulatedStatus("CP-175677-3", 3)).isEqualTo(Charger.ChargerStatus.FAULTED);
    }
}
