package com.mafia.game.model;

/**
 * Enum representing the different phases of a Mafia game.
 * Night is split into three sequential sub-phases so each role acts in order.
 */
public enum GamePhase {
    /** Lobby phase: game is waiting for players and hasn't started yet */
    LOBBY,

    /** Day phase: all players discuss who they suspect */
    DAY,

    /** Voting phase: players vote on who to eliminate */
    VOTING,

    /** Night sub-phase 1: mafia collectively chooses a kill target */
    NIGHT_MAFIA,

    /** Night sub-phase 2: doctor chooses who to protect (skipped if no doctor alive) */
    NIGHT_DOCTOR,

    /** Night sub-phase 3: detective investigates a player (skipped if no detective alive) */
    NIGHT_DETECTIVE,

    /** Game has ended with a winning team */
    ENDED
}
