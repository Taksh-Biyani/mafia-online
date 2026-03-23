package com.mafia.game.api;

import com.mafia.game.model.Player;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class JoinRoomResponse {
    private Player player;
    private String playerToken;
}
