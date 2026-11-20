package tn.voltpark.simulator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Parametres du simulateur, surchargeables par les variables SIMULATOR_*.
 *
 * @param enabled         active ou non la simulation
 * @param chargerId       identifiant de la borne simulee
 * @param intervalSeconds intervalle entre deux sessions simulees
 * @param durationSeconds duree d'une session (une mesure par seconde)
 * @param timeFactor      acceleration : une seconde reelle correspond a timeFactor secondes de charge,
 *                        pour que l'energie et le soc evoluent de facon visible pendant la demo
 */
@ConfigurationProperties(prefix = "simulator")
public record SimulatorProperties(
        boolean enabled,
        String chargerId,
        int intervalSeconds,
        int durationSeconds,
        int timeFactor) {
}
