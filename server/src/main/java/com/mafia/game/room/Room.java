package com.mafia.game.room;

import com.mafia.game.model.GamePhase;
import com.mafia.game.model.Player;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Room {
    private UUID id;
    private String joinCode;
    private GamePhase phase;
    private int minPlayers;
    private int maxPlayers;
    @Builder.Default
    private List<Player> players = new ArrayList<>();
}
