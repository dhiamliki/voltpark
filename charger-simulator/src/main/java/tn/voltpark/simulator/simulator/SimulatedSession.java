package tn.voltpark.simulator.simulator;

import java.time.Instant;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Une session simulee en cours : identifiant, borne, profil de charge et signal d'arret.
 * Le profil est garde ici pour qu'un nouvel essai (retry) reprenne l'energie la ou elle
 * en etait : energy-service refuse une mesure inferieure a la precedente.
 */
public final class SimulatedSession {

    private final String sessionId;
    private final String chargerId;
    private final Instant startedAt = Instant.now();
    private final ChargingProfile profile;
    private final CountDownLatch stopSignal = new CountDownLatch(1);
    private final CompletableFuture<Void> finished = new CompletableFuture<>();

    public SimulatedSession(String sessionId, String chargerId, int timeFactor, Random random) {
        this.sessionId = sessionId;
        this.chargerId = chargerId;
        this.profile = new ChargingProfile(sessionId, chargerId, timeFactor, random);
    }

    public String sessionId() {
        return sessionId;
    }

    public String chargerId() {
        return chargerId;
    }

    public Instant startedAt() {
        return startedAt;
    }

    ChargingProfile profile() {
        return profile;
    }

    /** Demande l'arret : le flux de mesures se termine normalement (onCompleted). */
    public void requestStop() {
        stopSignal.countDown();
    }

    public boolean isStopRequested() {
        return stopSignal.getCount() == 0;
    }

    /** Attend au plus le delai donne, ou moins si l'arret est demande entre-temps. */
    boolean awaitStop(long millis) throws InterruptedException {
        return stopSignal.await(millis, TimeUnit.MILLISECONDS);
    }

    void markFinished() {
        finished.complete(null);
    }

    /** Attend la fin du flux (resume recu ou erreur), au plus le delai donne. */
    public boolean awaitFinished(long millis) {
        try {
            finished.get(millis, TimeUnit.MILLISECONDS);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            return false;
        }
    }
}
