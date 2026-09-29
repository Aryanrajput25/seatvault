package com.aryanrajput.seatvault.booking.repo;

import com.aryanrajput.seatvault.booking.domain.Booking;
import com.aryanrajput.seatvault.booking.domain.BookingStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface Bookings extends JpaRepository<Booking, Long> {

    List<Booking> findByStatusAndCreatedAtBefore(BookingStatus status, Instant cutoff);

    /**
     * Locks a single booking's row so that concurrent actions on the same
     * booking (payment success, payment failure, cancellation, or the
     * background expiry sweep) are serialized and only one can win.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Booking b where b.id = :id")
    Optional<Booking> lockById(@Param("id") Long id);
}
