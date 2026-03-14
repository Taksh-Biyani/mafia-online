package com.mafia.game.model;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Player {
    private UUID id;
    private String name;
    private Role role;
    private boolean alive;
    private UUID roomId;

    public enum Role {
        CITIZEN,
        MAFIA,
        DETECTIVE,
        DOCTOR
    }
}
