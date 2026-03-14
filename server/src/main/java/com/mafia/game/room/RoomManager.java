package com.mafia.game.room;

import com.mafia.game.model.GamePhase;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class RoomManager {
    private final Map<UUID, Room> roomsById = new ConcurrentHashMap<>();
    private final Map<String, Room> roomsByJoinCode = new ConcurrentHashMap<>();

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

    public Optional<Room> getRoom(UUID id) {
        return Optional.ofNullable(roomsById.get(id));
    }

    public Optional<Room> getRoomByJoinCode(String joinCode) {
        return Optional.ofNullable(roomsByJoinCode.get(joinCode != null ? joinCode.toUpperCase() : null));
    }

    public void removeRoom(UUID id) {
        Room room = roomsById.remove(id);
        if (room != null) {
            roomsByJoinCode.remove(room.getJoinCode().toUpperCase());
        }
    }

    public Map<UUID, Room> getAllRooms() {
        return Map.copyOf(roomsById);
    }

    private static String generateJoinCode() {
        return UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }
}
