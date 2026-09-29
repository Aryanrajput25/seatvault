package com.aryanrajput.seatvault.booking.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * A permanent record that a seat was confirmed for a show, protected by a
 * database-level unique constraint on {@code (show_id, seat_id)}.
 *
 * <p>This is deliberately redundant with {@link ShowSeatReservation}'s
 * {@code CONFIRMED} state: the reservation row is what the application logic
 * checks during normal operation, while this table is the last line of
 * defense — even if there were ever a bug upstream that let two bookings
 * reach the confirm step for the same seat, the database itself would refuse
 * to let the second one insert.
 */
@Entity
@Table(
        name = "confirmed_show_seats",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_confirmed_show_seat",
                columnNames = {"show_id", "seat_id"}
        )
)
public class ConfirmedShowSeat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false) //
    @JoinColumn(name = "show_id")
    private Show show;

    @ManyToOne(optional = false)
    @JoinColumn(name = "seat_id")
    private Seat seat;

    @ManyToOne(optional = false)
    @JoinColumn(name = "booking_id")
    private Booking booking;

    protected ConfirmedShowSeat() {
        // required by JPA
    }

    public ConfirmedShowSeat(Show show, Seat seat, Booking booking) {
        this.show = show;
        this.seat = seat;
        this.booking = booking;
    }
}
