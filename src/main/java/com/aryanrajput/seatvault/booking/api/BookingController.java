package com.aryanrajput.seatvault.booking.api;

import com.aryanrajput.seatvault.booking.api.Dtos.AvailableSeatsResponse;
import com.aryanrajput.seatvault.booking.api.Dtos.BookingResponse;
import com.aryanrajput.seatvault.booking.api.Dtos.CreateBookingRequest;
import com.aryanrajput.seatvault.booking.api.Dtos.CreateShowRequest;
import com.aryanrajput.seatvault.booking.api.Dtos.IdResponse;
import com.aryanrajput.seatvault.booking.api.Dtos.NameRequest;
import com.aryanrajput.seatvault.booking.api.Dtos.SeatRequest;
import com.aryanrajput.seatvault.booking.api.Dtos.SeatResponse;
import com.aryanrajput.seatvault.booking.api.Dtos.ShowResponse;
import com.aryanrajput.seatvault.booking.api.Dtos.UserRequest;
import com.aryanrajput.seatvault.booking.domain.Booking;
import com.aryanrajput.seatvault.booking.domain.Movie;
import com.aryanrajput.seatvault.booking.domain.Screen;
import com.aryanrajput.seatvault.booking.domain.Seat;
import com.aryanrajput.seatvault.booking.domain.Show;
import com.aryanrajput.seatvault.booking.domain.ShowSeatReservation;
import com.aryanrajput.seatvault.booking.domain.Theatre;
import com.aryanrajput.seatvault.booking.repo.Repositories.Movies;
import com.aryanrajput.seatvault.booking.repo.Repositories.Screens;
import com.aryanrajput.seatvault.booking.repo.Repositories.Seats;
import com.aryanrajput.seatvault.booking.repo.Repositories.Shows;
import com.aryanrajput.seatvault.booking.repo.Repositories.ShowSeatReservations;
import com.aryanrajput.seatvault.booking.repo.Repositories.Theatres;
import com.aryanrajput.seatvault.booking.service.BookingService;
import com.aryanrajput.seatvault.booking.service.ShowService;
import jakarta.validation.Valid;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * HTTP boundary for the whole application. Every endpoint here only talks in
 * request/response DTOs (see {@link Dtos}) — JPA entities never leave this
 * class. Business rules and concurrency control live in
 * {@link BookingService} and {@link ShowService}; this class is deliberately
 * thin.
 */
// BookingController exposes the REST endpoints related to booking operations. It receives HTTP requests, maps request data into DTOs,
// delegates the actual business logic to BookingService, and returns the appropriate HTTP response.
@RestController
@RequestMapping("/api")
public class BookingController { //the first entry point of the project after initialisation. The controller handles HTTP requests related to bookings.

    private final Theatres theatres;
    private final Screens screens;
    private final Seats seats;
    private final Movies movies;
    private final Shows shows;
    private final ShowSeatReservations reservations;
    private final BookingService bookingService;
    private final ShowService showService;

    public BookingController(
            Theatres theatres,
            Screens screens,
            Seats seats,
            Movies movies,
            Shows shows,
            ShowSeatReservations reservations,
            BookingService bookingService,
            ShowService showService) {
        this.theatres = theatres;
        this.screens = screens;
        this.seats = seats;
        this.movies = movies;
        this.shows = shows;
        this.reservations = reservations;
        this.bookingService = bookingService;
        this.showService = showService;
    }

    // ------------------------------------------------------------------
    // Venue setup
    // ------------------------------------------------------------------

    @PostMapping("/theatres")
    @ResponseStatus(HttpStatus.CREATED)
    public IdResponse createTheatre(@Valid @RequestBody NameRequest request) {
        Theatre theatre = theatres.save(new Theatre(request.name()));
        return new IdResponse(theatre.getId());
    }

    @PostMapping("/theatres/{theatreId}/screens")
    @ResponseStatus(HttpStatus.CREATED)
    public IdResponse createScreen(@PathVariable Long theatreId, @Valid @RequestBody NameRequest request) {
        Theatre theatre = getOrThrow(theatres, theatreId);
        Screen screen = screens.save(new Screen(request.name(), theatre));
        return new IdResponse(screen.getId());
    }

    @PostMapping("/screens/{screenId}/seats")
    @ResponseStatus(HttpStatus.CREATED)
    public IdResponse createSeat(@PathVariable Long screenId, @Valid @RequestBody SeatRequest request) {
        Screen screen = getOrThrow(screens, screenId);
        Seat seat = seats.save(new Seat(screen, request.rowNumber(), request.seatNumber()));
        return new IdResponse(seat.getId());
    }

    @PostMapping("/movies")
    @ResponseStatus(HttpStatus.CREATED)
    public IdResponse createMovie(@Valid @RequestBody NameRequest request) {
        Movie movie = movies.save(new Movie(request.name()));
        return new IdResponse(movie.getId());
    }

    // ------------------------------------------------------------------
    // Shows
    // ------------------------------------------------------------------

    @PostMapping("/shows")
    @ResponseStatus(HttpStatus.CREATED)
    public ShowResponse createShow(@Valid @RequestBody CreateShowRequest request) {
        Show show = showService.create(request.movieId(), request.screenId(), request.startTime(), request.durationMinutes());
        return toShowResponse(show);
    }

    @GetMapping("/shows/{showId}")
    public ShowResponse getShow(@PathVariable Long showId) {
        return toShowResponse(getOrThrow(shows, showId));
    }

    @GetMapping("/shows/{showId}/seats")
    public AvailableSeatsResponse getAvailableSeats(@PathVariable Long showId) {
        Show show = getOrThrow(shows, showId);

        Set<Long> unavailableSeatIds = reservations.findByShowId(showId).stream()
                .filter(reservation -> reservation.unavailableAt(Instant.now()))
                .map(ShowSeatReservation::getSeatId)
                .collect(Collectors.toSet());

        List<SeatResponse> seatResponses = seats.findByScreenId(show.getScreen().getId()).stream()
                .map(seat -> new SeatResponse(
                        seat.getId(),
                        seat.getRowNumber(),
                        seat.getSeatNumber(),
                        !unavailableSeatIds.contains(seat.getId())))
                .toList();

        return new AvailableSeatsResponse(showId, seatResponses);
    }

    // ------------------------------------------------------------------
    // Bookings and payments
    // ------------------------------------------------------------------

    @PostMapping("/bookings") //when a client requests for booking a seat, thats where it points firsts.It delegates to BookingService
    @ResponseStatus(HttpStatus.CREATED)
    public BookingResponse createBooking(@Valid @RequestBody CreateBookingRequest request) {
        Booking booking = bookingService.create(request.userId(), request.showId(), request.seatIds());
        return toBookingResponse(booking);
    }

    @PostMapping("/bookings/{bookingId}/cancel")
    public BookingResponse cancelBooking(@PathVariable Long bookingId, @Valid @RequestBody UserRequest request) {
        return toBookingResponse(bookingService.cancel(bookingId, request.userId()));
    }

    @PostMapping("/payments/{bookingId}/success")
    public BookingResponse paymentSucceeded(@PathVariable Long bookingId, @Valid @RequestBody UserRequest request) {
        return toBookingResponse(bookingService.succeed(bookingId, request.userId()));
    }

    @PostMapping("/payments/{bookingId}/failure")
    public BookingResponse paymentFailed(@PathVariable Long bookingId, @Valid @RequestBody UserRequest request) {
        return toBookingResponse(bookingService.fail(bookingId, request.userId()));
    }

    // ------------------------------------------------------------------
    // Mapping helpers
    // ------------------------------------------------------------------

    private BookingResponse toBookingResponse(Booking booking) { //service's booking returns here, this is called when the payment is successfull and seat is reserved
        List<Long> seatIds = booking.getSeats().stream().map(Seat::getId).toList();
        return new BookingResponse(
                booking.getId(),
                booking.getShow().getId(),
                booking.getUserId(),
                booking.getStatus(),
                booking.getCreatedAt(),
                booking.getPaymentFailures(),
                seatIds);
    }

    private ShowResponse toShowResponse(Show show) {
        return new ShowResponse(
                show.getId(),
                show.getMovie().getId(),
                show.getScreen().getId(),
                show.getStartTime(),
                show.getEndTime());
    }

    private <T> T getOrThrow(JpaRepository<T, Long> repository, Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Resource not found: " + id));
    }
}
