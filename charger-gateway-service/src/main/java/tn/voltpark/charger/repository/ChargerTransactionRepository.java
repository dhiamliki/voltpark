package tn.voltpark.charger.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tn.voltpark.charger.model.ChargerTransaction;

@Repository
public interface ChargerTransactionRepository extends JpaRepository<ChargerTransaction, Integer> {
}
