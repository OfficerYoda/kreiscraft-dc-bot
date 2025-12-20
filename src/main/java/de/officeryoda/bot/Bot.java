package de.officeryoda.bot;

import de.officeryoda.config.Config;
import de.officeryoda.dto.WhitelistRequest;
import de.officeryoda.service.WhitelistService;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class Bot extends ListenerAdapter {

    private final WhitelistService whitelistService = new WhitelistService();
    private final Set<String> pendingApprovalPlayers = new HashSet<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private JDA jda;

    public static void main(String[] args) throws InterruptedException {
        Bot bot = new Bot();
        bot.start();
    }

    public void start() throws InterruptedException {
        jda = JDABuilder.createDefault(Config.get("BOT_TOKEN"))
                .addEventListeners(this)
                .build()
                .awaitReady();

        Guild guild = jda.getGuildById(Config.getAsLong("GUILD_ID"));
        if (guild != null) {
            guild.upsertCommand("whitelist", "Add a player to the Kreiscraft whitelist")
                    .addOption(OptionType.STRING, "playername", "The name of the player to whitelist", true)
                    .queue();

            guild.upsertCommand("sync-whitelist", "Sync the whitelist from the bot with the server")
                    .queue();
        }

        scheduler.scheduleAtFixedRate(() -> {
            whitelistService.retryPendingRequests();
            whitelistService.syncWhitelistedPlayers();
            updateWhitelistChannelEmbed(jda);
        }, 0, 5, TimeUnit.MINUTES);
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        switch (event.getName()) {
            case "whitelist":
                handleWhitelistCommand(event);
                break;
            case "sync-whitelist":
                handleRetryWhitelistCommand(event);
                break;
        }
    }

    private void handleWhitelistCommand(SlashCommandInteractionEvent event) {
        String playerName = Objects.requireNonNull(event.getOption("playername")).getAsString();
        String userId = event.getUser().getId();

        // Check if the playername is already pending
        boolean isPending = pendingApprovalPlayers.stream().anyMatch(p -> p.equalsIgnoreCase(playerName)) ||
                whitelistService.getPendingPlayers().stream()
                        .map(WhitelistRequest::playerName)
                        .anyMatch(p -> p.equalsIgnoreCase(playerName));

        // if (isPending) {
        // event.reply("A request for player `" + playerName + "` is already pending.")
        // .setEphemeral(true)
        // .queue(message -> message.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));
        // return;
        // }

        // Check if the playername is already whitelisted
        boolean isWhitelisted = whitelistService.getWhitelistedPlayers().stream()
                .anyMatch(p -> p.equalsIgnoreCase(playerName));
        if (isWhitelisted) {
            event.reply("Player `" + playerName + "` is already whitelisted.")
                    .setEphemeral(true)
                    .queue(message -> message.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));
            return;
        }

        whitelistService.addToWhitelist(new WhitelistRequest(playerName));
        event.reply("Player `" + playerName + "` has been added to the whitelist.")
                .setEphemeral(true)
                .queue(message -> message.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));

        // pendingApprovalPlayers.add(playerName);

        // event.reply("Your request to whitelist player `" + playerName + "` has been
        // submitted for approval.")
        // .setEphemeral(true)
        // .queue(message -> message.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));

        // TextChannel approvalChannel = event.getGuild()
        // .getTextChannelById(Config.getAsLong("WHITELIST_APPROVALS_CHANNEL_ID"));
        // if (approvalChannel != null) {
        // MessageEmbed approvalEmbed = new MessageEmbed(
        // null,
        // "Whitelist Request: " + playerName,
        // "User: " + event.getUser().getAsMention() + "\nPlayer Name: `" + playerName +
        // "`",
        // null,
        // null,
        // 15158332,
        // null,
        // null,
        // null,
        // null,
        // null,
        // null,
        // null);
        //
        // approvalChannel.sendMessageEmbeds(approvalEmbed)
        // .setActionRow(
        // Button.success("approve:" + userId + ":" + playerName, "Approve"),
        // Button.danger("deny:" + userId + ":" + playerName, "Deny"))
        // .queue();
        // }
    }

    private void handleRetryWhitelistCommand(SlashCommandInteractionEvent event) {
        long moderatorRoleId = Config.getAsLong("MODERATOR_ROLE_ID");
        boolean isModerator = event.getMember().getRoles().stream()
                .anyMatch(role -> role.getIdLong() == moderatorRoleId);

        if (!isModerator) {
            event.reply("You don't have the required role to execute this command.")
                    .setEphemeral(true)
                    .queue(message -> message.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));
            return;
        }

        event.deferReply(true).queue();
        whitelistService.retryPendingRequests();
        whitelistService.syncWhitelistedPlayers();
        updateWhitelistChannelEmbed(jda);
        event.getHook().sendMessage("Retried whitelisting all pending players.")
                .setEphemeral(true)
                .queue(message -> message.delete().queueAfter(5, TimeUnit.SECONDS));
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String[] buttonIdParts = event.getComponentId().split(":");
        String action = buttonIdParts[0];
        String userId = buttonIdParts[1];
        String playerName = buttonIdParts[2];

        event.deferEdit().queue();

        switch (action) {
            case "approve":
                pendingApprovalPlayers.removeIf(p -> p.equalsIgnoreCase(playerName));
                whitelistService.addToWhitelist(new WhitelistRequest(playerName));
                event.getJDA().retrieveUserById(userId).queue(user -> user.openPrivateChannel()
                        .queue(channel -> channel
                                .sendMessage(
                                        "Your whitelist request for player `" + playerName + "` has been **approved**.")
                                .queue()));
                event.getChannel().sendMessage("Approved: " + playerName)
                        .queue(message -> message.delete().queueAfter(5, TimeUnit.SECONDS));
                updateWhitelistChannelEmbed(event.getJDA());
                break;
            case "deny":
                pendingApprovalPlayers.removeIf(p -> p.equalsIgnoreCase(playerName));
                event.getJDA().retrieveUserById(userId).queue(user -> user.openPrivateChannel().queue(channel -> channel
                        .sendMessage("Your whitelist request for player `" + playerName + "` has been **denied**.")
                        .queue()));
                event.getChannel().sendMessage("Denied: " + playerName)
                        .queue(message -> message.delete().queueAfter(5, TimeUnit.SECONDS));
                break;
        }

        event.getMessage().delete().queue();
    }

    private void updateWhitelistChannelEmbed(JDA jda) {
        TextChannel whitelistChannel = jda.getTextChannelById(Config.getAsLong("WHITELIST_CHANNEL_ID"));
        if (whitelistChannel != null) {
            whitelistChannel.getHistory().retrievePast(1).queue(messages -> {
                MessageEmbed whitelistEmbed = new MessageEmbed(
                        null,
                        "Whitelisted Players",
                        String.join("\n", whitelistService.getWhitelistedPlayers()),
                        null,
                        null,
                        15844367,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null);
                if (!messages.isEmpty() && messages.get(0).getAuthor().isBot()) {
                    messages.get(0).editMessageEmbeds(whitelistEmbed).queue();
                } else {
                    whitelistChannel.sendMessageEmbeds(whitelistEmbed).queue();
                }
            });
        }
    }
}
