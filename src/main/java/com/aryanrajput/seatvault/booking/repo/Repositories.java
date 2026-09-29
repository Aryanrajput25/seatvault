package com.aryanrajput.seatvault.booking.repo;

import com.aryanrajput.seatvault.booking.domain.Booking;
import com.aryanrajput.seatvault.booking.domain.BookingStatus;
import com.aryanrajput.seatvault.booking.domain.ConfirmedShowSeat;
import com.aryanrajput.seatvault.booking.domain.Movie;
import com.aryanrajput.seatvault.booking.domain.Screen;
import com.aryanrajput.seatvault.booking.domain.Seat;
import com.aryanrajput.seatvault.booking.domain.Show;
import com.aryanrajput.seatvault.booking.domain.ShowSeatReservation;
import com.aryanrajput.seatvault.booking.domain.Theatre;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repositories that need to
 * take part in the concurrency-control scheme expose a {@code lockById} or
 * {@code lockAll} method that acquires a {@code SELECT ... FOR UPDATE} row
 * lock via {@link Lock @Lock(PESSIMISTIC_WRITE)} — see
 * {@link com.aryanrajput.seatvault.booking.service.BookingService} and
 * {@link com.aryanrajput.seatvault.booking.service.ShowService} for how those locks
 * are used to make state transitions safe under concurrent access.
 */
public final class Repositories { //Repository classes are responsible for communicating with the database through Spring Data JPA.

    private Repositories() {
        // namespace only — not instantiable
    }

    public interface Theatres extends JpaRepository<Theatre, Long> {
    }

    public interface Screens extends JpaRepository<Screen, Long> {

        /**
         * Locks a screen's row so that two concurrent "create a show on this
         * screen" requests are serialized, preventing both from passing the
         * overlap check and creating conflicting shows.
         */
        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("select s from Screen s where s.id = :id")
        Optional<Screen> lockById(@Param("id") Long id);
    }

    public interface Seats extends JpaRepository<Seat, Long> {

        List<Seat> findByScreenId(Long screenId);
    }

    public interface Movies extends JpaRepository<Movie, Long> {
    }

    public interface Shows extends JpaRepository<Show, Long> {

        List<Show> findByScreenId(Long screenId);
    }

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

    public interface ConfirmedSeats extends JpaRepository<ConfirmedShowSeat, Long> {

        boolean existsByShowIdAndSeatId(Long showId, Long seatId);
    }

    public interface ShowSeatReservations extends JpaRepository<ShowSeatReservation, Long> {

        List<ShowSeatReservation> findByShowId(Long showId);

        /**
         * Locks every reservation row for the given seats on the given show,
         * always in ascending seat-id order. Locking in a consistent order
         * across all callers is what prevents two transactions that both need
         * an overlapping set of seats from deadlocking against each other.
         */
        //this is a custom repository query
        @Lock(LockModeType.PESSIMISTIC_WRITE) //This tells JPA: When you fetch these reservation rows, lock them for writing.
        @Query("select r from ShowSeatReservation r "           //Hibernate then translates that into database-level locking behavior
                + "where r.show.id = :showId and r.seat.id in :seatIds "
                + "order by r.seat.id")
        List<ShowSeatReservation> lockAll(@Param("showId") Long showId, @Param("seatIds") List<Long> seatIds);
    }
}
