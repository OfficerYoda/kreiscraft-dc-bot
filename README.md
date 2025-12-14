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

2. **Configure the bot:** Create a `config.properties` file in
   `src/main/resources` with the following content:

   ```properties
   bot.token=YOUR_BOT_TOKEN
   guild.id=YOUR_GUILD_ID
   whitelist.channel.id=YOUR_WHITELIST_CHANNEL_ID
   whitelist.approvals.channel.id=YOUR_WHITELIST_APPROVALS_CHANNEL_ID
   whitelist.api.url=http://your-server-api.com/whitelist
   whitelisted.players.file=data/whitelisted.json
   pending.players.file=data/pending.json
   ```

   - `bot.token`: Your Discord bot token.
   - `guild.id`: The ID of your Discord server.
   - `whitelist.channel.id`: The ID of the channel where users can request to be
     whitelisted.
   - `whitelist.approvals.channel.id`: The ID of the channel where moderators
     can approve or deny requests.
   - `whitelist.api.url`: The URL of your game server's whitelist API.
   - `whitelisted.players.file`: The path to the file where whitelisted players
     are stored.
   - `pending.players.file`: The path to the file where pending players are
     stored.

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
   docker run -d -v $(pwd)/data:/app/data --name kreiscraft-dc-bot kreiscraft-dc-bot
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

## Retry Mechanism

If the whitelist API is unavailable, the bot will save the player name to a
`pending.json` file. You will need to implement a mechanism to retry these
pending requests at a later time. This could be a separate script that is run
periodically, or you could add a command to the bot to trigger a retry.
