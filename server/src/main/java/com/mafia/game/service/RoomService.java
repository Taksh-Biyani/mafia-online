package com.mafia.game.service;

import com.mafia.game.model.ChatMessage;
import com.mafia.game.model.GamePhase;
import com.mafia.game.model.Player;
import com.mafia.game.room.Room;
import com.mafia.game.room.RoomManager;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Service layer for managing game rooms.
 * Handles room creation, player joins/leaves, room retrieval, and listing.
 */
@Service
public class RoomService {
    private final RoomManager roomManager;
    private final Map<UUID, Long> lastMessageTime = new ConcurrentHashMap<>();

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
    public Room createRoom(String joinCode, int minPlayers, int maxPlayers, int dayDurationSeconds, int mafiaCount) {
        return roomManager.createRoom(joinCode, minPlayers, maxPlayers, dayDurationSeconds, mafiaCount);
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
        String name = (playerName != null && !playerName.isBlank()) ? playerName.trim() : "";
        if (name.length() < 3 || name.length() > 12) {
            return Optional.empty();
        }
        return roomManager.getRoom(roomId)
                .filter(r -> r.getPhase() == GamePhase.LOBBY)
                .filter(r -> r.getPlayers().size() < r.getMaxPlayers())
                .map(room -> {
                    boolean nameTaken = room.getPlayers().stream()
                            .anyMatch(p -> p.getName().equalsIgnoreCase(name));
                    if (nameTaken) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already taken in this room");
                    }
                    Player player = Player.builder()
                            .id(UUID.randomUUID())
                            .name(name)
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
                .map(room -> {
                    boolean removed = room.getPlayers().removeIf(p -> p.getId().equals(playerId));
                    if (removed && room.getPlayers().isEmpty()) {
                        roomManager.removeRoom(roomId);
                    }
                    return removed;
                })
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

    /**
     * Posts a chat message from a player. Enforces rate limiting and channel access rules.
     * Throws ResponseStatusException (400/403/404/429) on validation failure.
     */
    public ChatMessage postMessage(UUID roomId, UUID playerId, String text) {
        if (text == null || text.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Message cannot be empty");
        }
        if (text.length() > 200) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Message too long");
        }

        long now = System.currentTimeMillis();
        Long last = lastMessageTime.get(playerId);
        if (last != null && now - last < 1500) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Slow down!");
        }

        Room room = roomManager.getRoom(roomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        Player player = room.findPlayer(playerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        boolean isNightPhase = room.getPhase() == GamePhase.NIGHT_MAFIA
                || room.getPhase() == GamePhase.NIGHT_DOCTOR
                || room.getPhase() == GamePhase.NIGHT_DETECTIVE;
        ChatMessage.Channel channel;
        if (!player.isAlive()) {
            channel = ChatMessage.Channel.DEAD;
        } else if (player.getRole() == Player.Role.MAFIA && room.getPhase() == GamePhase.NIGHT_MAFIA) {
            channel = ChatMessage.Channel.MAFIA_NIGHT;
        } else if (isNightPhase) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can't chat right now");
        } else {
            channel = ChatMessage.Channel.GENERAL;
        }

        lastMessageTime.put(playerId, now);
        ChatMessage msg = new ChatMessage(player.getName(), text.trim(), now, channel);
        room.addMessage(msg);
        return msg;
    }

    /**
     * Returns the chat messages visible to the given player.
     * Dead players see GENERAL + DEAD. Alive MAFIA see MAFIA_NIGHT. Others see GENERAL only.
     */
    public List<ChatMessage> getMessages(UUID roomId, UUID playerId) {
        Room room = roomManager.getRoom(roomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        Player player = room.findPlayer(playerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        return room.getChatMessages().stream()
                .filter(m -> canSeeChannel(player, m.channel()))
                .toList();
    }

    private boolean canSeeChannel(Player player, ChatMessage.Channel channel) {
        if (!player.isAlive()) {
            return channel == ChatMessage.Channel.GENERAL || channel == ChatMessage.Channel.DEAD;
        }
        if (channel == ChatMessage.Channel.GENERAL) return true;
        if (channel == ChatMessage.Channel.MAFIA_NIGHT) {
            return player.getRole() == Player.Role.MAFIA;
        }
        return false; // DEAD channel invisible to alive players
    }
}
