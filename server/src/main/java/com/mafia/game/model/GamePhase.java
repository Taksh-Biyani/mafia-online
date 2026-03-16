package com.mafia.game.model;

/**
 * Enum representing the different phases of a Mafia game.
 * - LOBBY: Waiting for players to join the game
 * - NIGHT: Mafia and special roles (Detective, Doctor) take actions
 * - DAY: Discussion phase where players can talk
 * - VOTING: Players vote to eliminate someone from the game
 * - ENDED: Game has concluded with a winner
 */
public enum GamePhase {
    /** Lobby phase: game is waiting for players and hasn't started yet */
    LOBBY,
    
    /** Night phase: mafia and special roles perform their actions */
    NIGHT,
    
    /** Day phase: all players are awake and can discuss */
    DAY,
    
    /** Voting phase: players vote on who to eliminate during the day */
    VOTING,
    
    /** Game has ended with a winning team */
    ENDED
}
