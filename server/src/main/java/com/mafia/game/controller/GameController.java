package com.mafia.game.controller;

import com.mafia.game.api.ChatMessageRequest;
import com.mafia.game.api.NightActionRequest;
import com.mafia.game.api.VoteRequest;
import com.mafia.game.model.ChatMessage;
import com.mafia.game.model.Player;
import com.mafia.game.room.Room;
import com.mafia.game.service.GameService;
import com.mafia.game.service.RoomService;
import com.mafia.game.websocket.RoomWebSocketHandler;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rooms/{roomId}")
public class GameController {
    private final GameService gameService;
    private final RoomService roomService;
    private final RoomWebSocketHandler wsHandler;

    public GameController(GameService gameService, RoomService roomService, RoomWebSocketHandler wsHandler) {
        this.gameService = gameService;
        this.roomService = roomService;
        this.wsHandler = wsHandler;
    }

    @PostMapping("/start")
    public ResponseEntity<Room> startGame(@PathVariable UUID roomId, @RequestParam UUID playerId) {
        return gameService.getRoom(roomId)
                .map(room -> {
                    if (!playerId.equals(room.getHostId())) {
                        return ResponseEntity.status(HttpStatus.FORBIDDEN).<Room>build();
                    }
                    return gameService.startGame(roomId)
                            .map(r -> { wsHandler.broadcast(roomId); return ResponseEntity.ok(r); })
                            .orElse(ResponseEntity.badRequest().build());
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/state")
    public ResponseEntity<Room> getState(@PathVariable UUID roomId) {
        return gameService.getRoom(roomId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/night/action")
    public ResponseEntity<Room> nightAction(
            @PathVariable UUID roomId,
            @RequestParam UUID playerId,
            @RequestBody NightActionRequest request) {
        return gameService.submitNightAction(roomId, playerId, request.getTargetPlayerId())
                .map(r -> { wsHandler.broadcast(roomId); return ResponseEntity.ok(r); })
                .orElse(ResponseEntity.badRequest().build());
    }

    @PostMapping("/night/protect")
    public ResponseEntity<Room> doctorProtect(
            @PathVariable UUID roomId,
            @RequestParam UUID playerId,
            @RequestBody NightActionRequest request) {
        return gameService.submitDoctorProtect(roomId, playerId, request.getTargetPlayerId())
                .map(r -> { wsHandler.broadcast(roomId); return ResponseEntity.ok(r); })
                .orElse(ResponseEntity.badRequest().build());
    }

    @PostMapping("/night/investigate")
    public ResponseEntity<Player> detectiveInvestigate(
            @PathVariable UUID roomId,
            @RequestParam UUID playerId,
            @RequestBody NightActionRequest request) {
        return gameService.submitDetectiveInvestigate(roomId, playerId, request.getTargetPlayerId())
                .map(p -> { wsHandler.broadcast(roomId); return ResponseEntity.ok(p); })
                .orElse(ResponseEntity.badRequest().build());
    }

    @PostMapping("/night/end")
    public ResponseEntity<Room> endNight(@PathVariable UUID roomId, @RequestParam UUID playerId) {
        return gameService.getRoom(roomId)
                .filter(r -> playerId.equals(r.getHostId()))
                .flatMap(r -> gameService.endNight(roomId))
                .map(r -> { wsHandler.broadcast(roomId); return ResponseEntity.ok(r); })
                .orElse(ResponseEntity.status(HttpStatus.FORBIDDEN).build());
    }

    @PostMapping("/day/end")
    public ResponseEntity<Room> endDay(@PathVariable UUID roomId, @RequestParam UUID playerId) {
        return gameService.endDay(roomId, playerId)
                .map(r -> { wsHandler.broadcast(roomId); return ResponseEntity.ok(r); })
                .orElse(ResponseEntity.status(HttpStatus.FORBIDDEN).build());
    }

    @PostMapping("/vote/skip")
    public ResponseEntity<Room> skipVote(@PathVariable UUID roomId, @RequestParam UUID voterId) {
        return gameService.skipVote(roomId, voterId)
                .map(r -> { wsHandler.broadcast(roomId); return ResponseEntity.ok(r); })
                .orElse(ResponseEntity.badRequest().build());
    }

    @PostMapping("/night/mafia-skip")
    public ResponseEntity<Room> skipMafiaVote(@PathVariable UUID roomId, @RequestParam UUID playerId) {
        return gameService.skipMafiaVote(roomId, playerId)
                .map(r -> { wsHandler.broadcast(roomId); return ResponseEntity.ok(r); })
                .orElse(ResponseEntity.badRequest().build());
    }

    @PostMapping("/vote")
    public ResponseEntity<Room> vote(
            @PathVariable UUID roomId,
            @RequestParam UUID voterId,
            @RequestBody VoteRequest request) {
        return gameService.submitVote(roomId, voterId, request.getTargetPlayerId())
                .map(r -> { wsHandler.broadcast(roomId); return ResponseEntity.ok(r); })
                .orElse(ResponseEntity.badRequest().build());
    }

    @PostMapping("/vote/resolve")
    public ResponseEntity<Room> resolveVotes(@PathVariable UUID roomId, @RequestParam UUID playerId) {
        return gameService.getRoom(roomId)
                .filter(r -> playerId.equals(r.getHostId()))
                .filter(r -> r.getPhase() == com.mafia.game.model.GamePhase.VOTING)
                .flatMap(r -> gameService.resolveVoting(roomId))
                .map(r -> { wsHandler.broadcast(roomId); return ResponseEntity.ok(r); })
                .orElse(ResponseEntity.status(HttpStatus.FORBIDDEN).build());
    }

    @GetMapping("/players/{playerId}")
    public ResponseEntity<Player> getPlayer(@PathVariable UUID roomId, @PathVariable UUID playerId) {
        return gameService.getPlayer(roomId, playerId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/rematch")
    public ResponseEntity<Room> rematchVote(
            @PathVariable UUID roomId,
            @RequestParam UUID playerId,
            @RequestParam String choice) {
        return gameService.rematchVote(roomId, playerId, choice)
                .map(r -> { wsHandler.broadcast(roomId); return ResponseEntity.ok(r); })
                .orElse(ResponseEntity.badRequest().build());
    }

    @PostMapping("/chat")
    public ResponseEntity<Void> postChat(
            @PathVariable UUID roomId,
            @RequestParam UUID playerId,
            @RequestBody ChatMessageRequest request) {
        roomService.postMessage(roomId, playerId, request.getMessage());
        wsHandler.broadcast(roomId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/chat")
    public ResponseEntity<List<ChatMessage>> getChat(
            @PathVariable UUID roomId,
            @RequestParam UUID playerId) {
        return ResponseEntity.ok(roomService.getMessages(roomId, playerId));
    }
}
