package com.aryanrajput.seatvault.booking.repo;

import com.aryanrajput.seatvault.booking.domain.Movie;
import org.springframework.data.jpa.repository.JpaRepository;

public interface Movies extends JpaRepository<Movie, Long> {
}
