package com.mafia.game.api;

import lombok.Data;

/**
 * Request DTO for joining a game room.
 * Contains the player name for the joining player.
 */
@Data
public class JoinRoomRequest {
    /** The display name for the player joining the room. */
    private String playerName;

    /** Cloudflare Turnstile response token from the client widget. */
    private String captchaToken;
}
