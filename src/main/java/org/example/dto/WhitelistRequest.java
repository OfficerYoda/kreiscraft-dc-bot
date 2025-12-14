package org.example.dto;

public class WhitelistRequest {
    private final String userId;
    private final String playerName;

    public WhitelistRequest(String userId, String playerName) {
        this.userId = userId;
        this.playerName = playerName;
    }

    public String getUserId() {
        return userId;
    }

    public String getPlayerName() {
        return playerName;
    }
}
