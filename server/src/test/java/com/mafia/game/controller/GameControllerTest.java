package com.mafia.game.controller;

import com.mafia.game.model.GamePhase;
import com.mafia.game.model.Player;
import com.mafia.game.room.Room;
import com.mafia.game.room.RoomManager;
import com.mafia.game.service.GameService;
import com.mafia.game.service.RoomService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class GameControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RoomManager roomManager;

    @Autowired
    private RoomService roomService;

    @Autowired
    private GameService gameService;

    @BeforeEach
    void setUp() {
        roomManager.getAllRooms().keySet().forEach(roomManager::removeRoom);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Room createRoomWithPlayers(int playerCount) {
        Room room = roomManager.createRoom("CTRLTEST", playerCount, 12, 30, 1);
        for (int i = 0; i < playerCount; i++) {
            Optional<Player> player = roomService.joinRoom(room.getId(), "Player" + i);
            if (i == 0) {
                player.ifPresent(p -> room.setHostId(p.getId()));
            }
        }
        return room;
    }

    private Room startedRoom(int playerCount) {
        Room room = createRoomWithPlayers(playerCount);
        gameService.startGame(room.getId());
        return room;
    }

    private Player findByRole(Room room, Player.Role role) {
        return room.getPlayers().stream()
                .filter(p -> p.getRole() == role)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No player with role " + role));
    }

    private String nightActionBody(UUID targetId) {
        return "{\"targetPlayerId\":\"" + targetId + "\"}";
    }

    private String voteBody(UUID targetId) {
        return "{\"targetPlayerId\":\"" + targetId + "\"}";
    }

    // ── POST /start ───────────────────────────────────────────────────────────

    @Test
    void startGame_host_returns200WithDayPhase() throws Exception {
        Room room = createRoomWithPlayers(4);

        mockMvc.perform(post("/api/rooms/{id}/start", room.getId())
                        .param("playerId", room.getHostId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("DAY"));
    }

    @Test
    void startGame_nonHost_returns403() throws Exception {
        Room room = createRoomWithPlayers(4);
        UUID nonHostId = room.getPlayers().stream()
                .filter(p -> !p.getId().equals(room.getHostId()))
                .findFirst().orElseThrow().getId();

        mockMvc.perform(post("/api/rooms/{id}/start", room.getId())
                        .param("playerId", nonHostId.toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    void startGame_unknownRoom_returns404() throws Exception {
        mockMvc.perform(post("/api/rooms/{id}/start", UUID.randomUUID())
                        .param("playerId", UUID.randomUUID().toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    void startGame_notEnoughPlayers_returns400() throws Exception {
        Room room = roomManager.createRoom("TOOFEW", 4, 12, 30, 1);
        roomService.joinRoom(room.getId(), "Solo").ifPresent(p -> room.setHostId(p.getId()));

        mockMvc.perform(post("/api/rooms/{id}/start", room.getId())
                        .param("playerId", room.getHostId().toString()))
                .andExpect(status().isBadRequest());
    }

    // ── GET /state ────────────────────────────────────────────────────────────

    @Test
    void getState_existingRoom_returns200() throws Exception {
        Room room = createRoomWithPlayers(4);

        mockMvc.perform(get("/api/rooms/{id}/state", room.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(room.getId().toString()));
    }

    @Test
    void getState_unknownRoom_returns404() throws Exception {
        mockMvc.perform(get("/api/rooms/{id}/state", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    // ── POST /night/action ────────────────────────────────────────────────────

    @Test
    void nightAction_mafiaVotes_returns200() throws Exception {
        Room room = startedRoom(4);
        room.setPhase(GamePhase.NIGHT_MAFIA);

        Player mafia = findByRole(room, Player.Role.MAFIA);
        Player target = room.getPlayers().stream()
                .filter(p -> p.getRole() != Player.Role.MAFIA).findFirst().orElseThrow();

        mockMvc.perform(post("/api/rooms/{id}/night/action", room.getId())
                        .param("playerId", mafia.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(nightActionBody(target.getId())))
                .andExpect(status().isOk());
    }

    @Test
    void nightAction_wrongPhase_returns400() throws Exception {
        Room room = startedRoom(4); // DAY phase

        Player mafia = findByRole(room, Player.Role.MAFIA);
        Player target = room.getPlayers().stream()
                .filter(p -> p.getRole() != Player.Role.MAFIA).findFirst().orElseThrow();

        mockMvc.perform(post("/api/rooms/{id}/night/action", room.getId())
                        .param("playerId", mafia.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(nightActionBody(target.getId())))
                .andExpect(status().isBadRequest());
    }

    // ── POST /night/protect ───────────────────────────────────────────────────

    @Test
    void doctorProtect_validAction_returns200() throws Exception {
        Room room = startedRoom(6);
        room.setPhase(GamePhase.NIGHT_DOCTOR);

        Player doctor = findByRole(room, Player.Role.DOCTOR);
        Player target = findByRole(room, Player.Role.CITIZEN);

        mockMvc.perform(post("/api/rooms/{id}/night/protect", room.getId())
                        .param("playerId", doctor.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(nightActionBody(target.getId())))
                .andExpect(status().isOk());
    }

    @Test
    void doctorProtect_wrongPhase_returns400() throws Exception {
        Room room = startedRoom(6); // DAY phase

        Player doctor = findByRole(room, Player.Role.DOCTOR);
        Player target = findByRole(room, Player.Role.CITIZEN);

        mockMvc.perform(post("/api/rooms/{id}/night/protect", room.getId())
                        .param("playerId", doctor.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(nightActionBody(target.getId())))
                .andExpect(status().isBadRequest());
    }

    // ── POST /night/investigate ───────────────────────────────────────────────

    @Test
    void detectiveInvestigate_validAction_returnsPlayerWithRole() throws Exception {
        Room room = startedRoom(5);
        room.setPhase(GamePhase.NIGHT_DETECTIVE);

        Player detective = findByRole(room, Player.Role.DETECTIVE);
        Player mafia = findByRole(room, Player.Role.MAFIA);

        mockMvc.perform(post("/api/rooms/{id}/night/investigate", room.getId())
                        .param("playerId", detective.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(nightActionBody(mafia.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("MAFIA"));
    }

    @Test
    void detectiveInvestigate_wrongPhase_returns400() throws Exception {
        Room room = startedRoom(5); // DAY phase

        Player detective = findByRole(room, Player.Role.DETECTIVE);
        Player mafia = findByRole(room, Player.Role.MAFIA);

        mockMvc.perform(post("/api/rooms/{id}/night/investigate", room.getId())
                        .param("playerId", detective.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(nightActionBody(mafia.getId())))
                .andExpect(status().isBadRequest());
    }

    // ── POST /night/end ───────────────────────────────────────────────────────

    @Test
    void endNight_host_returns200() throws Exception {
        Room room = startedRoom(4);
        room.setPhase(GamePhase.NIGHT_MAFIA);

        mockMvc.perform(post("/api/rooms/{id}/night/end", room.getId())
                        .param("playerId", room.getHostId().toString()))
                .andExpect(status().isOk());
    }

    @Test
    void endNight_nonHost_returns403() throws Exception {
        Room room = startedRoom(4);
        room.setPhase(GamePhase.NIGHT_MAFIA);

        UUID nonHostId = room.getPlayers().stream()
                .filter(p -> !p.getId().equals(room.getHostId()))
                .findFirst().orElseThrow().getId();

        mockMvc.perform(post("/api/rooms/{id}/night/end", room.getId())
                        .param("playerId", nonHostId.toString()))
                .andExpect(status().isForbidden());
    }

    // ── POST /day/end ─────────────────────────────────────────────────────────

    @Test
    void endDay_host_returns200WithVotingPhase() throws Exception {
        Room room = startedRoom(4); // DAY phase

        mockMvc.perform(post("/api/rooms/{id}/day/end", room.getId())
                        .param("playerId", room.getHostId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("VOTING"));
    }

    @Test
    void endDay_nonHost_returns403() throws Exception {
        Room room = startedRoom(4);

        UUID nonHostId = room.getPlayers().stream()
                .filter(p -> !p.getId().equals(room.getHostId()))
                .findFirst().orElseThrow().getId();

        mockMvc.perform(post("/api/rooms/{id}/day/end", room.getId())
                        .param("playerId", nonHostId.toString()))
                .andExpect(status().isForbidden());
    }

    // ── POST /vote ────────────────────────────────────────────────────────────

    @Test
    void vote_validVote_returns200() throws Exception {
        Room room = startedRoom(4);
        room.setPhase(GamePhase.VOTING);

        Player voter = room.getPlayers().get(0);
        Player target = room.getPlayers().get(1);

        mockMvc.perform(post("/api/rooms/{id}/vote", room.getId())
                        .param("voterId", voter.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(voteBody(target.getId())))
                .andExpect(status().isOk());
    }

    @Test
    void vote_wrongPhase_returns400() throws Exception {
        Room room = startedRoom(4); // DAY phase

        mockMvc.perform(post("/api/rooms/{id}/vote", room.getId())
                        .param("voterId", room.getPlayers().get(0).getId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(voteBody(room.getPlayers().get(1).getId())))
                .andExpect(status().isBadRequest());
    }

    // ── POST /vote/skip ───────────────────────────────────────────────────────

    @Test
    void skipVote_validSkip_returns200() throws Exception {
        Room room = startedRoom(4);
        room.setPhase(GamePhase.VOTING);

        mockMvc.perform(post("/api/rooms/{id}/vote/skip", room.getId())
                        .param("voterId", room.getPlayers().get(0).getId().toString()))
                .andExpect(status().isOk());
    }

    @Test
    void skipVote_wrongPhase_returns400() throws Exception {
        Room room = startedRoom(4); // DAY phase

        mockMvc.perform(post("/api/rooms/{id}/vote/skip", room.getId())
                        .param("voterId", room.getPlayers().get(0).getId().toString()))
                .andExpect(status().isBadRequest());
    }

    // ── POST /vote/resolve ────────────────────────────────────────────────────

    @Test
    void resolveVotes_hostInVotingPhase_returns200() throws Exception {
        Room room = startedRoom(4);
        room.setPhase(GamePhase.VOTING);

        mockMvc.perform(post("/api/rooms/{id}/vote/resolve", room.getId())
                        .param("playerId", room.getHostId().toString()))
                .andExpect(status().isOk());
    }

    @Test
    void resolveVotes_nonHost_returns403() throws Exception {
        Room room = startedRoom(4);
        room.setPhase(GamePhase.VOTING);

        UUID nonHostId = room.getPlayers().stream()
                .filter(p -> !p.getId().equals(room.getHostId()))
                .findFirst().orElseThrow().getId();

        mockMvc.perform(post("/api/rooms/{id}/vote/resolve", room.getId())
                        .param("playerId", nonHostId.toString()))
                .andExpect(status().isForbidden());
    }

    // ── GET /players/{playerId} ───────────────────────────────────────────────

    @Test
    void getPlayer_existingPlayer_returns200() throws Exception {
        Room room = createRoomWithPlayers(4);
        Player player = room.getPlayers().get(0);

        mockMvc.perform(get("/api/rooms/{id}/players/{playerId}", room.getId(), player.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(player.getId().toString()));
    }

    @Test
    void getPlayer_unknownPlayer_returns404() throws Exception {
        Room room = createRoomWithPlayers(4);

        mockMvc.perform(get("/api/rooms/{id}/players/{playerId}", room.getId(), UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    // ── POST /rematch ─────────────────────────────────────────────────────────

    @Test
    void rematch_playAgain_returns200() throws Exception {
        Room room = startedRoom(4);
        room.setPhase(GamePhase.ENDED);
        room.setWinner(Player.Role.CITIZEN);

        mockMvc.perform(post("/api/rooms/{id}/rematch", room.getId())
                        .param("playerId", room.getPlayers().get(0).getId().toString())
                        .param("choice", "PLAY_AGAIN"))
                .andExpect(status().isOk());
    }

    @Test
    void rematch_wrongPhase_returns400() throws Exception {
        Room room = startedRoom(4); // DAY phase, not ENDED

        mockMvc.perform(post("/api/rooms/{id}/rematch", room.getId())
                        .param("playerId", room.getPlayers().get(0).getId().toString())
                        .param("choice", "PLAY_AGAIN"))
                .andExpect(status().isBadRequest());
    }
}
