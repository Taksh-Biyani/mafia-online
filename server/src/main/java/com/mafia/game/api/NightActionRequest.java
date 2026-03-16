package com.mafia.game.api;

import java.util.UUID;
import lombok.Data;

/**
 * Request DTO for submitting a night action during the game.
 * Used by mafia members to eliminate their target player at night.
 */
@Data
public class NightActionRequest {
    /** The UUID of the player being targeted for elimination by the mafia. */
    private UUID targetPlayerId;
}
