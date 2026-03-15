package com.mafia.game.room;

import com.mafia.game.model.GamePhase;
import com.mafia.game.model.Player;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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

    /**
     * Checks if the room has reached its maximum player capacity.
     *
     * @return true if the room is full, false otherwise
     */
    public boolean isFull() {
        return players.size() >= maxPlayers;
    }

    /**
     * Checks if the room has enough players to start the game.
     *
     * @return true if the minimum player requirement is met, false otherwise
     */
    public boolean canStartGame() {
        return players.size() >= minPlayers;
    }

    /**
     * Gets the count of players currently alive in the room.
     *
     * @return the number of alive players
     */
    public int getAlivePlayerCount() {
        return (int) players.stream().filter(Player::isAlive).count();
    }

    /**
     * Gets the count of players with a specific role who are currently alive.
     *
     * @param role the role to count
     * @return the number of alive players with the specified role
     */
    public int getAlivePlayerCountByRole(Player.Role role) {
        return (int) players.stream()
                .filter(Player::isAlive)
                .filter(p -> p.getRole() == role)
                .count();
    }

    /**
     * Gets a list of all players who are currently alive.
     *
     * @return list of alive players
     */
    public List<Player> getAlivePlayers() {
        return players.stream().filter(Player::isAlive).toList();
    }

    /**
     * Finds a player in the room by their unique ID.
     *
     * @param playerId the UUID of the player to find
     * @return Optional containing the player if found, empty otherwise
     */
    public Optional<Player> findPlayer(UUID playerId) {
        return players.stream()
                .filter(p -> p.getId().equals(playerId))
                .findFirst();
    }

    /**
     * Checks if a player with the given ID is in this room.
     *
     * @param playerId the UUID of the player to check
     * @return true if the player is in the room, false otherwise
     */
    public boolean hasPlayer(UUID playerId) {
        return players.stream().anyMatch(p -> p.getId().equals(playerId));
    }

    /**
     * Gets the total number of players currently in the room.
     *
     * @return the current player count
     */
    public int getCurrentPlayerCount() {
        return players.size();
    }
}
