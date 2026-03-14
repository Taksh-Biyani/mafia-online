package com.mafia.game.room;

import com.mafia.game.model.GamePhase;
import com.mafia.game.model.Player;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a game room/lobby where players can join and play Mafia.
 * Contains information about the room state, players, and game configuration.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Room {
    /** Unique identifier for the room */
    private UUID id;
    
    /** Shareable join code that allows other players to join this room */
    private String joinCode;
    
    /** Current phase of the game in this room */
    private GamePhase phase;
    
    /** Minimum number of players required to start the game */
    private int minPlayers;
    
    /** Maximum number of players allowed in this room */
    private int maxPlayers;
    
    /** List of players currently in this room */
    @Builder.Default
    private List<Player> players = new ArrayList<>();
}
