package com.mafia.game.service;

import com.mafia.game.model.GamePhase;
import com.mafia.game.model.Player;
import com.mafia.game.room.Room;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class GameService {
    private final RoomService roomService;

    public GameService(RoomService roomService) {
        this.roomService = roomService;
    }

    // ── Game start ──────────────────────────────────────────────────────────

    /** Assigns roles and starts the first DAY phase. */
    public Optional<Room> startGame(UUID roomId) {
        return roomService.getRoom(roomId)
                .filter(r -> r.getPhase() == GamePhase.LOBBY)
                .filter(r -> r.getPlayers().size() >= r.getMinPlayers())
                .map(room -> {
                    assignRoles(room);
                    room.setPhase(GamePhase.DAY);
                    return room;
                });
    }

    // ── Night sub-phase actions ─────────────────────────────────────────────

    /** Mafia player casts/updates their kill vote. Auto-advances when all mafia have voted. */
    public Optional<Room> submitNightAction(UUID roomId, UUID playerId, UUID targetPlayerId) {
        return roomService.getRoom(roomId)
                .filter(r -> r.getPhase() == GamePhase.NIGHT_MAFIA)
                .flatMap(room -> findPlayer(room, playerId)
                        .filter(p -> p.getRole() == Player.Role.MAFIA && p.isAlive())
                        .map(mafia -> {
                            room.getMafiaVotes().put(playerId, targetPlayerId);
                            checkAutoAdvance(room);
                            return room;
                        }));
    }

    /** Doctor chooses who to protect. Rejects if same target as last night. Auto-advances after acting. */
    public Optional<Room> submitDoctorProtect(UUID roomId, UUID playerId, UUID targetPlayerId) {
        return roomService.getRoom(roomId)
                .filter(r -> r.getPhase() == GamePhase.NIGHT_DOCTOR)
                .flatMap(room -> findPlayer(room, playerId)
                        .filter(p -> p.getRole() == Player.Role.DOCTOR && p.isAlive())
                        .flatMap(doctor -> {
                            if (targetPlayerId.equals(room.getLastDoctorProtectedId())) {
                                return Optional.empty();
                            }
                            room.setDoctorProtectedId(targetPlayerId);
                            room.getNightActors().add("DOCTOR");
                            checkAutoAdvance(room);
                            return Optional.of(room);
                        }));
    }

    /** Detective investigates a player. Auto-advances after acting. Returns investigated player. */
    public Optional<Player> submitDetectiveInvestigate(UUID roomId, UUID playerId, UUID targetPlayerId) {
        return roomService.getRoom(roomId)
                .filter(r -> r.getPhase() == GamePhase.NIGHT_DETECTIVE)
                .flatMap(room -> findPlayer(room, playerId)
                        .filter(p -> p.getRole() == Player.Role.DETECTIVE && p.isAlive())
                        .flatMap(detective -> {
                            room.getNightActors().add("DETECTIVE");
                            checkAutoAdvance(room);
                            return findPlayer(room, targetPlayerId);
                        }));
    }

    /**
     * Host skip — advances the current night sub-phase without waiting for remaining players.
     * Works on NIGHT_MAFIA, NIGHT_DOCTOR, NIGHT_DETECTIVE.
     */
    public Optional<Room> endNight(UUID roomId) {
        return roomService.getRoom(roomId)
                .filter(r -> r.getPhase() == GamePhase.NIGHT_MAFIA
                          || r.getPhase() == GamePhase.NIGHT_DOCTOR
                          || r.getPhase() == GamePhase.NIGHT_DETECTIVE)
                .map(room -> {
                    switch (room.getPhase()) {
                        case NIGHT_MAFIA -> advanceFromNightMafia(room);
                        case NIGHT_DOCTOR -> advanceFromNightDoctor(room);
                        case NIGHT_DETECTIVE -> resolveNight(room);
                        default -> { /* unreachable */ }
                    }
                    return room;
                });
    }

    // ── Auto-advance logic ───────────────────────────────────────────────────

    private void checkAutoAdvance(Room room) {
        switch (room.getPhase()) {
            case NIGHT_MAFIA -> {
                long aliveMafia = room.getAlivePlayers().stream()
                        .filter(p -> p.getRole() == Player.Role.MAFIA).count();
                if (room.getMafiaVotes().size() + room.getMafiaSkips().size() >= aliveMafia) {
                    advanceFromNightMafia(room);
                }
            }
            case NIGHT_DOCTOR -> {
                if (room.getNightActors().contains("DOCTOR")) {
                    advanceFromNightDoctor(room);
                }
            }
            case NIGHT_DETECTIVE -> {
                if (room.getNightActors().contains("DETECTIVE")) {
                    resolveNight(room);
                }
            }
            default -> { /* no auto-advance for DAY/VOTING/LOBBY */ }
        }
    }

    private void advanceFromNightMafia(Room room) {
        boolean doctorAlive = room.getAlivePlayers().stream()
                .anyMatch(p -> p.getRole() == Player.Role.DOCTOR);
        if (doctorAlive) {
            room.setPhase(GamePhase.NIGHT_DOCTOR);
        } else {
            advanceFromNightDoctor(room);
        }
    }

    private void advanceFromNightDoctor(Room room) {
        boolean detectiveAlive = room.getAlivePlayers().stream()
                .anyMatch(p -> p.getRole() == Player.Role.DETECTIVE);
        if (detectiveAlive) {
            room.setPhase(GamePhase.NIGHT_DETECTIVE);
        } else {
            resolveNight(room);
        }
    }

    private void resolveNight(Room room) {
        // Tally mafia votes — tie = no kill
        UUID targetId = null;
        Map<UUID, Long> tally = room.getMafiaVotes().values().stream()
                .collect(Collectors.groupingBy(id -> id, Collectors.counting()));
        if (!tally.isEmpty()) {
            long maxVotes = tally.values().stream().mapToLong(Long::longValue).max().orElse(0L);
            List<UUID> topVoted = tally.entrySet().stream()
                    .filter(e -> e.getValue() == maxVotes)
                    .map(Map.Entry::getKey)
                    .toList();
            if (topVoted.size() == 1) {
                targetId = topVoted.get(0);
            }
            // else: tie → no kill
        }
        UUID protectedId = room.getDoctorProtectedId();
        if (targetId != null && !targetId.equals(protectedId)) {
            final UUID finalTargetId = targetId;
            findPlayer(room, finalTargetId).ifPresent(p -> p.setAlive(false));
        }
        room.setLastDoctorProtectedId(room.getDoctorProtectedId());
        room.getMafiaVotes().clear();
        room.getMafiaSkips().clear();
        room.setDoctorProtectedId(null);
        room.getNightActors().clear();

        // Check win condition after night kills (mafia may have reached parity)
        Optional<Player.Role> win = checkWinCondition(room);
        if (win.isPresent()) {
            room.setPhase(GamePhase.ENDED);
            room.setWinner(win.get());
        } else {
            room.setPhase(GamePhase.DAY);
        }
    }

    // ── Day / Voting ────────────────────────────────────────────────────────

    public Optional<Room> endDay(UUID roomId, UUID playerId) {
        return roomService.getRoom(roomId)
                .filter(r -> playerId.equals(r.getHostId()))
                .filter(r -> r.getPhase() == GamePhase.DAY)
                .map(room -> {
                    room.getVotes().clear();
                    room.getVoteSkips().clear();
                    room.setPhase(GamePhase.VOTING);
                    return room;
                });
    }

    public Optional<Room> submitVote(UUID roomId, UUID voterId, UUID targetPlayerId) {
        return roomService.getRoom(roomId)
                .filter(r -> r.getPhase() == GamePhase.VOTING)
                .flatMap(room -> {
                    if (room.getVotes().containsKey(voterId) || room.getVoteSkips().contains(voterId)) {
                        return Optional.of(room);
                    }
                    room.getVotes().put(voterId, targetPlayerId);
                    if (room.getVotes().size() + room.getVoteSkips().size() >= room.getAlivePlayers().size()) {
                        return resolveVoting(roomId);
                    }
                    return Optional.of(room);
                });
    }

    public Optional<Room> skipVote(UUID roomId, UUID voterId) {
        return roomService.getRoom(roomId)
                .filter(r -> r.getPhase() == GamePhase.VOTING)
                .flatMap(room -> {
                    if (room.getVotes().containsKey(voterId) || room.getVoteSkips().contains(voterId)) {
                        return Optional.of(room);
                    }
                    room.getVoteSkips().add(voterId);
                    if (room.getVotes().size() + room.getVoteSkips().size() >= room.getAlivePlayers().size()) {
                        return resolveVoting(roomId);
                    }
                    return Optional.of(room);
                });
    }

    public Optional<Room> skipMafiaVote(UUID roomId, UUID playerId) {
        return roomService.getRoom(roomId)
                .filter(r -> r.getPhase() == GamePhase.NIGHT_MAFIA)
                .flatMap(room -> findPlayer(room, playerId)
                        .filter(p -> p.getRole() == Player.Role.MAFIA && p.isAlive())
                        .map(mafia -> {
                            room.getMafiaVotes().remove(playerId);
                            room.getMafiaSkips().add(playerId);
                            checkAutoAdvance(room);
                            return room;
                        }));
    }

    public Optional<Room> resolveVoting(UUID roomId) {
        return roomService.getRoom(roomId)
                .filter(r -> r.getPhase() == GamePhase.VOTING)
                .map(room -> {
                    Map<UUID, Long> tally = room.getVotes().values().stream()
                            .collect(Collectors.groupingBy(id -> id, Collectors.counting()));
                    if (!tally.isEmpty()) {
                        long maxVotes = tally.values().stream().mapToLong(Long::longValue).max().orElse(0L);
                        List<UUID> topVoted = tally.entrySet().stream()
                                .filter(e -> e.getValue() == maxVotes)
                                .map(Map.Entry::getKey)
                                .toList();
                        if (topVoted.size() == 1) {
                            findPlayer(room, topVoted.get(0)).ifPresent(p -> p.setAlive(false));
                        }
                        // else: tie → no elimination
                    }
                    room.getVotes().clear();
                    room.getVoteSkips().clear();
                    Optional<Player.Role> win = checkWinCondition(room);
                    if (win.isPresent()) {
                        room.setPhase(GamePhase.ENDED);
                        room.setWinner(win.get());
                    } else {
                        room.setPhase(GamePhase.NIGHT_MAFIA);
                        room.getNightActors().clear();
                    }
                    return room;
                });
    }

    // ── Win condition / end screen ──────────────────────────────────────────

    public Optional<Player.Role> checkWinCondition(Room room) {
        long mafiaAlive = room.getPlayers().stream()
                .filter(Player::isAlive)
                .filter(p -> p.getRole() == Player.Role.MAFIA)
                .count();
        long citizensAlive = room.getPlayers().stream()
                .filter(Player::isAlive)
                .filter(p -> p.getRole() != Player.Role.MAFIA)
                .count();
        if (mafiaAlive == 0) return Optional.of(Player.Role.CITIZEN);
        if (mafiaAlive >= citizensAlive) return Optional.of(Player.Role.MAFIA);
        return Optional.empty();
    }

    public Optional<Room> rematchVote(UUID roomId, UUID playerId, String choice) {
        return roomService.getRoom(roomId)
                .filter(r -> r.getPhase() == GamePhase.ENDED)
                .map(room -> {
                    if ("PLAY_AGAIN".equals(choice)) {
                        room.getLeaveVotes().remove(playerId);
                        room.getPlayAgainVotes().add(playerId);
                        boolean allVoted = room.getPlayers().stream()
                                .map(Player::getId)
                                .allMatch(id -> room.getPlayAgainVotes().contains(id));
                        if (allVoted && !room.getPlayers().isEmpty()) {
                            resetRoom(room);
                        }
                    } else if ("LEAVE".equals(choice)) {
                        room.getPlayAgainVotes().remove(playerId);
                        room.getLeaveVotes().add(playerId);
                    }
                    return room;
                });
    }

    private void resetRoom(Room room) {
        room.setPhase(GamePhase.LOBBY);
        room.setWinner(null);
        room.getVotes().clear();
        room.getVoteSkips().clear();
        room.getNightActors().clear();
        room.getMafiaVotes().clear();
        room.getMafiaSkips().clear();
        room.setDoctorProtectedId(null);
        room.setLastDoctorProtectedId(null);
        room.getPlayAgainVotes().clear();
        room.getLeaveVotes().clear();
        room.getPlayers().forEach(p -> {
            p.setRole(Player.Role.CITIZEN);
            p.setAlive(true);
        });
    }

    // ── Lookups ─────────────────────────────────────────────────────────────

    public Optional<Room> getRoom(UUID roomId) {
        return roomService.getRoom(roomId);
    }

    public Optional<Player> getPlayer(UUID roomId, UUID playerId) {
        return roomService.getRoom(roomId).flatMap(room -> findPlayer(room, playerId));
    }

    // ── Role assignment ──────────────────────────────────────────────────────

    private void assignRoles(Room room) {
        List<Player> players = room.getPlayers();
        Collections.shuffle(players);
        int mafiaCount = Math.max(1, room.getMafiaCount());
        for (int i = 0; i < players.size(); i++) {
            Player p = players.get(i);
            p.setAlive(true);
            if (i < mafiaCount) {
                p.setRole(Player.Role.MAFIA);
            } else if (i == mafiaCount && players.size() >= 5) {
                p.setRole(Player.Role.DETECTIVE);
            } else if (i == mafiaCount + 1 && players.size() >= 6) {
                p.setRole(Player.Role.DOCTOR);
            } else {
                p.setRole(Player.Role.CITIZEN);
            }
        }
    }

    private Optional<Player> findPlayer(Room room, UUID playerId) {
        return room.getPlayers().stream()
                .filter(p -> p.getId().equals(playerId))
                .findFirst();
    }
}
