package tn.voltpark.session.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tn.voltpark.session.domain.model.Invoice;

import java.util.List;
import java.util.Optional;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, Long> {
    Optional<Invoice> findBySessionId(String sessionId);
    List<Invoice> findByDriverId(Long driverId);
}
