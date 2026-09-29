package com.aryanrajput.seatvault.booking.repo;

import com.aryanrajput.seatvault.booking.domain.Show;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface Shows extends JpaRepository<Show, Long> {

    List<Show> findByScreenId(Long screenId);
}
