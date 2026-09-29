package com.aryanrajput.seatvault.booking.repo;

import com.aryanrajput.seatvault.booking.domain.ShowSeatReservation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ShowSeatReservations extends JpaRepository<ShowSeatReservation, Long> {

    List<ShowSeatReservation> findByShowId(Long showId);

    /**
     * Locks every reservation row for the given seats on the given show,
     * always in ascending seat-id order. Locking in a consistent order
     * across all callers is what prevents two transactions that both need
     * an overlapping set of seats from deadlocking against each other.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select r
        from ShowSeatReservation r
        where r.show.id = :showId
        and r.seat.id in :seatIds
        order by r.seat.id
    """)
    List<ShowSeatReservation> lockAll(
            @Param("showId") Long showId,
            @Param("seatIds") List<Long> seatIds
    );
}
