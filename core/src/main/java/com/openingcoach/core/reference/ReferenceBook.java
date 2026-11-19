package com.openingcoach.core.reference;

import com.openingcoach.core.Stats;

import java.util.List;
import java.util.Optional;

/** What strong players did from a position: used to compare a user's games against "theory". */
public interface ReferenceBook {

    /** A move strong players played from a position, with the results that followed. */
    record RefMove(String san, long childKey, Stats stats) {
    }

    /** Results of reference games that reached the position, or empty if none did. */
    Optional<Stats> position(long key);

    /** Moves played from the position, most popular first. Empty if the position is unknown. */
    List<RefMove> moves(long key);
}
