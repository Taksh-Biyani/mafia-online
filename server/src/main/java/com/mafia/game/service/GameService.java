package com.mafia.game.service;

import com.mafia.game.model.GamePhase;
import com.mafia.game.model.Player;
import com.mafia.game.room.Room;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Service layer for managing game logic and state transitions.
 * Handles game start, night actions, voting, role assignment, and win condition checks.
 */
@Service
public class GameService {
    private final RoomService roomService;

    /**
     * Constructs GameService with dependency on RoomService.
     *
     * @param roomService the service for room management
     */
    public GameService(RoomService roomService) {
        this.roomService = roomService;
    }

    /**
     * Starts a game in the specified room.
     * Requires the room to be in LOBBY phase and have at least minPlayers.
     * Assigns roles to all players and transitions room to NIGHT phase.
     *
     * @param roomId the UUID of the room
     * @return Optional containing the updated room if successful, empty if conditions not met
     */
    public Optional<Room> startGame(UUID roomId) {
        return roomService.getRoom(roomId)
                .filter(r -> r.getPhase() == GamePhase.LOBBY)
                .filter(r -> r.getPlayers().size() >= r.getMinPlayers())
                .map(room -> {
                    assignRoles(room);
                    room.setPhase(GamePhase.NIGHT);
                    return room;
                });
    }

    /**
     * Records the mafia's kill target for the current night.
     * Does not immediately kill — resolution happens when all night actors have submitted (or night/end is called).
     *
     * @param roomId the UUID of the room
     * @param playerId the UUID of the mafia player performing the action
     * @param targetPlayerId the UUID of the target player to eliminate
     * @return Optional containing the updated room if successful, empty if conditions not met
     */
    public Optional<Room> submitNightAction(UUID roomId, UUID playerId, UUID targetPlayerId) {
        return roomService.getRoom(roomId)
                .filter(r -> r.getPhase() == GamePhase.NIGHT)
                .flatMap(room -> findPlayer(room, playerId)
                        .filter(p -> p.getRole() == Player.Role.MAFIA && p.isAlive())
                        .map(mafia -> {
                            room.setMafiaTargetId(targetPlayerId);
                            room.getNightActors().add("MAFIA");
                            checkAutoEndNight(room);
                            return room;
                        }));
    }

    /**
     * Records the doctor's protection target for the current night.
     * If the mafia's target matches the protected player, the kill is blocked.
     *
     * @param roomId the UUID of the room
     * @param playerId the UUID of the doctor player
     * @param targetPlayerId the UUID of the player to protect
     * @return Optional containing the updated room if successful, empty if conditions not met
     */
    public Optional<Room> submitDoctorProtect(UUID roomId, UUID playerId, UUID targetPlayerId) {
        return roomService.getRoom(roomId)
                .filter(r -> r.getPhase() == GamePhase.NIGHT)
                .flatMap(room -> findPlayer(room, playerId)
                        .filter(p -> p.getRole() == Player.Role.DOCTOR && p.isAlive())
                        .map(doctor -> {
                            room.setDoctorProtectedId(targetPlayerId);
                            room.getNightActors().add("DOCTOR");
                            checkAutoEndNight(room);
                            return room;
                        }));
    }

    /**
     * Handles the detective's investigation of a target player.
     * Returns the target player so the detective can learn their role.
     * Auto-ends the night if all night actors have submitted.
     *
     * @param roomId the UUID of the room
     * @param playerId the UUID of the detective player
     * @param targetPlayerId the UUID of the player to investigate
     * @return Optional containing the investigated player if successful, empty if conditions not met
     */
    public Optional<Player> submitDetectiveInvestigate(UUID roomId, UUID playerId, UUID targetPlayerId) {
        return roomService.getRoom(roomId)
                .filter(r -> r.getPhase() == GamePhase.NIGHT)
                .flatMap(room -> findPlayer(room, playerId)
                        .filter(p -> p.getRole() == Player.Role.DETECTIVE && p.isAlive())
                        .flatMap(detective -> {
                            room.getNightActors().add("DETECTIVE");
                            checkAutoEndNight(room);
                            return findPlayer(room, targetPlayerId);
                        }));
    }

    /**
     * Manually ends the night phase, resolving all pending night actions and transitioning to DAY.
     *
     * @param roomId the UUID of the room
     * @return Optional containing the updated room if successful, empty if not in NIGHT phase
     */
    public Optional<Room> endNight(UUID roomId) {
        return roomService.getRoom(roomId)
                .filter(r -> r.getPhase() == GamePhase.NIGHT)
                .map(room -> {
                    resolveNight(room);
                    return room;
                });
    }

    private void checkAutoEndNight(Room room) {
        if (room.getPhase() != GamePhase.NIGHT) return;
        Set<String> expected = new HashSet<>();
        expected.add("MAFIA");
        if (room.getAlivePlayers().stream().anyMatch(p -> p.getRole() == Player.Role.DETECTIVE)) {
            expected.add("DETECTIVE");
        }
        if (room.getAlivePlayers().stream().anyMatch(p -> p.getRole() == Player.Role.DOCTOR)) {
            expected.add("DOCTOR");
        }
        if (room.getNightActors().containsAll(expected)) {
            resolveNight(room);
        }
    }

    private void resolveNight(Room room) {
        UUID targetId = room.getMafiaTargetId();
        UUID protectedId = room.getDoctorProtectedId();
        if (targetId != null && !targetId.equals(protectedId)) {
            findPlayer(room, targetId).ifPresent(p -> p.setAlive(false));
        }
        room.setMafiaTargetId(null);
        room.setDoctorProtectedId(null);
        room.getNightActors().clear();
        room.setPhase(GamePhase.DAY);
    }

    /**
     * Transitions the room from DAY to VOTING phase.
     * Only the host can call this. Clears any leftover votes from previous rounds.
     *
     * @param roomId   the UUID of the room
     * @param playerId the UUID of the player attempting to end the day (must be host)
     * @return Optional containing the updated room if successful, empty if not host or wrong phase
     */
    public Optional<Room> endDay(UUID roomId, UUID playerId) {
        return roomService.getRoom(roomId)
                .filter(r -> playerId.equals(r.getHostId()))
                .filter(r -> r.getPhase() == GamePhase.DAY)
                .map(room -> {
                    room.getVotes().clear();
                    room.setPhase(GamePhase.VOTING);
                    return room;
                });
    }

    /**
     * Submits a vote during the VOTING phase.
     * Stores the vote (one per player; subsequent calls are ignored).
     * Auto-resolves voting once all alive players have cast their vote.
     *
     * @param roomId         the UUID of the room
     * @param voterId        the UUID of the player voting
     * @param targetPlayerId the UUID of the player being voted for
     * @return Optional containing the updated room if successful, empty if not in VOTING phase
     */
    public Optional<Room> submitVote(UUID roomId, UUID voterId, UUID targetPlayerId) {
        return roomService.getRoom(roomId)
                .filter(r -> r.getPhase() == GamePhase.VOTING)
                .flatMap(room -> {
                    // Ignore double votes
                    if (room.getVotes().containsKey(voterId)) {
                        return Optional.of(room);
                    }
                    room.getVotes().put(voterId, targetPlayerId);
                    // Auto-resolve once every alive player has voted
                    if (room.getVotes().size() >= room.getAlivePlayers().size()) {
                        return resolveVoting(roomId);
                    }
                    return Optional.of(room);
                });
    }

    /**
     * Resolves voting by eliminating the player with the most votes.
     * Reads votes from the room's stored vote map.
     * Transitions to ENDED if a win condition is met, otherwise back to NIGHT.
     *
     * @param roomId the UUID of the room
     * @return Optional containing the updated room if successful, empty if not in VOTING phase
     */
    public Optional<Room> resolveVoting(UUID roomId) {
        return roomService.getRoom(roomId)
                .filter(r -> r.getPhase() == GamePhase.VOTING)
                .map(room -> {
                    Map<UUID, Long> tally = room.getVotes().values().stream()
                            .collect(Collectors.groupingBy(id -> id, Collectors.counting()));
                    Optional<UUID> lynched = tally.entrySet().stream()
                            .max(Map.Entry.comparingByValue())
                            .filter(e -> e.getValue() > 0)
                            .map(Map.Entry::getKey);
                    lynched.flatMap(id -> findPlayer(room, id)).ifPresent(p -> p.setAlive(false));
                    room.getVotes().clear();
                    Optional<Player.Role> win = checkWinCondition(room);
                    if (win.isPresent()) {
                        room.setPhase(GamePhase.ENDED);
                        room.setWinner(win.get());
                    } else {
                        room.setPhase(GamePhase.NIGHT);
                        room.getNightActors().clear();
                    }
                    return room;
                });
    }

    /**
     * Checks if there is a win condition met in the current game state.
     * Citizens win if all mafia are eliminated.
     * Mafia wins if their count equals or exceeds the count of non-mafia players.
     *
     * @param room the room to check win condition for
     * @return Optional containing the winning role if a win condition is met, empty otherwise
     */
    public Optional<Player.Role> checkWinCondition(Room room) {
        long mafiaAlive = room.getPlayers().stream()
                .filter(Player::isAlive)
                .filter(p -> p.getRole() == Player.Role.MAFIA)
                .count();
        long citizensAlive = room.getPlayers().stream()
                .filter(Player::isAlive)
                .filter(p -> p.getRole() != Player.Role.MAFIA)
                .count();
        if (mafiaAlive == 0) return Optional.of(Player.Role.CITIZEN);
        if (mafiaAlive >= citizensAlive) return Optional.of(Player.Role.MAFIA);
        return Optional.empty();
    }

    /**
     * Retrieves a room by its ID.
     *
     * @param roomId the UUID of the room
     * @return Optional containing the room if found, empty otherwise
     */
    public Optional<Room> getRoom(UUID roomId) {
        return roomService.getRoom(roomId);
    }

    /**
     * Retrieves a specific player from a room.
     *
     * @param roomId the UUID of the room
     * @param playerId the UUID of the player
     * @return Optional containing the player if found, empty otherwise
     */
    public Optional<Player> getPlayer(UUID roomId, UUID playerId) {
        return roomService.getRoom(roomId).flatMap(room -> findPlayer(room, playerId));
    }

    /**
     * Assigns roles to all players in a room.
     * Mafia count is calculated as size/4 (minimum 1).
     * Distribution: first mafiaCount get MAFIA role, then DETECTIVE (if >= 5 players),
     * then DOCTOR (if >= 6 players), remaining get CITIZEN role.
     * Players are shuffled before role assignment to ensure randomness.
     *
     * @param room the room whose players should be assigned roles
     */
    private void assignRoles(Room room) {
        List<Player> players = room.getPlayers();
        Collections.shuffle(players);
        int mafiaCount = Math.max(1, players.size() / 4);
        for (int i = 0; i < players.size(); i++) {
            Player p = players.get(i);
            p.setAlive(true);
            if (i < mafiaCount) {
                p.setRole(Player.Role.MAFIA);
            } else if (i == mafiaCount && players.size() >= 5) {
                p.setRole(Player.Role.DETECTIVE);
            } else if (i == mafiaCount + 1 && players.size() >= 6) {
                p.setRole(Player.Role.DOCTOR);
            } else {
                p.setRole(Player.Role.CITIZEN);
            }
        }
    }

    /**
     * Finds a player in a room by their ID.
     *
     * @param room the room to search in
     * @param playerId the UUID of the player to find
     * @return Optional containing the player if found, empty otherwise
     */
    private Optional<Player> findPlayer(Room room, UUID playerId) {
        return room.getPlayers().stream()
                .filter(p -> p.getId().equals(playerId))
                .findFirst();
    }
}
