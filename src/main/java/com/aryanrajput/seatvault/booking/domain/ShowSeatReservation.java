package com.aryanrajput.seatvault.booking.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * The single source of truth for "who owns this seat, for this show, right now".
 *
 * <p>One row exists for every seat belonging to a show (seeded when the show
 * is created — see {@link com.aryanrajput.seatvault.booking.service.ShowService}).
 * Redis ({@link com.aryanrajput.seatvault.booking.service.SeatLockService}) is only a
 * fast, short-lived, best-effort lock used to reject the vast majority of
 * concurrent requests before they ever reach the database. This table is
 * what actually decides ownership: every transition (claim, confirm,
 * release) locks this row with {@code SELECT ... FOR UPDATE}, so even if a
 * Redis TTL expires while a payment is still in flight, an older booking can
 * never confirm a seat that a newer booking has already claimed here.
 */
@Entity
@Table(
        name = "show_seat_reservations",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_show_seat_reservation",
                columnNames = {"show_id", "seat_id"}
        )
)
public class ShowSeatReservation { //this is a representation of the seat's reservation for a particular show.
                                   //A physical seat can be BOOKED for one show and AVAILABLE for another, so we need to represent a seat reservation for a particular show.

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false) //Many ShowSeatReservation records can belong to one Show. optional = false means Every reservation must have a Show. It cannot be null.
    @JoinColumn(name = "show_id") //show_id is the foreign-key column in the show_seat_reservations table.
    private Show show;

    @ManyToOne(optional = false)
    @JoinColumn(name = "seat_id")
    private Seat seat;

    @ManyToOne
    @JoinColumn(name = "booking_id")
    private Booking booking;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReservationState state = ReservationState.AVAILABLE;

    private Instant expiresAt;

    protected ShowSeatReservation() {
        // required by JPA
    }

    public ShowSeatReservation(Show show, Seat seat) {
        this.show = show;
        this.seat = seat;
    }

    public Long getSeatId() {
        return seat.getId();
    }

    public Long getBookingId() {
        return booking == null ? null : booking.getId();
    }

    public ReservationState getState() {
        return state;
    }

    /**
     * True if this seat cannot be claimed by a new booking right now: it is
     * either permanently confirmed, or held by a hold that has not yet expired.
     */
    public boolean unavailableAt(Instant now) {
        boolean confirmed = state == ReservationState.CONFIRMED;
        boolean liveHold = state == ReservationState.HELD && expiresAt.isAfter(now);
        return confirmed || liveHold;
    }

    public boolean canBeClaimed(Instant now) {
        return !unavailableAt(now);
    }

    /** Claims this seat for a new pending booking, with a hold that expires at {@code expiresAt}. */
    public void claim(Booking booking, Instant expiresAt) {
        this.booking = booking;
        this.expiresAt = expiresAt;
        this.state = ReservationState.HELD;
    }

    public boolean belongsTo(Booking otherBooking) {
        return booking != null && booking.getId().equals(otherBooking.getId());
    }

    public boolean isLiveHold(Instant now) {
        return state == ReservationState.HELD && expiresAt.isAfter(now);
    }

    /** Called on payment success. The hold becomes permanent and never expires. */
    public void confirm() {
        state = ReservationState.CONFIRMED;
        expiresAt = null;
    }

    /** Called on cancellation, payment failure past the retry limit, or hold timeout. */
    public void release() {
        state = ReservationState.AVAILABLE;
        booking = null;
        expiresAt = null;
    }
}
