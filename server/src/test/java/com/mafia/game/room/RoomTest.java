package com.mafia.game.room;

import com.mafia.game.model.GamePhase;
import com.mafia.game.model.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RoomTest {

    private Room room;
    private Player player1;
    private Player player2;
    private Player player3;

    @BeforeEach
    void setUp() {
        room = Room.builder()
                .id(UUID.randomUUID())
                .joinCode("TESTROOM")
                .phase(GamePhase.LOBBY)
                .minPlayers(3)
                .maxPlayers(6)
                .build();

        player1 = Player.builder()
                .id(UUID.randomUUID())
                .name("Alice")
                .role(Player.Role.CITIZEN)
                .alive(true)
                .roomId(room.getId())
                .build();

        player2 = Player.builder()
                .id(UUID.randomUUID())
                .name("Bob")
                .role(Player.Role.MAFIA)
                .alive(true)
                .roomId(room.getId())
                .build();

        player3 = Player.builder()
                .id(UUID.randomUUID())
                .name("Charlie")
                .role(Player.Role.DETECTIVE)
                .alive(false)
                .roomId(room.getId())
                .build();

        room.getPlayers().add(player1);
        room.getPlayers().add(player2);
        room.getPlayers().add(player3);
    }

    @Test
    void testIsFull_WhenNotFull() {
        // Given - room with 3 players, max 6

        // When
        boolean isFull = room.isFull();

        // Then
        assertFalse(isFull);
    }

    @Test
    void testIsFull_WhenFull() {
        // Given - add 3 more players to reach max
        for (int i = 0; i < 3; i++) {
            Player player = Player.builder()
                    .id(UUID.randomUUID())
                    .name("Player" + i)
                    .role(Player.Role.CITIZEN)
                    .alive(true)
                    .roomId(room.getId())
                    .build();
            room.getPlayers().add(player);
        }

        // When
        boolean isFull = room.isFull();

        // Then
        assertTrue(isFull);
    }

    @Test
    void testCanStartGame_WhenEnoughPlayers() {
        // Given - room with 3 players, min 3

        // When
        boolean canStart = room.canStartGame();

        // Then
        assertTrue(canStart);
    }

    @Test
    void testCanStartGame_WhenNotEnoughPlayers() {
        // Given - remove one player to go below minimum
        room.getPlayers().remove(player1);

        // When
        boolean canStart = room.canStartGame();

        // Then
        assertFalse(canStart);
    }

    @Test
    void testGetAlivePlayerCount() {
        // Given - 2 alive players (player3 is dead)

        // When
        int aliveCount = room.getAlivePlayerCount();

        // Then
        assertEquals(2, aliveCount);
    }

    @Test
    void testGetAlivePlayerCount_AllDead() {
        // Given - set all players to dead
        player1.setAlive(false);
        player2.setAlive(false);
        player3.setAlive(false);

        // When
        int aliveCount = room.getAlivePlayerCount();

        // Then
        assertEquals(0, aliveCount);
    }

    @Test
    void testGetAlivePlayerCountByRole_Citizen() {
        // Given - player1 is CITIZEN and alive

        // When
        int citizenCount = room.getAlivePlayerCountByRole(Player.Role.CITIZEN);

        // Then
        assertEquals(1, citizenCount);
    }

    @Test
    void testGetAlivePlayerCountByRole_Mafia() {
        // Given - player2 is MAFIA and alive

        // When
        int mafiaCount = room.getAlivePlayerCountByRole(Player.Role.MAFIA);

        // Then
        assertEquals(1, mafiaCount);
    }

    @Test
    void testGetAlivePlayerCountByRole_Detective() {
        // Given - player3 is DETECTIVE but dead

        // When
        int detectiveCount = room.getAlivePlayerCountByRole(Player.Role.DOCTOR);

        // Then
        assertEquals(0, detectiveCount);
    }

    @Test
    void testGetAlivePlayers() {
        // Given - 2 alive players

        // When
        var alivePlayers = room.getAlivePlayers();

        // Then
        assertEquals(2, alivePlayers.size());
        assertTrue(alivePlayers.contains(player1));
        assertTrue(alivePlayers.contains(player2));
        assertFalse(alivePlayers.contains(player3));
    }

    @Test
    void testFindPlayer_ExistingPlayer() {
        // When
        Optional<Player> found = room.findPlayer(player1.getId());

        // Then
        assertTrue(found.isPresent());
        assertEquals(player1, found.get());
    }

    @Test
    void testFindPlayer_NonExistentPlayer() {
        // Given
        UUID nonExistentId = UUID.randomUUID();

        // When
        Optional<Player> found = room.findPlayer(nonExistentId);

        // Then
        assertFalse(found.isPresent());
    }

    @Test
    void testHasPlayer_ExistingPlayer() {
        // When
        boolean hasPlayer = room.hasPlayer(player2.getId());

        // Then
        assertTrue(hasPlayer);
    }

    @Test
    void testHasPlayer_NonExistentPlayer() {
        // Given
        UUID nonExistentId = UUID.randomUUID();

        // When
        boolean hasPlayer = room.hasPlayer(nonExistentId);

        // Then
        assertFalse(hasPlayer);
    }

    @Test
    void testGetCurrentPlayerCount() {
        // Given - 3 players in room

        // When
        int count = room.getCurrentPlayerCount();

        // Then
        assertEquals(3, count);
    }

    @Test
    void testGetCurrentPlayerCount_EmptyRoom() {
        // Given - empty room
        Room emptyRoom = Room.builder()
                .id(UUID.randomUUID())
                .joinCode("EMPTY")
                .minPlayers(2)
                .maxPlayers(4)
                .build();

        // When
        int count = emptyRoom.getCurrentPlayerCount();

        // Then
        assertEquals(0, count);
    }

    @Test
    void testRoomBuilder() {
        // Given & When
        Room builtRoom = Room.builder()
                .id(UUID.randomUUID())
                .joinCode("BUILDER")
                .phase(GamePhase.DAY)
                .minPlayers(2)
                .maxPlayers(5)
                .build();

        // Then
        assertNotNull(builtRoom.getId());
        assertEquals("BUILDER", builtRoom.getJoinCode());
        assertEquals(GamePhase.DAY, builtRoom.getPhase());
        assertEquals(2, builtRoom.getMinPlayers());
        assertEquals(5, builtRoom.getMaxPlayers());
        assertNotNull(builtRoom.getPlayers());
        assertTrue(builtRoom.getPlayers().isEmpty());
    }
}
