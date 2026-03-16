package com.mafia.game.room;

import com.mafia.game.model.GamePhase;
import com.mafia.game.model.Player;
import com.mafia.game.service.RoomService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class RoomServiceTest {

    @Autowired
    private RoomService roomService;

    @Autowired
    private RoomManager roomManager;

    private Room testRoom;

    @BeforeEach
    void setUp() {
        // Clear all rooms
        roomManager.getAllRooms().keySet().forEach(roomManager::removeRoom);

        // Create a test room
        testRoom = roomManager.createRoom("SERVICETEST", 3, 6, 30);
    }

    @Test
    void testCreateRoom() {
        // Given
        String joinCode = "CREATETEST";
        int minPlayers = 4;
        int maxPlayers = 8;

        // When
        Room createdRoom = roomService.createRoom(joinCode, minPlayers, maxPlayers, 30);

        // Then
        assertNotNull(createdRoom);
        assertEquals(joinCode, createdRoom.getJoinCode());
        assertEquals(minPlayers, createdRoom.getMinPlayers());
        assertEquals(maxPlayers, createdRoom.getMaxPlayers());
        assertEquals(GamePhase.LOBBY, createdRoom.getPhase());
    }

    @Test
    void testGetRoom() {
        // When
        Optional<Room> retrievedRoom = roomService.getRoom(testRoom.getId());

        // Then
        assertTrue(retrievedRoom.isPresent());
        assertEquals(testRoom, retrievedRoom.get());
    }

    @Test
    void testGetRoom_NotFound() {
        // Given
        UUID nonExistentId = UUID.randomUUID();

        // When
        Optional<Room> room = roomService.getRoom(nonExistentId);

        // Then
        assertFalse(room.isPresent());
    }

    @Test
    void testGetRoomByJoinCode() {
        // When
        Optional<Room> retrievedRoom = roomService.getRoomByJoinCode(testRoom.getJoinCode());

        // Then
        assertTrue(retrievedRoom.isPresent());
        assertEquals(testRoom, retrievedRoom.get());
    }

    @Test
    void testListRooms_InLobbyPhase() {
        // Given - testRoom is in LOBBY phase

        // When
        List<Room> lobbyRooms = roomService.listRooms();

        // Then
        assertEquals(1, lobbyRooms.size());
        assertEquals(testRoom, lobbyRooms.get(0));
    }

    @Test
    void testListRooms_NotInLobby() {
        // Given - change room to NIGHT phase
        testRoom.setPhase(GamePhase.NIGHT);

        // When
        List<Room> lobbyRooms = roomService.listRooms();

        // Then
        assertTrue(lobbyRooms.isEmpty());
    }

    @Test
    void testJoinRoom() {
        // Given
        String playerName = "TestPlayer";

        // When
        Optional<Player> joinedPlayer = roomService.joinRoom(testRoom.getId(), playerName);

        // Then
        assertTrue(joinedPlayer.isPresent());
        assertEquals(playerName, joinedPlayer.get().getName());
        assertEquals(Player.Role.CITIZEN, joinedPlayer.get().getRole());
        assertTrue(joinedPlayer.get().isAlive());
        assertEquals(testRoom.getId(), joinedPlayer.get().getRoomId());

        // Verify player was added to room
        assertEquals(1, testRoom.getPlayers().size());
        assertTrue(testRoom.getPlayers().contains(joinedPlayer.get()));
    }

    @Test
    void testJoinRoom_BlankName() {
        // Given
        String blankName = "   ";

        // When
        Optional<Player> joinedPlayer = roomService.joinRoom(testRoom.getId(), blankName);

        // Then
        assertTrue(joinedPlayer.isPresent());
        assertEquals("Player", joinedPlayer.get().getName());
    }

    @Test
    void testJoinRoom_RoomNotFound() {
        // Given
        UUID nonExistentId = UUID.randomUUID();

        // When
        Optional<Player> joinedPlayer = roomService.joinRoom(nonExistentId, "TestPlayer");

        // Then
        assertFalse(joinedPlayer.isPresent());
    }

    @Test
    void testJoinRoom_RoomFull() {
        // Given - fill the room to max capacity
        for (int i = 0; i < testRoom.getMaxPlayers(); i++) {
            Player player = Player.builder()
                    .id(UUID.randomUUID())
                    .name("Player" + i)
                    .role(Player.Role.CITIZEN)
                    .alive(true)
                    .roomId(testRoom.getId())
                    .build();
            testRoom.getPlayers().add(player);
        }

        // When
        Optional<Player> joinedPlayer = roomService.joinRoom(testRoom.getId(), "NewPlayer");

        // Then
        assertFalse(joinedPlayer.isPresent());
    }

    @Test
    void testJoinRoom_NotInLobby() {
        // Given - change room to NIGHT phase
        testRoom.setPhase(GamePhase.NIGHT);

        // When
        Optional<Player> joinedPlayer = roomService.joinRoom(testRoom.getId(), "TestPlayer");

        // Then
        assertFalse(joinedPlayer.isPresent());
    }

    @Test
    void testJoinRoomByCode() {
        // Given
        String playerName = "CodeJoiner";

        // When
        Optional<Player> joinedPlayer = roomService.joinRoomByCode(testRoom.getJoinCode(), playerName);

        // Then
        assertTrue(joinedPlayer.isPresent());
        assertEquals(playerName, joinedPlayer.get().getName());
        assertEquals(1, testRoom.getPlayers().size());
    }

    @Test
    void testJoinRoomByCode_InvalidCode() {
        // Given
        String invalidCode = "INVALID";

        // When
        Optional<Player> joinedPlayer = roomService.joinRoomByCode(invalidCode, "TestPlayer");

        // Then
        assertFalse(joinedPlayer.isPresent());
    }

    @Test
    void testLeaveRoom() {
        // Given - add a player first
        Player player = Player.builder()
                .id(UUID.randomUUID())
                .name("LeavingPlayer")
                .role(Player.Role.CITIZEN)
                .alive(true)
                .roomId(testRoom.getId())
                .build();
        testRoom.getPlayers().add(player);

        // When
        boolean left = roomService.leaveRoom(testRoom.getId(), player.getId());

        // Then
        assertTrue(left);
        assertFalse(testRoom.getPlayers().contains(player));
        assertEquals(0, testRoom.getPlayers().size());
    }

    @Test
    void testLeaveRoom_PlayerNotFound() {
        // Given
        UUID nonExistentPlayerId = UUID.randomUUID();

        // When
        boolean left = roomService.leaveRoom(testRoom.getId(), nonExistentPlayerId);

        // Then
        assertFalse(left);
    }

    @Test
    void testLeaveRoom_RoomNotFound() {
        // Given
        UUID nonExistentRoomId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();

        // When
        boolean left = roomService.leaveRoom(nonExistentRoomId, playerId);

        // Then
        assertFalse(left);
    }

    @Test
    void testRemoveRoom() {
        // Given
        UUID roomId = testRoom.getId();
        String joinCode = testRoom.getJoinCode();

        // Verify room exists
        assertTrue(roomService.getRoom(roomId).isPresent());
        assertTrue(roomService.getRoomByJoinCode(joinCode).isPresent());

        // When
        roomService.removeRoom(roomId);

        // Then
        assertFalse(roomService.getRoom(roomId).isPresent());
        assertFalse(roomService.getRoomByJoinCode(joinCode).isPresent());
    }

    @Test
    void testRemoveRoom_NonExistent() {
        // Given
        UUID nonExistentId = UUID.randomUUID();

        // When & Then - should not throw exception
        assertDoesNotThrow(() -> roomService.removeRoom(nonExistentId));
    }
}
