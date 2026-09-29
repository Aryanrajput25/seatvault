package com.aryanrajput.seatvault.booking.repo;

import com.aryanrajput.seatvault.booking.domain.Screen;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface Screens extends JpaRepository<Screen, Long> {

    /**
     * Locks a screen's row so that two concurrent "create a show on this
     * screen" requests are serialized, preventing both from passing the
     * overlap check and creating conflicting shows.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Screen s where s.id = :id")
    Optional<Screen> lockById(@Param("id") Long id);
}
