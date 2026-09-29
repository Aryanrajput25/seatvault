package com.aryanrajput.seatvault.booking.repo;

import com.aryanrajput.seatvault.booking.domain.Seat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface Seats extends JpaRepository<Seat, Long> {

    List<Seat> findByScreenId(Long screenId);
}
