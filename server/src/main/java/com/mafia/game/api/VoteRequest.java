package com.mafia.game.api;

import java.util.UUID;
import lombok.Data;

/**
 * Request DTO for submitting a vote during the voting phase.
 * Players use this to vote for which player to eliminate during the day phase.
 */
@Data
public class VoteRequest {
    /** The UUID of the player being voted for elimination. */
    private UUID targetPlayerId;
}
