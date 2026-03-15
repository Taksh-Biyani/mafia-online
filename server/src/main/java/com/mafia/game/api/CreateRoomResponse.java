package com.mafia.game.api;

import com.mafia.game.model.Player;
import com.mafia.game.room.Room;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Response DTO for room creation that includes both the created room and the creator player.
 */
@Data
@AllArgsConstructor
public class CreateRoomResponse {
    /** The created room */
    private Room room;
    
    /** The player who created the room (automatically joined) */
    private Player creator;
}
