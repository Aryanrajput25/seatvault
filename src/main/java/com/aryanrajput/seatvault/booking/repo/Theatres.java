package com.aryanrajput.seatvault.booking.repo;

import com.aryanrajput.seatvault.booking.domain.Theatre;
import org.springframework.data.jpa.repository.JpaRepository;

public interface Theatres extends JpaRepository<Theatre, Long> {
}
