package de.officeryoda.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.kittinunf.fuel.Fuel;
import com.github.kittinunf.fuel.core.FuelError;
import com.github.kittinunf.fuel.core.Response;
import com.github.kittinunf.result.Result;
import de.officeryoda.config.Config;
import de.officeryoda.dto.WhitelistRequest;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import kotlin.Triple;

public class WhitelistService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final File whitelistedPlayersFile = new File(Config.get("whitelisted.players.file"));
    private final File pendingPlayersFile = new File(Config.get("pending.players.file"));

    public void addToWhitelist(WhitelistRequest request) {
        String url = Config.get("whitelist.api.url");
        String jsonBody = "{\"playerName\": \"" + request.getPlayerName() + "\"}";

        Triple<com.github.kittinunf.fuel.core.Request, Response, Result<String, FuelError>> result = Fuel.post(url)
                .header("Content-Type", "application/json")
                .body(jsonBody)
                .responseString();

        if (result.getThird() instanceof Result.Failure) {
            addPendingPlayer(request);
        } else {
            addWhitelistedPlayer(request.getPlayerName());
        }
    }

    public List<String> getWhitelistedPlayers() {
        try {
            if (!whitelistedPlayersFile.exists()) {
                return new ArrayList<>();
            }
            return objectMapper.readValue(whitelistedPlayersFile, new TypeReference<>() {});
        } catch (IOException e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    private void addWhitelistedPlayer(String playerName) {
        List<String> players = getWhitelistedPlayers();
        players.add(playerName);
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
            return objectMapper.readValue(pendingPlayersFile, new TypeReference<>() {});
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
