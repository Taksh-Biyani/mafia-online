package com.mafia.game.room;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.mafia.game.model.ChatMessage;
import com.mafia.game.model.GamePhase;
import com.mafia.game.model.Player;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
    
    /** List of players currently in this room. Role is hidden here — each player fetches their own role via /players/{id}. */
    @JsonIgnoreProperties({"role"})
    @Builder.Default
    private List<Player> players = new ArrayList<>();

    /** UUID of the player who created the room; only they can start the game */
    private UUID hostId;

    /** Duration of the day discussion phase in seconds (host-configurable, default 30) */
    private int dayDurationSeconds;

    /** Number of mafia players assigned when the game starts (host-configurable, default 1) */
    private int mafiaCount;

    /** Whether the room appears in the public browse list; private rooms require a join code */
    @Builder.Default
    private boolean publicRoom = true;

    /** Votes cast during the voting phase: voterId -> targetPlayerId */
    @Builder.Default
    private Map<UUID, UUID> votes = new HashMap<>();

    /** Per-mafia votes for the current night: mafiaPlayerId -> targetPlayerId. @JsonIgnore hides targets from non-mafia. */
    @JsonIgnore
    @Builder.Default
    private Map<UUID, UUID> mafiaVotes = new HashMap<>();

    /** ID of the doctor's chosen protection target for the current night (null until doctor acts) */
    private UUID doctorProtectedId;

    /** Roles that have submitted their night action this night (used for auto-end-night detection) */
    @Builder.Default
    private Set<String> nightActors = new HashSet<>();

    /** Winning role when the game ends: CITIZEN means town wins, MAFIA means mafia wins (null while game is ongoing) */
    private Player.Role winner;

    /** Players who clicked "Play Again" on the end screen */
    @Builder.Default
    private Set<UUID> playAgainVotes = new HashSet<>();

    /** Players who clicked "Return to Lobby" on the end screen (will leave shortly) */
    @Builder.Default
    private Set<UUID> leaveVotes = new HashSet<>();

    /** The player the doctor protected last night; doctor cannot protect the same player two nights in a row */
    private UUID lastDoctorProtectedId;

    /** Players who chose to skip their vote during the voting phase */
    @Builder.Default
    private Set<UUID> voteSkips = new HashSet<>();

    /** Mafia players who chose to skip their kill vote this night (hidden from JSON) */
    @JsonIgnore
    @Builder.Default
    private Set<UUID> mafiaSkips = new HashSet<>();

    /** Chat history for this room, capped at 200 messages. Not included in room JSON — fetched separately per-player. */
    @JsonIgnore
    @Builder.Default
    private List<ChatMessage> chatMessages = new ArrayList<>();

    /**
     * Adds a chat message, evicting the oldest if the cap of 200 is reached.
     */
    public void addMessage(ChatMessage m) {
        if (chatMessages.size() >= 200) {
            chatMessages.remove(0);
        }
        chatMessages.add(m);
    }

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

    /**
     * Returns how many mafia players have submitted a night vote this round.
     * Safe to expose in JSON — reveals count only, never the targets.
     */
    @JsonProperty("mafiaVoteCount")
    public int getMafiaVoteCount() {
        return mafiaVotes.size() + mafiaSkips.size();
    }
}
