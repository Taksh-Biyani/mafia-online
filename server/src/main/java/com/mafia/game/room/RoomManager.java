package com.mafia.game.room;

import com.mafia.game.model.GamePhase;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Manages all active game rooms.
 * Handles room creation, lookup by ID or join code, and room removal.
 * Uses ConcurrentHashMap for thread-safe operations across multiple rooms.
 */
@Component
public class RoomManager {
    /** Map of room IDs to Room objects for quick lookup by UUID */
    private final Map<UUID, Room> roomsById = new ConcurrentHashMap<>();
    
    /** Map of join codes to Room objects for quick lookup by join code */
    private final Map<String, Room> roomsByJoinCode = new ConcurrentHashMap<>();

    /**
     * Creates a new game room with the specified configuration.
     * If no join code is provided, generates a 6-character code.
     *
     * @param joinCode the join code for the room (optional; auto-generated if blank)
     * @param minPlayers minimum players required to start the game
     * @param maxPlayers maximum players allowed in the room
     * @return the newly created Room
     */
    public Room createRoom(String joinCode, int minPlayers, int maxPlayers) {
        Room room = Room.builder()
                .id(UUID.randomUUID())
                .joinCode(joinCode != null && !joinCode.isBlank() ? joinCode : generateJoinCode())
                .phase(GamePhase.LOBBY)
                .minPlayers(minPlayers)
                .maxPlayers(maxPlayers)
                .build();
        roomsById.put(room.getId(), room);
        roomsByJoinCode.put(room.getJoinCode().toUpperCase(), room);
        return room;
    }

    /**
     * Retrieves a room by its unique ID.
     *
     * @param id the UUID of the room
     * @return Optional containing the room if found, empty otherwise
     */
    public Optional<Room> getRoom(UUID id) {
        return Optional.ofNullable(roomsById.get(id));
    }

    /**
     * Retrieves a room by its join code (case-insensitive).
     *
     * @param joinCode the join code to search for
     * @return Optional containing the room if found, empty otherwise
     */
    public Optional<Room> getRoomByJoinCode(String joinCode) {
        if (joinCode == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(roomsByJoinCode.get(joinCode.toUpperCase()));
    }

    /**
     * Removes a room by its ID.
     * Also removes the room from the join code lookup map.
     *
     * @param id the UUID of the room to remove
     */
    public void removeRoom(UUID id) {
        Room room = roomsById.remove(id);
        if (room != null) {
            roomsByJoinCode.remove(room.getJoinCode().toUpperCase());
        }
    }

    /**
     * Retrieves a copy of all rooms currently managed.
     *
     * @return an immutable map of all rooms indexed by ID
     */
    public Map<UUID, Room> getAllRooms() {
        return Map.copyOf(roomsById);
    }

    /**
     * Generates a random 6-character join code from a UUID.
     * Takes the first 6 characters of a UUID string and converts to uppercase.
     *
     * @return a random 6-character join code
     */
    private static String generateJoinCode() {
        return UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }
}
