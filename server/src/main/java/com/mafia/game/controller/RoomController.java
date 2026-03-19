package com.mafia.game.controller;

import com.mafia.game.api.CreateRoomRequest;
import com.mafia.game.api.CreateRoomResponse;
import com.mafia.game.api.JoinRoomRequest;
import com.mafia.game.api.JoinRoomResponse;
import com.mafia.game.model.Player;
import com.mafia.game.room.Room;
import com.mafia.game.service.CaptchaService;
import com.mafia.game.service.RoomService;
import com.mafia.game.websocket.RoomWebSocketHandler;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
    private final RoomWebSocketHandler wsHandler;
    private final CaptchaService captchaService;
    private final Map<String, Deque<Long>> joinAttempts = new ConcurrentHashMap<>();
    private final Map<String, Long> joinBlockedUntil = new ConcurrentHashMap<>();

    private static final int JOIN_WINDOW_MS = 5_000;
    private static final int JOIN_MAX_REQUESTS = 3;
    private static final int JOIN_BLOCK_MS = 10_000;

    public RoomController(RoomService roomService, RoomWebSocketHandler wsHandler, CaptchaService captchaService) {
        this.roomService = roomService;
        this.wsHandler = wsHandler;
        this.captchaService = captchaService;
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

    /** Always uses the direct TCP connection address — never trusts X-Forwarded-For to prevent rate limit spoofing. */
    private String getClientIp(HttpServletRequest request) {
        return request.getRemoteAddr();
    }

    /**
     * Creates a new game room with specified configuration and automatically joins the creator.
     * Returns 201 Created with the new room details, creator player, and the creator's bearer token.
     */
    @PostMapping
    public ResponseEntity<?> createRoom(@RequestBody CreateRoomRequest request, HttpServletRequest httpReq) {
        if (isJoinRateLimited(httpReq)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        }
        if (!captchaService.verify(request.getCaptchaToken(), getClientIp(httpReq))) {
            return ResponseEntity.badRequest().body("Invalid captcha");
        }
        Room room;
        try {
            room = roomService.createRoom(
                    request.getJoinCode(),
                    request.getMinPlayers(),
                    request.getMaxPlayers(),
                    request.getDayDurationSeconds(),
                    request.getMafiaCount(),
                    request.isPublicRoom());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }

        // Auto-join the creator to the room
        return roomService.joinRoom(room.getId(), request.getPlayerName())
                .map(creator -> {
                    room.setHostId(creator.getId());
                    String token = creator.getSecret();
                    return ResponseEntity.status(HttpStatus.CREATED)
                            .body((Object) new CreateRoomResponse(room, creator, token));
                })
                .orElseGet(() -> {
                    roomService.removeRoom(room.getId());
                    return ResponseEntity.badRequest().body("Username must be between 3 and 12 characters");
                });
    }

    /**
     * Lists all rooms currently in LOBBY phase (open for joining).
     */
    @GetMapping
    public List<Room> listRooms() {
        return roomService.listRooms();
    }

    /**
     * Retrieves a specific room by its ID.
     */
    @GetMapping("/{roomId}")
    public ResponseEntity<Room> getRoom(@PathVariable UUID roomId) {
        return roomService.getRoom(roomId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Adds a player to a room by room ID. Returns 200 OK with the player and their bearer token.
     */
    @PostMapping("/{roomId}/join")
    public ResponseEntity<?> joinRoom(@PathVariable UUID roomId, @RequestBody JoinRoomRequest request, HttpServletRequest httpReq) {
        if (isJoinRateLimited(httpReq)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        }
        if (!captchaService.verify(request.getCaptchaToken(), getClientIp(httpReq))) {
            return ResponseEntity.badRequest().body("Invalid captcha");
        }
        return roomService.joinRoom(roomId, request.getPlayerName())
                .map(p -> {
                    wsHandler.broadcast(roomId);
                    return ResponseEntity.ok((Object) new JoinRoomResponse(p, p.getSecret()));
                })
                .orElse(ResponseEntity.status(HttpStatus.BAD_REQUEST).build());
    }

    /**
     * Adds a player to a room using the room's join code. Returns 200 OK with the player and their bearer token.
     */
    @PostMapping("/join")
    public ResponseEntity<?> joinByCode(@RequestParam String code, @RequestBody JoinRoomRequest request, HttpServletRequest httpReq) {
        if (isJoinRateLimited(httpReq)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        }
        if (!captchaService.verify(request.getCaptchaToken(), getClientIp(httpReq))) {
            return ResponseEntity.badRequest().body("Invalid captcha");
        }
        return roomService.joinRoomByCode(code, request.getPlayerName())
                .map(p -> {
                    wsHandler.broadcast(p.getRoomId());
                    return ResponseEntity.ok((Object) new JoinRoomResponse(p, p.getSecret()));
                })
                .orElse(ResponseEntity.status(HttpStatus.BAD_REQUEST).build());
    }

    /**
     * Removes a player from a room. Requires the player's bearer token to prove ownership.
     */
    @PostMapping("/{roomId}/leave")
    public ResponseEntity<Void> leaveRoom(
            @PathVariable UUID roomId,
            @RequestParam UUID playerId,
            @RequestParam String playerToken) {
        if (roomService.verifyToken(roomId, playerId, playerToken).isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        boolean left = roomService.leaveRoom(roomId, playerId);
        if (left) wsHandler.broadcast(roomId);
        return left ? ResponseEntity.ok().build() : ResponseEntity.notFound().build();
    }
}
