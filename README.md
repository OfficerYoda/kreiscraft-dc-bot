# Kreiscraft DC Bot

This is a Discord bot for managing a whitelist on a game server.

## Features

- Users can request to be whitelisted by sending their player name in a specific
  Discord channel.
- Moderators can approve or deny whitelist requests through a private channel.
- Approved players are added to the whitelist via an API call.
- If the API call fails, the player name is saved to a file for later
  processing.
- A list of all whitelisted players is displayed in the whitelist channel.
- All data is persisted in a `data` directory, which can be mounted as a Docker
  volume.

## Setup

1. **Clone the repository:**

   ```bash
   git clone https://github.com/OfficerYoda/kreiscraft-dc-bot.git
   cd kreiscraft-dc-bot
   ```

2. **Configure the bot (Environment Variables):** The bot is configured using
   environment variables. The following variables are mandatory:
   - `BOT_TOKEN`: Your Discord bot token.
   - `GUILD_ID`: The ID of your Discord server.
   - `WHITELIST_CHANNEL_ID`: The ID of the channel where users can request to be
     whitelisted.
   - `WHITELIST_APPROVALS_CHANNEL_ID`: The ID of the channel where moderators
     can approve or deny requests.
   - `WHITELIST_API_URL`: The URL of your game server's whitelist API.
   - `MODERATOR_ROLE_ID`: The ID of the Discord role that identifies moderators.

3. **Build the bot:**

   ```bash
   ./gradlew shadowJar
   ```

## Running the Bot

### Using Docker

This is the recommended way to run the bot, as it ensures that the bot runs in a
consistent environment and that data is persisted across container restarts.

1. **Build the Docker image:**

   ```bash
   docker build -t kreiscraft-dc-bot .
   ```

2. **Run the Docker container:**

   ```bash
   docker run -d \
     -v $(pwd)/kreiscraft-dc-bot/data:/app/data \
     -e BOT_TOKEN="YOUR_BOT_TOKEN" \
     -e GUILD_ID="YOUR_GUILD_ID" \
     -e WHITELIST_CHANNEL_ID="YOUR_WHITELIST_CHANNEL_ID" \
     -e WHITELIST_APPROVALS_CHANNEL_ID="YOUR_WHITELIST_APPROVALS_CHANNEL_ID" \
     -e WHITELIST_API_URL="http://your-server-api.com/whitelist" \
     -e MODERATOR_ROLE_ID="YOUR_MODERATOR_ROLE_ID" \
     --name kreiscraft-dc-bot kreiscraft-dc-bot
   ```

   This will run the bot in the background and mount the `data` directory on
   your host machine to the `/app/data` directory in the container. This ensures
   that the `whitelisted.json` and `pending.json` files are persisted even if
   the container is removed.

### Using Java

You can also run the bot directly using Java, but you will need to have Java 21
or higher installed.

```bash
java -jar build/libs/kreiscraft-dc-bot.jar
```
