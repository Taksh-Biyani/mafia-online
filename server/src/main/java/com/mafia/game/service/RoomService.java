package com.mafia.game.service;

import com.mafia.game.model.GamePhase;
import com.mafia.game.model.Player;
import com.mafia.game.room.Room;
import com.mafia.game.room.RoomManager;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Service layer for managing game rooms.
 * Handles room creation, player joins/leaves, room retrieval, and listing.
 */
@Service
public class RoomService {
    private final RoomManager roomManager;

    /**
     * Constructs RoomService with dependency on RoomManager.
     *
     * @param roomManager the manager for room operations
     */
    public RoomService(RoomManager roomManager) {
        this.roomManager = roomManager;
    }

    /**
     * Creates a new game room with specified parameters.
     *
     * @param joinCode the join code for the room (optional; auto-generated if blank)
     * @param minPlayers minimum players required to start the game
     * @param maxPlayers maximum players allowed in the room
     * @return the newly created Room
     */
    public Room createRoom(String joinCode, int minPlayers, int maxPlayers, int dayDurationSeconds) {
        return roomManager.createRoom(joinCode, minPlayers, maxPlayers, dayDurationSeconds);
    }

    /**
     * Retrieves a room by its unique ID.
     *
     * @param roomId the UUID of the room
     * @return Optional containing the room if found, empty otherwise
     */
    public Optional<Room> getRoom(UUID roomId) {
        return roomManager.getRoom(roomId);
    }

    /**
     * Retrieves a room by its join code.
     *
     * @param joinCode the join code to search for
     * @return Optional containing the room if found, empty otherwise
     */
    public Optional<Room> getRoomByJoinCode(String joinCode) {
        return roomManager.getRoomByJoinCode(joinCode);
    }

    /**
     * Lists all rooms that are currently in LOBBY phase (waiting for players).
     *
     * @return list of rooms in LOBBY phase
     */
    public List<Room> listRooms() {
        return roomManager.getAllRooms().values().stream()
                .filter(r -> r.getPhase() == GamePhase.LOBBY)
                .toList();
    }

    /**
     * Adds a player to a room by room ID.
     * Only allows joining if room is in LOBBY phase and has space for more players.
     * Automatically assigns CITIZEN role to new players.
     *
     * @param roomId the UUID of the room to join
     * @param playerName the name for the new player (defaults to "Player" if blank)
     * @return Optional containing the created Player if successful, empty if room is full or not in LOBBY
     */
    public Optional<Player> joinRoom(UUID roomId, String playerName) {
        return roomManager.getRoom(roomId)
                .filter(r -> r.getPhase() == GamePhase.LOBBY)
                .filter(r -> r.getPlayers().size() < r.getMaxPlayers())
                .map(room -> {
                    Player player = Player.builder()
                            .id(UUID.randomUUID())
                            .name(playerName != null && !playerName.isBlank() ? playerName : "Player")
                            .role(Player.Role.CITIZEN)
                            .alive(true)
                            .roomId(roomId)
                            .build();
                    room.getPlayers().add(player);
                    return player;
                });
    }

    /**
     * Adds a player to a room by join code.
     * Looks up the room using the join code and adds the player.
     *
     * @param joinCode the join code of the room
     * @param playerName the name for the new player
     * @return Optional containing the created Player if successful, empty if room not found or full
     */
    public Optional<Player> joinRoomByCode(String joinCode, String playerName) {
        return roomManager.getRoomByJoinCode(joinCode)
                .map(Room::getId)
                .flatMap(roomId -> joinRoom(roomId, playerName));
    }

    /**
     * Removes a player from a room.
     *
     * @param roomId the UUID of the room
     * @param playerId the UUID of the player to remove
     * @return true if the player was found and removed, false otherwise
     */
    public boolean leaveRoom(UUID roomId, UUID playerId) {
        return roomManager.getRoom(roomId)
                .map(room -> room.getPlayers().removeIf(p -> p.getId().equals(playerId)))
                .orElse(false);
    }

    /**
     * Completely removes a room and all its players from the system.
     *
     * @param roomId the UUID of the room to remove
     */
    public void removeRoom(UUID roomId) {
        roomManager.removeRoom(roomId);
    }
}
