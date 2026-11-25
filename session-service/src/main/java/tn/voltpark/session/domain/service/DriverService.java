package tn.voltpark.session.domain.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.voltpark.session.api.dto.DriverRequest;
import tn.voltpark.session.api.dto.DriverResponse;
import tn.voltpark.session.api.error.ResourceConflictException;
import tn.voltpark.session.api.error.ResourceNotFoundException;
import tn.voltpark.session.domain.model.Driver;
import tn.voltpark.session.repository.DriverRepository;

import java.util.List;

@Service
public class DriverService {

    private final DriverRepository driverRepository;

    public DriverService(DriverRepository driverRepository) {
        this.driverRepository = driverRepository;
    }

    @Transactional
    public DriverResponse createDriver(DriverRequest request) {
        if (driverRepository.existsByEmail(request.email())) {
            throw new ResourceConflictException("Un conducteur avec cet email existe deja: " + request.email());
        }
        if (driverRepository.existsByBadgeId(request.badgeId())) {
            throw new ResourceConflictException("Ce badge RFID est deja utilise: " + request.badgeId());
        }

        Driver driver = new Driver(request.name(), request.email(), request.badgeId(), request.phoneNumber());
        Driver saved = driverRepository.save(driver);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public DriverResponse getDriver(Long id) {
        Driver driver = driverRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Conducteur", id));
        return toResponse(driver);
    }

    @Transactional(readOnly = true)
    public List<DriverResponse> getAllDrivers() {
        return driverRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public Driver findEntityById(Long id) {
        return driverRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Conducteur", id));
    }

    private DriverResponse toResponse(Driver d) {
        return new DriverResponse(d.getId(), d.getName(), d.getEmail(), d.getBadgeId(), d.getPhoneNumber(), d.getCreatedAt());
    }
}
