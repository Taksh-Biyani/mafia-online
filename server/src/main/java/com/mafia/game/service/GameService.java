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

    public Optional<Room> startGame(UUID roomId) {
        return roomService.getRoom(roomId).flatMap(room -> {
            synchronized (room) {
                if (room.getPhase() != GamePhase.LOBBY) return Optional.empty();
                if (room.getPlayers().size() < room.getMinPlayers()) return Optional.empty();
                assignRoles(room);
                room.setPhase(GamePhase.DAY);
                return Optional.of(room);
            }
        });
    }

    // ── Night sub-phase actions ─────────────────────────────────────────────

    public Optional<Room> submitNightAction(UUID roomId, UUID playerId, UUID targetPlayerId) {
        return roomService.getRoom(roomId).flatMap(room -> {
            synchronized (room) {
                if (room.getPhase() != GamePhase.NIGHT_MAFIA) return Optional.empty();
                Optional<Player> mafia = findPlayer(room, playerId)
                        .filter(p -> p.getRole() == Player.Role.MAFIA && p.isAlive());
                if (mafia.isEmpty()) return Optional.empty();
                room.getMafiaVotes().put(playerId, targetPlayerId);
                checkAutoAdvance(room);
                return Optional.of(room);
            }
        });
    }

    public Optional<Room> submitDoctorProtect(UUID roomId, UUID playerId, UUID targetPlayerId) {
        return roomService.getRoom(roomId).flatMap(room -> {
            synchronized (room) {
                if (room.getPhase() != GamePhase.NIGHT_DOCTOR) return Optional.empty();
                Optional<Player> doctor = findPlayer(room, playerId)
                        .filter(p -> p.getRole() == Player.Role.DOCTOR && p.isAlive());
                if (doctor.isEmpty()) return Optional.empty();
                if (targetPlayerId.equals(room.getLastDoctorProtectedId())) return Optional.empty();
                room.setDoctorProtectedId(targetPlayerId);
                room.getNightActors().add("DOCTOR");
                checkAutoAdvance(room);
                return Optional.of(room);
            }
        });
    }

    public Optional<Player> submitDetectiveInvestigate(UUID roomId, UUID playerId, UUID targetPlayerId) {
        return roomService.getRoom(roomId).flatMap(room -> {
            synchronized (room) {
                if (room.getPhase() != GamePhase.NIGHT_DETECTIVE) return Optional.empty();
                Optional<Player> detective = findPlayer(room, playerId)
                        .filter(p -> p.getRole() == Player.Role.DETECTIVE && p.isAlive());
                if (detective.isEmpty()) return Optional.empty();
                room.getNightActors().add("DETECTIVE");
                checkAutoAdvance(room);
                return findPlayer(room, targetPlayerId);
            }
        });
    }

    public Optional<Room> endNight(UUID roomId) {
        return roomService.getRoom(roomId).flatMap(room -> {
            synchronized (room) {
                switch (room.getPhase()) {
                    case NIGHT_MAFIA -> advanceFromNightMafia(room);
                    case NIGHT_DOCTOR -> advanceFromNightDoctor(room);
                    case NIGHT_DETECTIVE -> resolveNight(room);
                    default -> { return Optional.empty(); }
                }
                return Optional.of(room);
            }
        });
    }

    // ── Auto-advance logic ───────────────────────────────────────────────────

    // Must be called while holding synchronized(room)
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
        return roomService.getRoom(roomId).flatMap(room -> {
            synchronized (room) {
                if (!playerId.equals(room.getHostId())) return Optional.empty();
                if (room.getPhase() != GamePhase.DAY) return Optional.empty();
                room.getVotes().clear();
                room.getVoteSkips().clear();
                room.setPhase(GamePhase.VOTING);
                return Optional.of(room);
            }
        });
    }

    public Optional<Room> submitVote(UUID roomId, UUID voterId, UUID targetPlayerId) {
        return roomService.getRoom(roomId).flatMap(room -> {
            synchronized (room) {
                if (room.getPhase() != GamePhase.VOTING) return Optional.empty();
                if (room.getVotes().containsKey(voterId) || room.getVoteSkips().contains(voterId)) {
                    return Optional.of(room);
                }
                room.getVotes().put(voterId, targetPlayerId);
                if (room.getVotes().size() + room.getVoteSkips().size() >= room.getAlivePlayers().size()) {
                    return resolveVotingUnderLock(room);
                }
                return Optional.of(room);
            }
        });
    }

    public Optional<Room> skipVote(UUID roomId, UUID voterId) {
        return roomService.getRoom(roomId).flatMap(room -> {
            synchronized (room) {
                if (room.getPhase() != GamePhase.VOTING) return Optional.empty();
                if (room.getVotes().containsKey(voterId) || room.getVoteSkips().contains(voterId)) {
                    return Optional.of(room);
                }
                room.getVoteSkips().add(voterId);
                if (room.getVotes().size() + room.getVoteSkips().size() >= room.getAlivePlayers().size()) {
                    return resolveVotingUnderLock(room);
                }
                return Optional.of(room);
            }
        });
    }

    public Optional<Room> skipMafiaVote(UUID roomId, UUID playerId) {
        return roomService.getRoom(roomId).flatMap(room -> {
            synchronized (room) {
                if (room.getPhase() != GamePhase.NIGHT_MAFIA) return Optional.empty();
                Optional<Player> mafia = findPlayer(room, playerId)
                        .filter(p -> p.getRole() == Player.Role.MAFIA && p.isAlive());
                if (mafia.isEmpty()) return Optional.empty();
                room.getMafiaVotes().remove(playerId);
                room.getMafiaSkips().add(playerId);
                checkAutoAdvance(room);
                return Optional.of(room);
            }
        });
    }

    // Called by the host-only /vote/resolve endpoint
    public Optional<Room> resolveVoting(UUID roomId) {
        return roomService.getRoom(roomId).flatMap(room -> {
            synchronized (room) {
                if (room.getPhase() != GamePhase.VOTING) return Optional.empty();
                return resolveVotingUnderLock(room);
            }
        });
    }

    // Must be called while holding synchronized(room) and after confirming VOTING phase
    private Optional<Room> resolveVotingUnderLock(Room room) {
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
        return Optional.of(room);
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
        return roomService.getRoom(roomId).flatMap(room -> {
            synchronized (room) {
                if (room.getPhase() != GamePhase.ENDED) return Optional.empty();
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
                return Optional.of(room);
            }
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
