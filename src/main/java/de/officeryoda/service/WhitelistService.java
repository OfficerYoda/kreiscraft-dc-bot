package de.officeryoda.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.officeryoda.config.Config;
import de.officeryoda.dto.WhitelistRequest;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class WhitelistService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Path whitelistedPlayersFile = Paths.get(Config.get("WHITELISTED_PLAYERS_FILE"));
    private final Path pendingPlayersFile = Paths.get(Config.get("PENDING_PLAYERS_FILE"));

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

    public void syncWhitelistedPlayers() {
        String apiUrl = Config.get("WHITELIST_API_URL");
        if (apiUrl == null || apiUrl.isEmpty()) {
            System.err.println("WHITELIST_API_URL not configured.");
            return;
        }

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .GET()
                .build();

        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                List<String> players = objectMapper.readValue(response.body(), new TypeReference<List<String>>() {
                });
                savePlayers(whitelistedPlayersFile, players);
                System.out.println("Successfully synced whitelisted players from API.");
            } else {
                System.err
                        .println("Failed to fetch whitelisted players from API. Status code: " + response.statusCode());
            }
        } catch (IOException | InterruptedException e) {
            System.err.println("Error syncing whitelisted players: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public List<String> getWhitelistedPlayers() {
        try {
            if (!Files.exists(whitelistedPlayersFile)) {
                return new ArrayList<>();
            }
            return objectMapper.readValue(Files.readString(whitelistedPlayersFile), new TypeReference<List<String>>() {
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
            if (!Files.exists(pendingPlayersFile)) {
                return new ArrayList<>();
            }
            return objectMapper.readValue(Files.readString(pendingPlayersFile),
                    new TypeReference<List<WhitelistRequest>>() {
                    });
        } catch (IOException e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    private void savePlayers(Path file, Object data) {
        try {
            if (!Files.exists(file.getParent())) {
                Files.createDirectories(file.getParent());
            }
            objectMapper.writeValue(file.toFile(), data);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
