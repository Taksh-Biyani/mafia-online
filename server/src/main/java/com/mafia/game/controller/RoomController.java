package com.mafia.game.controller;

import com.mafia.game.api.CreateRoomRequest;
import com.mafia.game.api.CreateRoomResponse;
import com.mafia.game.api.JoinRoomRequest;
import com.mafia.game.model.Player;
import com.mafia.game.room.Room;
import com.mafia.game.service.RoomService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for managing game rooms.
 * Handles room creation, listing, joining, and player management.
 */
@RestController
@RequestMapping("/api/rooms")
public class RoomController {
    private final RoomService roomService;
    private final Map<String, Deque<Long>> joinAttempts = new ConcurrentHashMap<>();
    private final Map<String, Long> joinBlockedUntil = new ConcurrentHashMap<>();

    private static final int JOIN_WINDOW_MS = 5_000;
    private static final int JOIN_MAX_REQUESTS = 3;
    private static final int JOIN_BLOCK_MS = 10_000;

    public RoomController(RoomService roomService) {
        this.roomService = roomService;
    }

    /** Returns true if this IP has exceeded the join rate limit. Tracks a sliding 5s window; blocks for 10s at 4+ requests. */
    private boolean isJoinRateLimited(HttpServletRequest httpReq) {
        String ip = getClientIp(httpReq);
        long now = System.currentTimeMillis();
        Long blockedUntil = joinBlockedUntil.get(ip);
        if (blockedUntil != null && now < blockedUntil) return true;

        Deque<Long> window = joinAttempts.computeIfAbsent(ip, k -> new ArrayDeque<>());
        synchronized (window) {
            window.addLast(now);
            while (!window.isEmpty() && now - window.peekFirst() > JOIN_WINDOW_MS) {
                window.pollFirst();
            }
            if (window.size() > JOIN_MAX_REQUESTS) {
                joinBlockedUntil.put(ip, now + JOIN_BLOCK_MS);
                window.clear();
                return true;
            }
        }
        return false;
    }

    private String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * Creates a new game room with specified configuration and automatically joins the creator.
     * Returns 201 Created with the new room details and creator player information.
     *
     * @param request contains join code (optional), player name, minPlayers, and maxPlayers
     * @return ResponseEntity containing the CreateRoomResponse with 201 status
     */
    @PostMapping
    public ResponseEntity<?> createRoom(@RequestBody CreateRoomRequest request) {
        Room room;
        try {
            room = roomService.createRoom(
                    request.getJoinCode(),
                    request.getMinPlayers(),
                    request.getMaxPlayers(),
                    request.getDayDurationSeconds(),
                    request.getMafiaCount());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }

        // Auto-join the creator to the room
        return roomService.joinRoom(room.getId(), request.getPlayerName())
                .map(creator -> {
                    room.setHostId(creator.getId());
                    return ResponseEntity.status(HttpStatus.CREATED)
                            .body((Object) new CreateRoomResponse(room, creator));
                })
                .orElseGet(() -> {
                    roomService.removeRoom(room.getId());
                    return ResponseEntity.badRequest().body("Username must be between 3 and 12 characters");
                });
    }

    /**
     * Lists all rooms currently in LOBBY phase (open for joining).
     * Returns 200 OK with list of available rooms.
     *
     * @return list of rooms in LOBBY phase
     */
    @GetMapping
    public List<Room> listRooms() {
        return roomService.listRooms();
    }

    /**
     * Retrieves a specific room by its ID.
     * Returns 200 OK with room details or 404 Not Found if room doesn't exist.
     *
     * @param roomId the UUID of the room to retrieve
     * @return ResponseEntity containing the Room or 404 error
     */
    @GetMapping("/{roomId}")
    public ResponseEntity<Room> getRoom(@PathVariable UUID roomId) {
        return roomService.getRoom(roomId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Adds a player to a room by room ID.
     * Returns 200 OK with the created player or 400 Bad Request if room is full or not in LOBBY.
     *
     * @param roomId the UUID of the room to join
     * @param request contains the player name
     * @return ResponseEntity containing the created Player or 400 error
     */
    @PostMapping("/{roomId}/join")
    public ResponseEntity<Player> joinRoom(@PathVariable UUID roomId, @RequestBody JoinRoomRequest request, HttpServletRequest httpReq) {
        if (isJoinRateLimited(httpReq)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        }
        return roomService.joinRoom(roomId, request.getPlayerName())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.status(HttpStatus.BAD_REQUEST).build());
    }

    /**
     * Adds a player to a room using the room's join code.
     * Returns 200 OK with the created player or 400 Bad Request if room not found or full.
     *
     * @param code the join code of the room (query parameter)
     * @param request contains the player name
     * @return ResponseEntity containing the created Player or 400 error
     */
    @PostMapping("/join")
    public ResponseEntity<Player> joinByCode(@RequestParam String code, @RequestBody JoinRoomRequest request, HttpServletRequest httpReq) {
        if (isJoinRateLimited(httpReq)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        }
        return roomService.joinRoomByCode(code, request.getPlayerName())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.status(HttpStatus.BAD_REQUEST).build());
    }

    /**
     * Removes a player from a room.
     * Returns 200 OK on success or 404 Not Found if player or room doesn't exist.
     *
     * @param roomId the UUID of the room
     * @param playerId the UUID of the player to remove (query parameter)
     * @return ResponseEntity with 200 OK or 404 error
     */
    @PostMapping("/{roomId}/leave")
    public ResponseEntity<Void> leaveRoom(@PathVariable UUID roomId, @RequestParam UUID playerId) {
        return roomService.leaveRoom(roomId, playerId)
                ? ResponseEntity.ok().build()
                : ResponseEntity.notFound().build();
    }

    /**
     * Completely deletes a room and all its players.
     * Returns 204 No Content on successful deletion.
     *
     * @param roomId the UUID of the room to delete
     * @return ResponseEntity with 204 No Content status
     */
    @DeleteMapping("/{roomId}")
    public ResponseEntity<Void> deleteRoom(@PathVariable UUID roomId) {
        roomService.removeRoom(roomId);
        return ResponseEntity.noContent().build();
    }
}
