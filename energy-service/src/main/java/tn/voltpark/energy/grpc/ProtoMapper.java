package tn.voltpark.energy.grpc;

import com.google.protobuf.Timestamp;
import tn.voltpark.energy.grpc.v1.MeterValue;
import tn.voltpark.energy.grpc.v1.SessionProgress;
import tn.voltpark.energy.service.NewReading;
import tn.voltpark.energy.service.SessionProgressView;

import java.time.Instant;

/** Conversions entre les messages protobuf et les objets du service. */
final class ProtoMapper {

    private ProtoMapper() {
    }

    static NewReading toNewReading(MeterValue value) {
        return new NewReading(
                value.getSessionId(),
                value.getChargerId(),
                value.hasTimestamp() ? toInstant(value.getTimestamp()) : null,
                value.getPowerKw(),
                value.getEnergyKwh(),
                value.getSocPercent());
    }

    static SessionProgress toProto(SessionProgressView view) {
        return SessionProgress.newBuilder()
                .setSessionId(view.sessionId())
                .setTimestamp(toTimestamp(view.timestamp()))
                .setPowerKw(view.powerKw())
                .setEnergyKwh(view.energyKwh())
                .setSocPercent(view.socPercent())
                .setEstimatedMinutesRemaining(view.estimatedMinutesRemaining())
                .build();
    }

    static tn.voltpark.energy.grpc.v1.SessionEnergy toProto(tn.voltpark.energy.model.SessionEnergy session) {
        return tn.voltpark.energy.grpc.v1.SessionEnergy.newBuilder()
                .setSessionId(session.getSessionId())
                .setTotalEnergyKwh(session.getTotalEnergyKwh())
                .setStartedAt(toTimestamp(session.getStartedAt()))
                .setLastReadingAt(toTimestamp(session.getLastReadingAt()))
                .setPeakKwh(session.getPeakKwh())
                .setOffPeakKwh(session.getOffPeakKwh())
                .setReadingsCount(session.getReadingsCount())
                .build();
    }

    static Instant toInstant(Timestamp timestamp) {
        return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
    }

    static Timestamp toTimestamp(Instant instant) {
        return Timestamp.newBuilder()
                .setSeconds(instant.getEpochSecond())
                .setNanos(instant.getNano())
                .build();
    }
}
