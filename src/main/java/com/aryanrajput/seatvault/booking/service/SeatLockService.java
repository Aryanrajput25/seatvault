package com.aryanrajput.seatvault.booking.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

/**
 * A short-lived, best-effort distributed lock over seats, backed by Redis.
 *
 * <p>This is the <b>fast path</b> of the concurrency-control scheme: it
 * rejects almost all concurrent contention for the same seat in microseconds,
 * before a request ever needs to touch MySQL. It is intentionally not the
 * final authority on seat ownership — that role belongs to the
 * {@code show_seat_reservations} table, locked row-by-row in
 * {@link BookingService}. A lock in this class expiring early (Redis TTL)
 * can therefore never cause a double-booking; it can, at worst, let a second
 * request reach the database, where it will be correctly rejected.
 */
@Service
public class SeatLockService { //This is where the Redis locking logic lives.

    /**
     * Locks every key in {@code KEYS} only if none of them are already held.
     * Reading all keys before writing any of them, inside a single Lua
     * script, is what makes "lock this whole group of seats, or none of
     * them" atomic — Redis runs the whole script as one operation.
     */
    //I use a Redis Lua script so checking all requested seat-lock keys and setting them happens atomically. This gives an all-or-nothing lock for multi-seat bookings.
    //this is a redis lua script
    private static final String LOCK_ALL_SCRIPT =
            "for i, key in ipairs(KEYS) do "                         //Check every requested key
                    + "  if redis.call('exists', key) == 1 then "    //Is ANY key already present?
                    + "    return 0 "                                //YES → return 0
                    + "  end "
                    + "end "
                    + "for i, key in ipairs(KEYS) do "               //if No, Set ALL keys
                    + "  redis.call('set', key, ARGV[1], 'EX', ARGV[2]) "
                    + "end "
                    + "return 1";  //if redis can lock all the seats successfully then return 1

    /**
     * Deletes only the keys that are still owned by {@code ARGV[1]}. This
     * avoids the classic distributed-lock bug of blindly deleting a key that
     * has since been claimed by someone else (a plain GET-then-DELETE from
     * application code would not be atomic; doing both inside one Lua script
     * is).
     */
    private static final String RELEASE_OWNED_SCRIPT =
            "local removed = 0 "
                    + "for _, key in ipairs(KEYS) do "
                    + "  if redis.call('get', key) == ARGV[1] then "
                    + "    redis.call('del', key) "
                    + "    removed = removed + 1 "
                    + "  end "
                    + "end "
                    + "return removed";

    private final StringRedisTemplate redis;
    private final Duration lockTimeout;

    public SeatLockService(StringRedisTemplate redis, @Value("${booking.lock-timeout-seconds}") long lockTimeoutSeconds) {
        this.redis = redis;
        this.lockTimeout = Duration.ofSeconds(lockTimeoutSeconds);
    }

    /**
     * Attempts to lock every seat in {@code seatIds} for {@code bookingId}.
     *
     * @return {@code true} if all seats were free and are now locked;
     *         {@code false} if any seat was already locked, in which case
     *         none of the seats were touched.
     */
    public boolean lockAll(Long showId, List<Long> seatIds, Long bookingId) {
        List<String> keys = seatIds.stream()
                .map(seatId -> lockKey(showId, seatId))
                .toList();

        Long result = redis.execute(
                new DefaultRedisScript<>(LOCK_ALL_SCRIPT, Long.class),
                keys,
                String.valueOf(bookingId),
                String.valueOf(lockTimeout.toSeconds())
        );

        return Long.valueOf(1).equals(result);
    }

    /**
     * Releases the given seats, but only the ones still owned by
     * {@code bookingId}. Safe to call even if the lock has already expired
     * or was never acquired.
     */
    public void release(Long showId, List<Long> seatIds, Long bookingId) {
        List<String> keys = seatIds.stream()
                .map(seatId -> lockKey(showId, seatId))
                .toList();

        redis.execute(
                new DefaultRedisScript<>(RELEASE_OWNED_SCRIPT, Long.class),
                keys,
                String.valueOf(bookingId)
        );
    }

    public boolean isLocked(Long showId, Long seatId) {
        return Boolean.TRUE.equals(redis.hasKey(lockKey(showId, seatId)));
    }

    private String lockKey(Long showId, Long seatId) {
        return "seat-lock:" + showId + ":" + seatId;
    }
}
