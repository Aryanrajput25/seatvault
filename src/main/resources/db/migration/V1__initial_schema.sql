-- Venue hierarchy: theatre -> screen -> seat.
-- this Creates initial database structure

CREATE TABLE theatre (
    id   BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL
);

CREATE TABLE screen (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    name       VARCHAR(255) NOT NULL,
    theatre_id BIGINT NOT NULL,
    CONSTRAINT fk_screen_theatre FOREIGN KEY (theatre_id) REFERENCES theatre (id)
);

CREATE TABLE seat (
                      id          BIGINT AUTO_INCREMENT PRIMARY KEY,
                      screen_id   BIGINT NOT NULL,
                      row_num     INT NOT NULL,
                      seat_number INT NOT NULL,
                      CONSTRAINT fk_seat_screen FOREIGN KEY (screen_id) REFERENCES screen (id),
                      CONSTRAINT uq_screen_position UNIQUE (screen_id, row_num, seat_number)
);

-- Catalogue.

CREATE TABLE movie (
    id    BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(255) NOT NULL
);

-- Named `shows`, not `show`, because SHOW is a reserved word in MySQL.
CREATE TABLE shows (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    movie_id         BIGINT NOT NULL,
    screen_id        BIGINT NOT NULL,
    start_time       TIMESTAMP(6) NOT NULL,
    duration_minutes INT NOT NULL,
    CONSTRAINT fk_show_movie FOREIGN KEY (movie_id) REFERENCES movie (id),
    CONSTRAINT fk_show_screen FOREIGN KEY (screen_id) REFERENCES screen (id)
);

-- Bookings.

CREATE TABLE bookings (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    show_id           BIGINT NOT NULL,
    user_id           VARCHAR(255) NOT NULL,
    status            VARCHAR(20) NOT NULL,
    created_at        TIMESTAMP(6) NOT NULL,
    payment_failures  INT NOT NULL DEFAULT 0,
    CONSTRAINT fk_booking_show FOREIGN KEY (show_id) REFERENCES shows (id)
);

-- Join table for the seats included in a booking.
CREATE TABLE booking_seats (
    booking_id BIGINT NOT NULL,
    seat_id    BIGINT NOT NULL,
    PRIMARY KEY (booking_id, seat_id),
    CONSTRAINT fk_booking_seat_booking FOREIGN KEY (booking_id) REFERENCES bookings (id),
    CONSTRAINT fk_booking_seat_seat FOREIGN KEY (seat_id) REFERENCES seat (id)
);
