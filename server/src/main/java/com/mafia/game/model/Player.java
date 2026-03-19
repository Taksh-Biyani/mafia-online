package com.mafia.game.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a player in the Mafia game.
 * Contains player information including identity, role, alive status, and room association.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Player {
    /** Unique identifier for the player */
    private UUID id;
    
    /** Player's display name */
    private String name;
    
    /** The role assigned to the player in the game (CITIZEN, MAFIA, DETECTIVE, DOCTOR) */
    private Role role;
    
    /** Flag indicating whether the player is still alive in the current game */
    private boolean alive;
    
    /** UUID of the room the player is currently in */
    private UUID roomId;

    /** Cryptographically random bearer token — never serialized in JSON responses */
    @JsonIgnore
    private String secret;

    /**
     * Enum representing the different roles a player can have in the Mafia game.
     * - CITIZEN: Regular townsperson, wins with other citizens
     * - MAFIA: Tries to eliminate citizens during night phases
     * - DETECTIVE: Can investigate a player each night
     * - DOCTOR: Can protect a player each night
     */
    public enum Role {
        /** A regular citizen, goal is to eliminate all mafia members */
        CITIZEN,
        
        /** A mafia member, goal is to reduce citizens to equal or fewer numbers */
        MAFIA,
        
        /** Can investigate players to learn their roles */
        DETECTIVE,
        
        /** Can protect players from being eliminated by mafia */
        DOCTOR
    }
}
