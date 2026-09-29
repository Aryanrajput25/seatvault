package com.aryanrajput.seatvault.booking.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Column;
import jakarta.persistence.ManyToOne;

/**
 * A physical seat at a fixed row/number position inside a {@link Screen}.
 * A seat's availability is show-specific and tracked separately by
 * {@link ShowSeatReservation} — this entity only describes its position.
 */
@Entity
public class Seat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false) //@ManyToOne represents a relationship where multiple entities are associated with one entity. In SeatVault, multiple seats belong to the same screen.
    private Screen screen;

    @Column(name = "row_num", nullable = false)
    private int rowNumber;

    @Column(nullable = false)
    private int seatNumber;

    protected Seat() {
        // required by JPA
    }

    public Seat(Screen screen, int rowNumber, int seatNumber) {
        this.screen = screen;
        this.rowNumber = rowNumber;
        this.seatNumber = seatNumber;
    }

    public Long getId() {
        return id;
    }

    public Screen getScreen() {
        return screen;
    }

    public int getRowNumber() {
        return rowNumber;
    }

    public int getSeatNumber() {
        return seatNumber;
    }
}
