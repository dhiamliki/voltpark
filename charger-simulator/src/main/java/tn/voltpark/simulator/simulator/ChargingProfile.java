package tn.voltpark.simulator.simulator;

import com.google.protobuf.Timestamp;
import tn.voltpark.energy.grpc.v1.MeterValue;

import java.time.Instant;
import java.util.Random;

/**
 * Genere des mesures realistes pour une session : puissance entre 7 et 22 kW,
 * reduite au-dessus de 80 % de charge, energie cumulee et soc croissants.
 */
class ChargingProfile {

    private static final double BATTERY_CAPACITY_KWH = 60.0;

    private final String sessionId;
    private final String chargerId;
    private final int timeFactor;
    private final Random random;
    private final double maxPowerKw;

    private double energyKwh;
    private double soc;

    ChargingProfile(String sessionId, String chargerId, int timeFactor, Random random) {
        this.sessionId = sessionId;
        this.chargerId = chargerId;
        this.timeFactor = timeFactor;
        this.random = random;
        this.maxPowerKw = 7 + random.nextDouble() * 15;
        this.soc = 15 + random.nextInt(30);
    }

    /** Mesure suivante, une seconde reelle (soit timeFactor secondes de charge) apres la precedente. */
    MeterValue next(Instant now) {
        double taper = soc < 80 ? 1.0 : Math.max(0.2, (100 - soc) / 20.0);
        double powerKw = maxPowerKw * taper * (0.97 + random.nextDouble() * 0.06);

        energyKwh += powerKw * timeFactor / 3600.0;
        soc = Math.min(100.0, soc + powerKw * timeFactor / 3600.0 / BATTERY_CAPACITY_KWH * 100);

        return MeterValue.newBuilder()
                .setSessionId(sessionId)
                .setChargerId(chargerId)
                .setTimestamp(Timestamp.newBuilder()
                        .setSeconds(now.getEpochSecond())
                        .setNanos(now.getNano()))
                .setPowerKw(round(powerKw))
                .setEnergyKwh(round(energyKwh))
                .setSocPercent((int) soc)
                .build();
    }

    private static double round(double value) {
        return Math.round(value * 1000) / 1000.0;
    }
}
