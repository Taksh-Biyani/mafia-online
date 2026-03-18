package com.mafia.game.model;

public record ChatMessage(String playerName, String message, long timestamp, Channel channel) {
    public enum Channel { GENERAL, MAFIA_NIGHT, DEAD }
}
