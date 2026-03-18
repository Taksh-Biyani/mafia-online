package com.mafia.game.controller;

import com.mafia.game.api.ChatMessageRequest;
import com.mafia.game.api.NightActionRequest;
import com.mafia.game.api.VoteRequest;
import com.mafia.game.model.ChatMessage;
import com.mafia.game.model.Player;
import com.mafia.game.room.Room;
import com.mafia.game.service.GameService;
import com.mafia.game.service.RoomService;
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

/**
 * REST controller for managing game state and actions during an active game.
 * Handles game transitions, player actions, voting, and state queries.
 * All endpoints require a valid roomId path variable.
 */
@RestController
@RequestMapping("/api/rooms/{roomId}")
public class GameController {
    private final GameService gameService;
    private final RoomService roomService;

    /**
     * Constructs GameController with dependencies on GameService and RoomService.
     */
    public GameController(GameService gameService, RoomService roomService) {
        this.gameService = gameService;
        this.roomService = roomService;
    }

    /**
     * Starts a game in the specified room.
     * Only the host (room creator) can start the game.
     * Requires the room to be in LOBBY phase with at least minPlayers joined.
     * Returns 200 OK with the updated room, 403 if caller is not the host,
     * or 400 Bad Request if conditions not met.
     *
     * @param roomId   the UUID of the room
     * @param playerId the UUID of the player attempting to start the game
     * @return ResponseEntity containing the updated Room state or error status
     */
    @PostMapping("/start")
    public ResponseEntity<Room> startGame(@PathVariable UUID roomId, @RequestParam UUID playerId) {
        return gameService.getRoom(roomId)
                .map(room -> {
                    if (!playerId.equals(room.getHostId())) {
                        return ResponseEntity.status(HttpStatus.FORBIDDEN).<Room>build();
                    }
                    return gameService.startGame(roomId)
                            .map(ResponseEntity::ok)
                            .orElse(ResponseEntity.badRequest().build());
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Retrieves the current state of a game room.
     * Returns 200 OK with room details or 404 Not Found if room doesn't exist.
     *
     * @param roomId the UUID of the room
     * @return ResponseEntity containing the Room state or 404 error
     */
    @GetMapping("/state")
    public ResponseEntity<Room> getState(@PathVariable UUID roomId) {
        return gameService.getRoom(roomId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Submits a night action (mafia elimination) for the specified player.
     * Only mafia members can perform night actions during NIGHT phase.
     * Returns 200 OK with updated room on success, or 400 Bad Request if invalid action.
     *
     * @param roomId the UUID of the room
     * @param playerId the UUID of the player performing the action (query parameter)
     * @param request contains the target player ID to eliminate
     * @return ResponseEntity containing the updated Room state or error status
     */
    @PostMapping("/night/action")
    public ResponseEntity<Room> nightAction(
            @PathVariable UUID roomId,
            @RequestParam UUID playerId,
            @RequestBody NightActionRequest request) {
        return gameService.submitNightAction(roomId, playerId, request.getTargetPlayerId())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.badRequest().build());
    }

    /**
     * Submits the doctor's protection choice for the current night.
     * Only the alive doctor can call this during NIGHT phase.
     *
     * @param roomId the UUID of the room
     * @param playerId the UUID of the doctor player
     * @param request contains the target player ID to protect
     * @return ResponseEntity containing the updated Room state or 400 if invalid
     */
    @PostMapping("/night/protect")
    public ResponseEntity<Room> doctorProtect(
            @PathVariable UUID roomId,
            @RequestParam UUID playerId,
            @RequestBody NightActionRequest request) {
        return gameService.submitDoctorProtect(roomId, playerId, request.getTargetPlayerId())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.badRequest().build());
    }

    /**
     * Submits the detective's investigation for the current night.
     * Returns the investigated player (with their role) — visible only to the detective.
     *
     * @param roomId the UUID of the room
     * @param playerId the UUID of the detective player
     * @param request contains the target player ID to investigate
     * @return ResponseEntity containing the investigated Player or 400 if invalid
     */
    @PostMapping("/night/investigate")
    public ResponseEntity<Player> detectiveInvestigate(
            @PathVariable UUID roomId,
            @RequestParam UUID playerId,
            @RequestBody NightActionRequest request) {
        return gameService.submitDetectiveInvestigate(roomId, playerId, request.getTargetPlayerId())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.badRequest().build());
    }

    /**
     * Manually ends the night phase and transitions to day phase.
     * Returns 200 OK with updated room on success, or 400 Bad Request if not in NIGHT phase.
     *
     * @param roomId the UUID of the room
     * @return ResponseEntity containing the updated Room state or error status
     */
    @PostMapping("/night/end")
    public ResponseEntity<Room> endNight(@PathVariable UUID roomId, @RequestParam UUID playerId) {
        return gameService.getRoom(roomId)
                .filter(r -> playerId.equals(r.getHostId()))
                .flatMap(r -> gameService.endNight(roomId))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.status(HttpStatus.FORBIDDEN).build());
    }

    /**
     * Ends the day discussion phase and transitions to voting.
     * Only the host can call this. Timer expiry on the frontend also triggers this.
     *
     * @param roomId   the UUID of the room
     * @param playerId the UUID of the player ending the day (must be host)
     * @return ResponseEntity containing the updated Room state, 403 if not host, 400 if wrong phase
     */
    @PostMapping("/day/end")
    public ResponseEntity<Room> endDay(@PathVariable UUID roomId, @RequestParam UUID playerId) {
        return gameService.endDay(roomId, playerId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.status(HttpStatus.FORBIDDEN).build());
    }

    /**
     * Submits a vote during the voting phase.
     * Votes are stored per player (double votes ignored).
     * Auto-resolves once all alive players have voted.
     * Returns 200 OK with updated room on success, or 400 Bad Request if not in VOTING phase.
     *
     * @param roomId  the UUID of the room
     * @param voterId the UUID of the player voting (query parameter)
     * @param request contains the target player ID to vote for
     * @return ResponseEntity containing the updated Room state or error status
     */
    @PostMapping("/vote/skip")
    public ResponseEntity<Room> skipVote(@PathVariable UUID roomId, @RequestParam UUID voterId) {
        return gameService.skipVote(roomId, voterId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.badRequest().build());
    }

    @PostMapping("/night/mafia-skip")
    public ResponseEntity<Room> skipMafiaVote(@PathVariable UUID roomId, @RequestParam UUID playerId) {
        return gameService.skipMafiaVote(roomId, playerId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.badRequest().build());
    }

    @PostMapping("/vote")
    public ResponseEntity<Room> vote(
            @PathVariable UUID roomId,
            @RequestParam UUID voterId,
            @RequestBody VoteRequest request) {
        return gameService.submitVote(roomId, voterId, request.getTargetPlayerId())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.badRequest().build());
    }

    /**
     * Manually resolves voting (host only). Useful if not all players have voted.
     * Eliminates the player with the most votes, then checks win condition.
     *
     * @param roomId   the UUID of the room
     * @param playerId the UUID of the player resolving the vote (must be host)
     * @return ResponseEntity containing the updated Room state, 403 if not host
     */
    @PostMapping("/vote/resolve")
    public ResponseEntity<Room> resolveVotes(@PathVariable UUID roomId, @RequestParam UUID playerId) {
        return gameService.getRoom(roomId)
                .filter(r -> playerId.equals(r.getHostId()))
                .filter(r -> r.getPhase() == com.mafia.game.model.GamePhase.VOTING)

                .flatMap(r -> gameService.resolveVoting(roomId))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.status(HttpStatus.FORBIDDEN).build());
    }

    /**
     * Retrieves details for a specific player in the room.
     * Returns 200 OK with player details or 404 Not Found if player doesn't exist.
     *
     * @param roomId the UUID of the room
     * @param playerId the UUID of the player to retrieve
     * @return ResponseEntity containing the Player or 404 error
     */
    @GetMapping("/players/{playerId}")
    public ResponseEntity<Player> getPlayer(@PathVariable UUID roomId, @PathVariable UUID playerId) {
        return gameService.getPlayer(roomId, playerId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Records a player's rematch choice on the end screen.
     * choice=PLAY_AGAIN marks them with a green checkmark; all voting resets the room.
     * choice=LEAVE marks them with a red X; the client will call leave shortly after.
     */
    @PostMapping("/rematch")
    public ResponseEntity<Room> rematchVote(
            @PathVariable UUID roomId,
            @RequestParam UUID playerId,
            @RequestParam String choice) {
        return gameService.rematchVote(roomId, playerId, choice)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.badRequest().build());
    }

    /**
     * Posts a chat message from a player.
     * Returns 200 on success, 400 if empty/too long, 403 if not allowed to chat, 429 if sending too fast.
     */
    @PostMapping("/chat")
    public ResponseEntity<Void> postChat(
            @PathVariable UUID roomId,
            @RequestParam UUID playerId,
            @RequestBody ChatMessageRequest request) {
        roomService.postMessage(roomId, playerId, request.getMessage());
        return ResponseEntity.ok().build();
    }

    /**
     * Returns the chat messages visible to the requesting player.
     * Dead players see GENERAL + DEAD channels. Alive MAFIA see MAFIA_NIGHT. Others see GENERAL only.
     */
    @GetMapping("/chat")
    public ResponseEntity<List<ChatMessage>> getChat(
            @PathVariable UUID roomId,
            @RequestParam UUID playerId) {
        return ResponseEntity.ok(roomService.getMessages(roomId, playerId));
    }
}