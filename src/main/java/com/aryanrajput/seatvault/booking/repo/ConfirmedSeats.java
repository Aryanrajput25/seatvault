package com.aryanrajput.seatvault.booking.repo;

import com.aryanrajput.seatvault.booking.domain.ConfirmedShowSeat;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConfirmedSeats extends JpaRepository<ConfirmedShowSeat, Long> {

    boolean existsByShowIdAndSeatId(Long showId, Long seatId);
}
