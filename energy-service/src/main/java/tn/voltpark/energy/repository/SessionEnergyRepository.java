package tn.voltpark.energy.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.voltpark.energy.model.SessionEnergy;

public interface SessionEnergyRepository extends JpaRepository<SessionEnergy, String> {
}
