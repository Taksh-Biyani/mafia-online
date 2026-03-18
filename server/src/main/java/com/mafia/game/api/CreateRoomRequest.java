package com.mafia.game.api;

import lombok.Data;

/**
 * Request DTO for creating a new game room.
 * Contains configuration for room initialization including player limits and join code.
 */
@Data
public class CreateRoomRequest {
    /** Optional custom join code for the room. If not provided, a random 6-character code is generated. */
    private String joinCode;
    
    /** Name of the player creating the room. This player will be automatically added to the room. */
    private String playerName;
    
    /** Minimum number of players required to start the game. Defaults to 4. */
    private int minPlayers = 4;
    
    /** Maximum number of players allowed in the room. Defaults to 12. */
    private int maxPlayers = 12;

    /** Duration of the day discussion phase in seconds. Defaults to 30. */
    private int dayDurationSeconds = 30;

    /** Number of mafia players in the game. Defaults to 1. Max 2 if ≥6 players, max 3 if ≥8 players. */
    private int mafiaCount = 1;
}
