package tn.voltpark.session.domain.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tn.voltpark.session.api.dto.ReservationRequest;
import tn.voltpark.session.api.dto.ReservationResponse;
import tn.voltpark.session.api.error.ReservationConflictException;
import tn.voltpark.session.domain.model.Reservation;
import tn.voltpark.session.repository.ChargerRepository;
import tn.voltpark.session.repository.DriverRepository;
import tn.voltpark.session.repository.ReservationRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private DriverRepository driverRepository;

    @Mock
    private ChargerRepository chargerRepository;

    private ReservationService service;

    @BeforeEach
    void init() {
        service = new ReservationService(reservationRepository, driverRepository, chargerRepository);
    }

    @Test
    void testReservationCreation() {
        Instant tStart = Instant.now().plus(2, ChronoUnit.HOURS);
        Instant tEnd = tStart.plus(1, ChronoUnit.HOURS);

        when(driverRepository.existsById(10L)).thenReturn(true);
        when(chargerRepository.existsById("CP-460375-1")).thenReturn(true);
        when(reservationRepository.findOverlappingReservations("CP-460375-1", tStart, tEnd)).thenReturn(List.of());

        Reservation entity = new Reservation(10L, "CP-460375-1", tStart, tEnd);
        when(reservationRepository.save(any(Reservation.class))).thenReturn(entity);

        ReservationResponse resp = service.createReservation(new ReservationRequest(10L, "CP-460375-1", tStart, tEnd));
        assertNotNull(resp);
        assertEquals("CP-460375-1", resp.chargerId());
    }

    @Test
    void testReservationConflict() {
        Instant tStart = Instant.now().plus(2, ChronoUnit.HOURS);
        Instant tEnd = tStart.plus(1, ChronoUnit.HOURS);

        when(driverRepository.existsById(10L)).thenReturn(true);
        when(chargerRepository.existsById("CP-460375-1")).thenReturn(true);
        when(reservationRepository.findOverlappingReservations("CP-460375-1", tStart, tEnd))
                .thenReturn(List.of(new Reservation(99L, "CP-460375-1", tStart, tEnd)));

        assertThrows(ReservationConflictException.class, () ->
                service.createReservation(new ReservationRequest(10L, "CP-460375-1", tStart, tEnd)));
    }

    @Test
    void testCancelReservation() {
        Reservation r = new Reservation(10L, "CP-460375-1", Instant.now(), Instant.now().plus(1, ChronoUnit.HOURS));
        when(reservationRepository.findById(7L)).thenReturn(Optional.of(r));
        when(reservationRepository.save(any())).thenAnswer(a -> a.getArgument(0));

        ReservationResponse resp = service.cancelReservation(7L);
        assertEquals(Reservation.ReservationStatus.CANCELLED, resp.status());
    }
}
