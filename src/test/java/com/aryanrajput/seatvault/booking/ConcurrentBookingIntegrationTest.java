package com.aryanrajput.seatvault.booking;

import com.aryanrajput.seatvault.booking.repo.ConfirmedSeats;
import com.aryanrajput.seatvault.booking.repo.ShowSeatReservations;
import com.aryanrajput.seatvault.booking.service.SeatLockService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the concurrency guarantees end to end: real HTTP requests, hitting
 * a real Spring Boot app, backed by real MySQL and Redis containers — not a
 * unit test with two threads calling a service method directly.
 *
 * <p>Requires Docker to be running locally (Testcontainers starts MySQL and
 * Redis containers automatically for the test class).
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ConcurrentBookingIntegrationTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("seat_booking")
            .withUsername("test")
            .withPassword("test");

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("booking.lock-timeout-seconds", () -> "60");
    }

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private ConfirmedSeats confirmedSeats;

    @Autowired
    private ShowSeatReservations reservations;

    @Autowired
    private SeatLockService seatLocks;

    @LocalServerPort
    private int port;

    @Test
    void fiftyUsersRacingForOneSeatProduceExactlyOneConfirmedBooking() throws Exception {
        long confirmedBefore = confirmedSeats.count();
        Scenario scenario = createScenario(1);

        List<BookingAttempt> results = bookConcurrently(scenario.showId, List.of(scenario.seatIds.get(0)), 50);
        List<BookingAttempt> winners = results.stream().filter(r -> r.httpStatus == 201).toList();
        assertThat(winners).hasSize(1);

        confirmPayment(winners.get(0));

        assertThat(confirmedSeats.count()).isEqualTo(confirmedBefore + 1);
        assertThat(results.stream().filter(r -> r.httpStatus == 409).count()).isEqualTo(49);
    }

    @Test
    void fiftyUsersContendingForTenSeatsProduceTenBookingsWithoutDuplicates() throws Exception {
        long confirmedBefore = confirmedSeats.count();
        Scenario scenario = createScenario(10);

        List<Callable<BookingAttempt>> attempts = new ArrayList<>();
        for (Long seatId : scenario.seatIds) {
            for (int user = 0; user < 5; user++) {
                String userId = "user-" + seatId + "-" + user;
                attempts.add(() -> attemptBooking(scenario.showId, List.of(seatId), userId));
            }
        }

        ExecutorService pool = Executors.newFixedThreadPool(50);
        List<BookingAttempt> results = runAll(pool, attempts);
        pool.shutdown();

        List<BookingAttempt> winners = results.stream().filter(r -> r.httpStatus == 201).toList();
        assertThat(winners).hasSize(10);

        winners.forEach(this::confirmPayment);
        assertThat(confirmedSeats.count()).isEqualTo(confirmedBefore + 10);
    }

    @Test
    void expiredHoldCannotBeConfirmedAfterAnotherBookingClaimsTheSeat() {
        Scenario scenario = createScenario(1);
        Long seatId = scenario.seatIds.get(0);

        BookingAttempt aliceBooking = attemptBooking(scenario.showId, List.of(seatId), "alice");

        // Simulate alice's hold having already expired, then release her
        // Redis lock the way the background sweep would.
        var reservationRow = reservations.findByShowId(scenario.showId).get(0);
        ReflectionTestUtils.setField(reservationRow, "expiresAt", Instant.now().minusSeconds(1));
        reservations.saveAndFlush(reservationRow);
        seatLocks.release(scenario.showId, List.of(seatId), aliceBooking.bookingId);

        BookingAttempt bobBooking = attemptBooking(scenario.showId, List.of(seatId), "bob");
        assertThat(bobBooking.httpStatus).isEqualTo(201);

        // Alice's payment should now be rejected — bob's booking owns the seat.
        assertThat(paymentStatus(aliceBooking, "alice")).isEqualTo(409);
        assertThat(paymentStatus(bobBooking, "bob")).isEqualTo(200);
    }

    @Test
    void cancellationAndPaymentSuccessHaveExactlyOneTerminalWinner() throws Exception {
        Scenario scenario = createScenario(1);
        BookingAttempt booking = attemptBooking(scenario.showId, List.of(scenario.seatIds.get(0)), "alice");

        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Future<Integer>> outcomes = pool.invokeAll(List.of(
                () -> cancelStatus(booking, "alice"),
                () -> paymentStatus(booking, "alice")
        ));
        pool.shutdown();

        List<Integer> statuses = outcomes.stream().map(this::resultOf).toList();
        assertThat(statuses.stream().filter(status -> status == 200).count()).isEqualTo(1);
        assertThat(statuses.stream().filter(status -> status == 409).count()).isEqualTo(1);
    }

    @Test
    void concurrentOverlappingShowsOnOneScreenHaveExactlyOneWinner() throws Exception {
        Venue venue = createVenue(1);
        String startTime = Instant.now().plusSeconds(7200).toString();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Future<Integer>> outcomes = pool.invokeAll(List.of(
                () -> createShowStatus(venue.movieId, venue.screenId, startTime),
                () -> createShowStatus(venue.movieId, venue.screenId, startTime)
        ));
        pool.shutdown();

        List<Integer> statuses = outcomes.stream().map(this::resultOf).toList();
        assertThat(statuses.stream().filter(status -> status == 201).count()).isEqualTo(1);
        assertThat(statuses.stream().filter(status -> status == 409).count()).isEqualTo(1);
    }

    // ------------------------------------------------------------------
    // Test helpers
    // ------------------------------------------------------------------

    private List<BookingAttempt> bookConcurrently(Long showId, List<Long> seatIds, int userCount) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(userCount);
        List<Callable<BookingAttempt>> attempts = new ArrayList<>();
        for (int i = 0; i < userCount; i++) {
            String userId = "u" + i;
            attempts.add(() -> attemptBooking(showId, seatIds, userId));
        }
        List<BookingAttempt> results = runAll(pool, attempts);
        pool.shutdown();
        return results;
    }

    private List<BookingAttempt> runAll(ExecutorService pool, List<Callable<BookingAttempt>> attempts) throws Exception {
        List<Future<BookingAttempt>> futures = pool.invokeAll(attempts);
        List<BookingAttempt> results = new ArrayList<>();
        for (Future<BookingAttempt> future : futures) {
            results.add(future.get());
        }
        return results;
    }

    private BookingAttempt attemptBooking(Long showId, List<Long> seatIds, String userId) {
        var response = http.postForEntity(
                "/api/bookings",
                Map.of("userId", userId, "showId", showId, "seatIds", seatIds),
                Map.class);
        Long bookingId = response.getStatusCode().value() == 201
                ? ((Number) response.getBody().get("id")).longValue()
                : null;
        return new BookingAttempt(response.getStatusCode().value(), bookingId, userId);
    }

    private void confirmPayment(BookingAttempt booking) {
        paymentStatus(booking, booking.userId);
    }

    private int paymentStatus(BookingAttempt booking, String userId) {
        return http.postForEntity(
                "/api/payments/" + booking.bookingId + "/success",
                Map.of("userId", userId),
                Map.class
        ).getStatusCode().value();
    }

    private int cancelStatus(BookingAttempt booking, String userId) {
        return http.postForEntity(
                "/api/bookings/" + booking.bookingId + "/cancel",
                Map.of("userId", userId),
                Map.class
        ).getStatusCode().value();
    }

    private int createShowStatus(Long movieId, Long screenId, String startTime) {
        return http.postForEntity(
                "/api/shows",
                Map.of("movieId", movieId, "screenId", screenId, "startTime", startTime, "durationMinutes", 120),
                Map.class
        ).getStatusCode().value();
    }

    private <T> T resultOf(Future<T> future) {
        try {
            return future.get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Scenario createScenario(int seatCount) {
        Venue venue = createVenue(seatCount);
        Long showId = postForId(
                "/api/shows",
                Map.of(
                        "movieId", venue.movieId,
                        "screenId", venue.screenId,
                        "startTime", Instant.now().plusSeconds(3600).toString(),
                        "durationMinutes", 120));
        return new Scenario(showId, venue.seatIds);
    }

    private Venue createVenue(int seatCount) {
        Long theatreId = postForId("/api/theatres", Map.of("name", "Cinema" + UUID.randomUUID()));
        Long screenId = postForId("/api/theatres/" + theatreId + "/screens", Map.of("name", "Screen"));

        List<Long> seatIds = new ArrayList<>();
        for (int row = 1; row <= seatCount; row++) {
            seatIds.add(postForId("/api/screens/" + screenId + "/seats", Map.of("rowNumber", 1, "seatNumber", row)));
        }

        Long movieId = postForId("/api/movies", Map.of("name", "Movie" + UUID.randomUUID()));
        return new Venue(movieId, screenId, seatIds);
    }

    private Long postForId(String path, Map<String, ?> body) {
        Map<?, ?> response = http.postForObject(path, body, Map.class);
        return ((Number) response.get("id")).longValue();
    }

    private record Scenario(Long showId, List<Long> seatIds) {
    }

    private record Venue(Long movieId, Long screenId, List<Long> seatIds) {
    }

    private record BookingAttempt(int httpStatus, Long bookingId, String userId) {
    }
}
