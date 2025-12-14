package de.officeryoda.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.officeryoda.config.Config;
import de.officeryoda.dto.WhitelistRequest;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class WhitelistService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final File whitelistedPlayersFile = new File(Config.get("WHITELISTED_PLAYERS_FILE"));
    private final File pendingPlayersFile = new File(Config.get("PENDING_PLAYERS_FILE"));

    public void addToWhitelist(WhitelistRequest request) {
        if (processWhitelistRequest(request)) {
            addWhitelistedPlayer(request.playerName());
        } else {
            addPendingPlayer(request);
        }
    }

    public void retryPendingRequests() {
        List<WhitelistRequest> pendingPlayers = getPendingPlayers();
        if (pendingPlayers.isEmpty()) {
            return;
        }

        List<WhitelistRequest> successfullyProcessed = new ArrayList<>();
        for (WhitelistRequest request : pendingPlayers) {
            if (processWhitelistRequest(request)) {
                addWhitelistedPlayer(request.playerName());
                successfullyProcessed.add(request);
            }
        }

        if (!successfullyProcessed.isEmpty()) {
            pendingPlayers.removeAll(successfullyProcessed);
            savePlayers(pendingPlayersFile, pendingPlayers);
        }
    }

    private boolean processWhitelistRequest(WhitelistRequest request) {
        String url = Config.get("WHITELIST_API_URL");
        String jsonBody;
        try {
            jsonBody = new ObjectMapper().writeValueAsString(request);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            e.printStackTrace();
            return false;
        }
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        try {
            HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() >= 200 && response.statusCode() < 300;
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            return false;
        }
    }

    public List<String> getWhitelistedPlayers() {
        try {
            if (!whitelistedPlayersFile.exists()) {
                return new ArrayList<>();
            }
            return objectMapper.readValue(whitelistedPlayersFile, new TypeReference<>() {
            });
        } catch (IOException e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    private void addWhitelistedPlayer(String playerName) {
        List<String> players = getWhitelistedPlayers();
        players.add(playerName);
        Collections.sort(players);
        savePlayers(whitelistedPlayersFile, players);
    }

    private void addPendingPlayer(WhitelistRequest request) {
        List<WhitelistRequest> pending = getPendingPlayers();
        pending.add(request);
        savePlayers(pendingPlayersFile, pending);
    }

    public List<WhitelistRequest> getPendingPlayers() {
        try {
            if (!pendingPlayersFile.exists()) {
                return new ArrayList<>();
            }
            return objectMapper.readValue(pendingPlayersFile, new TypeReference<>() {
            });
        } catch (IOException e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    private void savePlayers(File file, Object data) {
        try {
            objectMapper.writeValue(file, data);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
