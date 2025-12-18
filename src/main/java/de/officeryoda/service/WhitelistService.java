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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class WhitelistService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    // In-memory storage replacing the files
    private final List<String> whitelistedPlayers = Collections.synchronizedList(new ArrayList<>());
    private final List<WhitelistRequest> pendingPlayers = Collections.synchronizedList(new ArrayList<>());

    public void addToWhitelist(WhitelistRequest request) {
        if (processWhitelistRequest(request)) {
            addWhitelistedPlayer(request.playerName());
        } else {
            addPendingPlayer(request);
        }
    }

    public void retryPendingRequests() {
        if (pendingPlayers.isEmpty()) {
            return;
        }

        List<WhitelistRequest> successfullyProcessed = new ArrayList<>();

        synchronized (pendingPlayers) {
            for (WhitelistRequest request : pendingPlayers) {
                if (processWhitelistRequest(request)) {
                    addWhitelistedPlayer(request.playerName());
                    successfullyProcessed.add(request);
                }
            }
            pendingPlayers.removeAll(successfullyProcessed);
        }
    }

    private boolean processWhitelistRequest(WhitelistRequest request) {
        String url = Config.get("WHITELIST_API_URL");
        try {
            String jsonBody = objectMapper.writeValueAsString(request);
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() >= 200 && response.statusCode() < 300;
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            return false;
        }
    }

    public void syncWhitelistedPlayers() {
        String apiUrl = Config.get("WHITELIST_API_URL");
        if (apiUrl == null || apiUrl.isEmpty())
            return;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                List<String> players = objectMapper.readValue(response.body(), new TypeReference<List<String>>() {
                });

                synchronized (whitelistedPlayers) {
                    whitelistedPlayers.clear();
                    whitelistedPlayers.addAll(players);
                    Collections.sort(whitelistedPlayers);
                }
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    public List<String> getWhitelistedPlayers() {
        return new ArrayList<>(whitelistedPlayers);
    }

    private void addWhitelistedPlayer(String playerName) {
        synchronized (whitelistedPlayers) {
            if (!whitelistedPlayers.contains(playerName)) {
                whitelistedPlayers.add(playerName);
                Collections.sort(whitelistedPlayers);
            }
        }
    }

    private void addPendingPlayer(WhitelistRequest request) {
        pendingPlayers.add(request);
    }

    public List<WhitelistRequest> getPendingPlayers() {
        return new ArrayList<>(pendingPlayers);
    }
}
