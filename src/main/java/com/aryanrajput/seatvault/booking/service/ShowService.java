package com.aryanrajput.seatvault.booking.service;

import com.aryanrajput.seatvault.booking.domain.Movie;
import com.aryanrajput.seatvault.booking.domain.Screen;
import com.aryanrajput.seatvault.booking.domain.Show;
import com.aryanrajput.seatvault.booking.domain.ShowSeatReservation;
import com.aryanrajput.seatvault.booking.repo.Movies;
import com.aryanrajput.seatvault.booking.repo.Screens;
import com.aryanrajput.seatvault.booking.repo.Seats;
import com.aryanrajput.seatvault.booking.repo.Shows;
import com.aryanrajput.seatvault.booking.repo.ShowSeatReservations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.NoSuchElementException;

/**
 * Creates {@link Show}s and seeds their per-seat {@link ShowSeatReservation}
 * rows.
 */
@Service
public class ShowService { //This handles show-related business operations.

    private final Shows shows;
    private final Movies movies;
    private final Screens screens;
    private final Seats seats;
    private final ShowSeatReservations reservations;

    public ShowService(Shows shows, Movies movies, Screens screens, Seats seats, ShowSeatReservations reservations) {
        this.shows = shows;
        this.movies = movies;
        this.screens = screens;
        this.seats = seats;
        this.reservations = reservations;
    }

    /**
     * Schedules a new show, rejecting it if it would overlap an existing show
     * on the same screen.
     *
     * <p>The screen row is locked with {@code SELECT ... FOR UPDATE} before
     * the overlap check runs, so two concurrent requests to schedule
     * overlapping shows on the same screen are serialized — the second one
     * to acquire the lock will see the first show's now-committed row and
     * correctly reject itself.
     */
    @Transactional
    public Show create(Long movieId, Long screenId, Instant startTime, int durationMinutes) {
        if (durationMinutes <= 0) {
            throw new IllegalArgumentException("Duration must be positive");
        }

        Screen screen = screens.lockById(screenId)
                .orElseThrow(() -> new NoSuchElementException("Screen not found: " + screenId));

        Instant endTime = startTime.plusSeconds(durationMinutes * 60L);
        boolean overlapsExistingShow = shows.findByScreenId(screenId).stream()
                .anyMatch(existing -> existing.getStartTime().isBefore(endTime) && existing.getEndTime().isAfter(startTime));
        if (overlapsExistingShow) {
            throw new IllegalStateException("Screen already has an overlapping show");
        }

        Movie movie = movies.findById(movieId)
                .orElseThrow(() -> new NoSuchElementException("Movie not found: " + movieId));

        Show show = shows.save(new Show(movie, screen, startTime, durationMinutes));

        // Seed one reservation row per seat on this screen so BookingService
        // always has an authoritative row to lock against for this show.
        reservations.saveAll(
                seats.findByScreenId(screenId).stream()
                        .map(seat -> new ShowSeatReservation(show, seat))
                        .toList()
        );

        return show;
    }
}
