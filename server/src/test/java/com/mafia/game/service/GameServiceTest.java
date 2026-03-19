package com.mafia.game.service;

import com.mafia.game.model.GamePhase;
import com.mafia.game.model.Player;
import com.mafia.game.room.Room;
import com.mafia.game.room.RoomManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class GameServiceTest {

    @Autowired
    private GameService gameService;

    @Autowired
    private RoomService roomService;

    @Autowired
    private RoomManager roomManager;

    @BeforeEach
    void setUp() {
        roomManager.getAllRooms().keySet().forEach(roomManager::removeRoom);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Room createRoomWithPlayers(int playerCount) {
        Room room = roomManager.createRoom("GAMETEST", playerCount, 12, 30, 1);
        for (int i = 0; i < playerCount; i++) {
            Optional<Player> player = roomService.joinRoom(room.getId(), "Player" + i);
            if (i == 0) {
                player.ifPresent(p -> room.setHostId(p.getId()));
            }
        }
        return room;
    }

    private Player findByRole(Room room, Player.Role role) {
        return room.getPlayers().stream()
                .filter(p -> p.getRole() == role)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No player with role " + role));
    }

    private Player findNonMafia(Room room) {
        return room.getPlayers().stream()
                .filter(p -> p.getRole() != Player.Role.MAFIA)
                .findFirst()
                .orElseThrow();
    }

    // ── startGame ────────────────────────────────────────────────────────────

    @Test
    void startGame_withEnoughPlayers_transitionsToDayPhase() {
        Room room = createRoomWithPlayers(4);

        Optional<Room> result = gameService.startGame(room.getId());

        assertTrue(result.isPresent());
        assertEquals(GamePhase.DAY, result.get().getPhase());
    }

    @Test
    void startGame_notEnoughPlayers_returnsEmpty() {
        Room room = roomManager.createRoom("TOOFEW", 4, 12, 30, 1);
        roomService.joinRoom(room.getId(), "Solo").ifPresent(p -> room.setHostId(p.getId()));

        Optional<Room> result = gameService.startGame(room.getId());

        assertTrue(result.isEmpty());
        assertEquals(GamePhase.LOBBY, room.getPhase());
    }

    @Test
    void startGame_alreadyStarted_returnsEmpty() {
        Room room = createRoomWithPlayers(4);
        room.setPhase(GamePhase.DAY);

        Optional<Room> result = gameService.startGame(room.getId());

        assertTrue(result.isEmpty());
    }

    @Test
    void startGame_unknownRoom_returnsEmpty() {
        assertTrue(gameService.startGame(UUID.randomUUID()).isEmpty());
    }

    // ── Role assignment ───────────────────────────────────────────────────────

    @Test
    void startGame_4players_assigns1Mafia3Citizens() {
        Room room = createRoomWithPlayers(4);
        gameService.startGame(room.getId());

        assertEquals(1, room.getPlayers().stream().filter(p -> p.getRole() == Player.Role.MAFIA).count());
        assertEquals(3, room.getPlayers().stream().filter(p -> p.getRole() == Player.Role.CITIZEN).count());
        assertEquals(0, room.getPlayers().stream().filter(p -> p.getRole() == Player.Role.DETECTIVE).count());
        assertEquals(0, room.getPlayers().stream().filter(p -> p.getRole() == Player.Role.DOCTOR).count());
    }

    @Test
    void startGame_5players_assignsDetective() {
        Room room = createRoomWithPlayers(5);
        gameService.startGame(room.getId());

        assertEquals(1, room.getPlayers().stream().filter(p -> p.getRole() == Player.Role.DETECTIVE).count());
        assertEquals(0, room.getPlayers().stream().filter(p -> p.getRole() == Player.Role.DOCTOR).count());
    }

    @Test
    void startGame_6players_assignsDoctor() {
        Room room = createRoomWithPlayers(6);
        gameService.startGame(room.getId());

        assertEquals(1, room.getPlayers().stream().filter(p -> p.getRole() == Player.Role.DETECTIVE).count());
        assertEquals(1, room.getPlayers().stream().filter(p -> p.getRole() == Player.Role.DOCTOR).count());
    }

    // ── submitNightAction ────────────────────────────────────────────────────

    @Test
    void submitNightAction_recordsMafiaVote() {
        // Use 2 mafia so 1 vote doesn't trigger auto-advance (both must vote first)
        Room room = roomManager.createRoom("GAMETEST", 4, 12, 30, 2);
        for (int i = 0; i < 6; i++) {
            Optional<Player> p = roomService.joinRoom(room.getId(), "Player" + i);
            if (i == 0) p.ifPresent(pl -> room.setHostId(pl.getId()));
        }
        gameService.startGame(room.getId());
        room.setPhase(GamePhase.NIGHT_MAFIA);

        Player mafia1 = room.getPlayers().stream()
                .filter(p -> p.getRole() == Player.Role.MAFIA).findFirst().orElseThrow();
        Player target = findNonMafia(room);

        // Only 1 of 2 mafia votes → no auto-advance yet
        Optional<Room> result = gameService.submitNightAction(room.getId(), mafia1.getId(), target.getId());

        assertTrue(result.isPresent());
        assertEquals(target.getId(), room.getMafiaVotes().get(mafia1.getId()));
    }

    @Test
    void submitNightAction_wrongPhase_returnsEmpty() {
        Room room = createRoomWithPlayers(4);
        gameService.startGame(room.getId()); // DAY phase

        Player mafia = findByRole(room, Player.Role.MAFIA);
        Player target = findNonMafia(room);

        assertTrue(gameService.submitNightAction(room.getId(), mafia.getId(), target.getId()).isEmpty());
    }

    @Test
    void submitNightAction_nonMafiaPlayer_returnsEmpty() {
        Room room = createRoomWithPlayers(4);
        gameService.startGame(room.getId());
        room.setPhase(GamePhase.NIGHT_MAFIA);

        Player citizen = findByRole(room, Player.Role.CITIZEN);
        Player mafia = findByRole(room, Player.Role.MAFIA);

        assertTrue(gameService.submitNightAction(room.getId(), citizen.getId(), mafia.getId()).isEmpty());
    }

    @Test
    void submitNightAction_allMafiaVoted_autoAdvances() {
        // 4 players → 1 mafia, no doctor, no detective → resolves night after mafia votes
        Room room = createRoomWithPlayers(4);
        gameService.startGame(room.getId());
        room.setPhase(GamePhase.NIGHT_MAFIA);

        Player mafia = findByRole(room, Player.Role.MAFIA);
        Player target = findNonMafia(room);

        gameService.submitNightAction(room.getId(), mafia.getId(), target.getId());

        assertNotEquals(GamePhase.NIGHT_MAFIA, room.getPhase());
    }

    // ── skipMafiaVote ────────────────────────────────────────────────────────

    @Test
    void skipMafiaVote_addsMafiaToSkips() {
        // Use 2 mafia so 1 skip doesn't trigger auto-advance
        Room room = roomManager.createRoom("GAMETEST", 4, 12, 30, 2);
        for (int i = 0; i < 6; i++) {
            Optional<Player> p = roomService.joinRoom(room.getId(), "Player" + i);
            if (i == 0) p.ifPresent(pl -> room.setHostId(pl.getId()));
        }
        gameService.startGame(room.getId());
        room.setPhase(GamePhase.NIGHT_MAFIA);

        Player mafia1 = room.getPlayers().stream()
                .filter(p -> p.getRole() == Player.Role.MAFIA).findFirst().orElseThrow();

        // Only 1 of 2 mafia skips → no auto-advance yet
        Optional<Room> result = gameService.skipMafiaVote(room.getId(), mafia1.getId());

        assertTrue(result.isPresent());
        assertTrue(room.getMafiaSkips().contains(mafia1.getId()));
    }

    @Test
    void skipMafiaVote_countsTowardAutoAdvance() {
        Room room = createRoomWithPlayers(4);
        gameService.startGame(room.getId());
        room.setPhase(GamePhase.NIGHT_MAFIA);

        Player mafia = findByRole(room, Player.Role.MAFIA);
        gameService.skipMafiaVote(room.getId(), mafia.getId());

        // 1 mafia skipped → all accounted for → auto-advance
        assertNotEquals(GamePhase.NIGHT_MAFIA, room.getPhase());
    }

    // ── submitDoctorProtect ───────────────────────────────────────────────────

    @Test
    void submitDoctorProtect_setsProtectedTarget() {
        Room room = createRoomWithPlayers(6);
        gameService.startGame(room.getId());
        room.setPhase(GamePhase.NIGHT_DOCTOR);

        Player doctor = findByRole(room, Player.Role.DOCTOR);
        Player target = findByRole(room, Player.Role.CITIZEN);

        Optional<Room> result = gameService.submitDoctorProtect(room.getId(), doctor.getId(), target.getId());

        assertTrue(result.isPresent());
        assertEquals(target.getId(), room.getDoctorProtectedId());
        assertTrue(room.getNightActors().contains("DOCTOR"));
    }

    @Test
    void submitDoctorProtect_sameTargetAsLastNight_returnsEmpty() {
        Room room = createRoomWithPlayers(6);
        gameService.startGame(room.getId());
        room.setPhase(GamePhase.NIGHT_DOCTOR);

        Player doctor = findByRole(room, Player.Role.DOCTOR);
        Player target = findByRole(room, Player.Role.CITIZEN);
        room.setLastDoctorProtectedId(target.getId());

        assertTrue(gameService.submitDoctorProtect(room.getId(), doctor.getId(), target.getId()).isEmpty());
    }

    @Test
    void submitDoctorProtect_wrongPhase_returnsEmpty() {
        Room room = createRoomWithPlayers(6);
        gameService.startGame(room.getId()); // DAY phase

        Player doctor = findByRole(room, Player.Role.DOCTOR);
        Player target = findByRole(room, Player.Role.CITIZEN);

        assertTrue(gameService.submitDoctorProtect(room.getId(), doctor.getId(), target.getId()).isEmpty());
    }

    // ── submitDetectiveInvestigate ────────────────────────────────────────────

    @Test
    void submitDetectiveInvestigate_returnsTargetWithRole() {
        Room room = createRoomWithPlayers(5);
        gameService.startGame(room.getId());
        room.setPhase(GamePhase.NIGHT_DETECTIVE);

        Player detective = findByRole(room, Player.Role.DETECTIVE);
        Player mafia = findByRole(room, Player.Role.MAFIA);

        Optional<Player> result = gameService.submitDetectiveInvestigate(room.getId(), detective.getId(), mafia.getId());

        assertTrue(result.isPresent());
        assertEquals(mafia.getId(), result.get().getId());
        assertEquals(Player.Role.MAFIA, result.get().getRole());
    }

    @Test
    void submitDetectiveInvestigate_triggersNightResolution() {
        // After investigating, auto-advance fires and resolves the night (nightActors is cleared).
        // The observable effect is that the phase transitions away from NIGHT_DETECTIVE.
        Room room = createRoomWithPlayers(5);
        gameService.startGame(room.getId());
        room.setPhase(GamePhase.NIGHT_DETECTIVE);

        Player detective = findByRole(room, Player.Role.DETECTIVE);
        Player target = findByRole(room, Player.Role.CITIZEN);

        gameService.submitDetectiveInvestigate(room.getId(), detective.getId(), target.getId());

        assertNotEquals(GamePhase.NIGHT_DETECTIVE, room.getPhase());
    }

    @Test
    void submitDetectiveInvestigate_wrongPhase_returnsEmpty() {
        Room room = createRoomWithPlayers(5);
        gameService.startGame(room.getId()); // DAY phase

        Player detective = findByRole(room, Player.Role.DETECTIVE);
        Player target = findByRole(room, Player.Role.CITIZEN);

        assertTrue(gameService.submitDetectiveInvestigate(room.getId(), detective.getId(), target.getId()).isEmpty());
    }

    // ── endNight ─────────────────────────────────────────────────────────────

    @Test
    void endNight_fromNightMafia_withDoctor_advancesToNightDoctor() {
        Room room = createRoomWithPlayers(6);
        gameService.startGame(room.getId());
        room.setPhase(GamePhase.NIGHT_MAFIA);

        gameService.endNight(room.getId());

        assertEquals(GamePhase.NIGHT_DOCTOR, room.getPhase());
    }

    @Test
    void endNight_fromNightMafia_noDoctor_withDetective_advancesToNightDetective() {
        // 5 players → detective, no doctor
        Room room = createRoomWithPlayers(5);
        gameService.startGame(room.getId());
        room.setPhase(GamePhase.NIGHT_MAFIA);

        gameService.endNight(room.getId());

        assertEquals(GamePhase.NIGHT_DETECTIVE, room.getPhase());
    }

    @Test
    void endNight_fromNightMafia_noDoctor_noDetective_resolvesNight() {
        // 4 players → no doctor, no detective
        Room room = createRoomWithPlayers(4);
        gameService.startGame(room.getId());
        room.setPhase(GamePhase.NIGHT_MAFIA);

        gameService.endNight(room.getId());

        // Should have resolved night → DAY or ENDED
        assertNotEquals(GamePhase.NIGHT_MAFIA, room.getPhase());
        assertNotEquals(GamePhase.NIGHT_DOCTOR, room.getPhase());
        assertNotEquals(GamePhase.NIGHT_DETECTIVE, room.getPhase());
    }

    @Test
    void endNight_wrongPhase_returnsEmpty() {
        Room room = createRoomWithPlayers(4);
        gameService.startGame(room.getId()); // DAY phase

        assertTrue(gameService.endNight(room.getId()).isEmpty());
    }

    // ── endDay ────────────────────────────────────────────────────────────────

    @Test
    void endDay_hostEndsDay_transitionsToVoting() {
        Room room = createRoomWithPlayers(4);
        gameService.startGame(room.getId()); // DAY phase

        Optional<Room> result = gameService.endDay(room.getId(), room.getHostId());

        assertTrue(result.isPresent());
        assertEquals(GamePhase.VOTING, result.get().getPhase());
    }

    @Test
    void endDay_nonHost_returnsEmpty() {
        Room room = createRoomWithPlayers(4);
        gameService.startGame(room.getId());

        UUID nonHostId = room.getPlayers().stream()
                .filter(p -> !p.getId().equals(room.getHostId()))
                .findFirst().orElseThrow().getId();

        Optional<Room> result = gameService.endDay(room.getId(), nonHostId);

        assertTrue(result.isEmpty());
        assertEquals(GamePhase.DAY, room.getPhase());
    }

    @Test
    void endDay_wrongPhase_returnsEmpty() {
        Room room = createRoomWithPlayers(4);
        gameService.startGame(room.getId());
        room.setPhase(GamePhase.VOTING);

        assertTrue(gameService.endDay(room.getId(), room.getHostId()).isEmpty());
    }

    // ── submitVote ────────────────────────────────────────────────────────────

    @Test
    void submitVote_recordsVote() {
        Room room = createRoomWithPlayers(4);
        gameService.startGame(room.getId());
        room.setPhase(GamePhase.VOTING);

        Player voter = room.getPlayers().get(0);
        Player target = room.getPlayers().get(1);

        Optional<Room> result = gameService.submitVote(room.getId(), voter.getId(), target.getId());

        assertTrue(result.isPresent());
        assertEquals(target.getId(), room.getVotes().get(voter.getId()));
    }

    @Test
    void submitVote_duplicate_firstVoteKept() {
        Room room = createRoomWithPlayers(4);
        gameService.startGame(room.getId());
        room.setPhase(GamePhase.VOTING);

        Player voter = room.getPlayers().get(0);
        Player target1 = room.getPlayers().get(1);
        Player target2 = room.getPlayers().get(2);

        gameService.submitVote(room.getId(), voter.getId(), target1.getId());
        gameService.submitVote(room.getId(), voter.getId(), target2.getId()); // ignored

        assertEquals(target1.getId(), room.getVotes().get(voter.getId()));
    }

    @Test
    void submitVote_wrongPhase_returnsEmpty() {
        Room room = createRoomWithPlayers(4);
        gameService.startGame(room.getId()); // DAY phase

        assertTrue(gameService.submitVote(room.getId(),
                room.getPlayers().get(0).getId(), room.getPlayers().get(1).getId()).isEmpty());
    }

    // ── skipVote ──────────────────────────────────────────────────────────────

    @Test
    void skipVote_addsToVoteSkips() {
        Room room = createRoomWithPlayers(4);
        gameService.startGame(room.getId());
        room.setPhase(GamePhase.VOTING);

        Player voter = room.getPlayers().get(0);
        Optional<Room> result = gameService.skipVote(room.getId(), voter.getId());

        assertTrue(result.isPresent());
        assertTrue(room.getVoteSkips().contains(voter.getId()));
    }

    // ── resolveVoting ─────────────────────────────────────────────────────────

    @Test
    void resolveVoting_eliminatesTopVotedPlayer() {
        Room room = createRoomWithPlayers(4);
        gameService.startGame(room.getId());
        room.setPhase(GamePhase.VOTING);

        Player target = room.getPlayers().get(0);
        room.getPlayers().stream()
                .filter(p -> !p.getId().equals(target.getId()))
                .forEach(voter -> room.getVotes().put(voter.getId(), target.getId()));

        gameService.resolveVoting(room.getId());

        assertFalse(target.isAlive());
    }

    @Test
    void resolveVoting_tie_noElimination() {
        Room room = createRoomWithPlayers(4);
        gameService.startGame(room.getId());
        room.setPhase(GamePhase.VOTING);

        // Each of two players gets exactly 1 vote → tie → no kill
        Player a = room.getPlayers().get(0);
        Player b = room.getPlayers().get(1);
        room.getVotes().put(room.getPlayers().get(2).getId(), a.getId());
        room.getVotes().put(room.getPlayers().get(3).getId(), b.getId());

        long aliveBefore = room.getPlayers().stream().filter(Player::isAlive).count();
        gameService.resolveVoting(room.getId());
        long aliveAfter = room.getPlayers().stream().filter(Player::isAlive).count();

        assertEquals(aliveBefore, aliveAfter);
    }

    @Test
    void resolveVoting_allMafiaEliminated_citizensWin() {
        Room room = createRoomWithPlayers(4);
        gameService.startGame(room.getId());
        room.setPhase(GamePhase.VOTING);

        Player mafia = findByRole(room, Player.Role.MAFIA);
        room.getPlayers().stream()
                .filter(p -> !p.getId().equals(mafia.getId()))
                .forEach(voter -> room.getVotes().put(voter.getId(), mafia.getId()));

        gameService.resolveVoting(room.getId());

        assertEquals(GamePhase.ENDED, room.getPhase());
        assertEquals(Player.Role.CITIZEN, room.getWinner());
    }

    @Test
    void resolveVoting_mafiaReachesParity_mafiaWins() {
        Room room = createRoomWithPlayers(4);
        gameService.startGame(room.getId());

        // Kill 2 citizens beforehand so mafiaAlive (1) >= nonMafiaAlive (1)
        room.getPlayers().stream()
                .filter(p -> p.getRole() != Player.Role.MAFIA)
                .limit(2)
                .forEach(p -> p.setAlive(false));

        room.setPhase(GamePhase.VOTING);
        // No votes → no elimination → win check fires immediately
        gameService.resolveVoting(room.getId());

        assertEquals(GamePhase.ENDED, room.getPhase());
        assertEquals(Player.Role.MAFIA, room.getWinner());
    }

    @Test
    void resolveVoting_gameOngoing_transitionsToNightMafia() {
        Room room = createRoomWithPlayers(4);
        gameService.startGame(room.getId());
        room.setPhase(GamePhase.VOTING);

        // Vote out a citizen — mafia still alive → game continues
        Player citizen = findByRole(room, Player.Role.CITIZEN);
        room.getPlayers().stream()
                .filter(Player::isAlive)
                .forEach(p -> room.getVotes().put(p.getId(), citizen.getId()));

        gameService.resolveVoting(room.getId());

        assertEquals(GamePhase.NIGHT_MAFIA, room.getPhase());
    }

    // ── checkWinCondition ─────────────────────────────────────────────────────

    @Test
    void checkWinCondition_noMafiaAlive_citizensWin() {
        Room room = createRoomWithPlayers(4);
        gameService.startGame(room.getId());
        room.getPlayers().stream()
                .filter(p -> p.getRole() == Player.Role.MAFIA)
                .forEach(p -> p.setAlive(false));

        Optional<Player.Role> winner = gameService.checkWinCondition(room);

        assertTrue(winner.isPresent());
        assertEquals(Player.Role.CITIZEN, winner.get());
    }

    @Test
    void checkWinCondition_mafiaEqualsNonMafia_mafiaWins() {
        Room room = createRoomWithPlayers(4);
        gameService.startGame(room.getId());
        // Kill 2 citizens: 1 mafia alive, 1 citizen alive → mafia wins
        room.getPlayers().stream()
                .filter(p -> p.getRole() != Player.Role.MAFIA)
                .limit(2)
                .forEach(p -> p.setAlive(false));

        Optional<Player.Role> winner = gameService.checkWinCondition(room);

        assertTrue(winner.isPresent());
        assertEquals(Player.Role.MAFIA, winner.get());
    }

    @Test
    void checkWinCondition_gameOngoing_returnsEmpty() {
        Room room = createRoomWithPlayers(4);
        gameService.startGame(room.getId()); // all alive

        assertTrue(gameService.checkWinCondition(room).isEmpty());
    }

    // ── resolveNight ──────────────────────────────────────────────────────────

    @Test
    void resolveNight_mafiaVote_killsTarget() {
        Room room = createRoomWithPlayers(4);
        gameService.startGame(room.getId());
        room.setPhase(GamePhase.NIGHT_MAFIA);

        Player mafia = findByRole(room, Player.Role.MAFIA);
        Player target = findNonMafia(room);
        room.getMafiaVotes().put(mafia.getId(), target.getId());

        // 4 players → no doctor, no detective → resolves immediately
        gameService.endNight(room.getId());

        assertFalse(target.isAlive());
    }

    @Test
    void resolveNight_doctorProtects_targetSurvives() {
        // 6 players → 1 mafia, 1 detective, 1 doctor, 3 citizens
        Room room = createRoomWithPlayers(6);
        gameService.startGame(room.getId());

        Player mafia = findByRole(room, Player.Role.MAFIA);
        Player citizen = findByRole(room, Player.Role.CITIZEN);

        room.setPhase(GamePhase.NIGHT_MAFIA);
        room.getMafiaVotes().put(mafia.getId(), citizen.getId());
        room.setDoctorProtectedId(citizen.getId());

        // endNight × 3: NIGHT_MAFIA → NIGHT_DOCTOR → NIGHT_DETECTIVE → resolveNight
        gameService.endNight(room.getId()); // → NIGHT_DOCTOR
        gameService.endNight(room.getId()); // → NIGHT_DETECTIVE
        gameService.endNight(room.getId()); // → resolveNight

        assertTrue(citizen.isAlive());
    }

    // ── rematchVote ───────────────────────────────────────────────────────────

    @Test
    void rematchVote_allPlayersChoosePlayAgain_resetsRoom() {
        Room room = createRoomWithPlayers(4);
        gameService.startGame(room.getId());
        room.setPhase(GamePhase.ENDED);
        room.setWinner(Player.Role.CITIZEN);

        room.getPlayers().forEach(p ->
                gameService.rematchVote(room.getId(), p.getId(), "PLAY_AGAIN"));

        assertEquals(GamePhase.LOBBY, room.getPhase());
        assertNull(room.getWinner());
        room.getPlayers().forEach(p -> {
            assertTrue(p.isAlive());
            assertEquals(Player.Role.CITIZEN, p.getRole());
        });
    }

    @Test
    void rematchVote_leave_recordsLeaveVote() {
        Room room = createRoomWithPlayers(4);
        gameService.startGame(room.getId());
        room.setPhase(GamePhase.ENDED);
        room.setWinner(Player.Role.CITIZEN);

        Player player = room.getPlayers().get(0);
        gameService.rematchVote(room.getId(), player.getId(), "LEAVE");

        assertTrue(room.getLeaveVotes().contains(player.getId()));
        assertEquals(GamePhase.ENDED, room.getPhase()); // room not reset yet
    }

    @Test
    void rematchVote_wrongPhase_returnsEmpty() {
        Room room = createRoomWithPlayers(4);
        gameService.startGame(room.getId()); // DAY phase

        assertTrue(gameService.rematchVote(room.getId(), room.getPlayers().get(0).getId(), "PLAY_AGAIN").isEmpty());
    }
}
