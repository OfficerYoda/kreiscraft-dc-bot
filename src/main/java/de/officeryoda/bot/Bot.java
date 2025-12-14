package de.officeryoda.bot;

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
import de.officeryoda.config.Config;
import de.officeryoda.dto.WhitelistRequest;
import de.officeryoda.service.WhitelistService;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class Bot extends ListenerAdapter {

    private final WhitelistService whitelistService = new WhitelistService();

    public static void main(String[] args) throws InterruptedException {
        JDA jda = JDABuilder.createDefault(Config.get("bot.token"))
                .addEventListeners(new Bot())
                .build()
                .awaitReady();

        Guild guild = jda.getGuildById(Config.get("guild.id"));
        if (guild != null) {
            guild.upsertCommand("whitelist", "Add a player to the Kreiscraft whitelist")
                    .addOption(OptionType.STRING, "playername", "The name of the player to whitelist", true)
                    .queue();
        }
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("whitelist")) {
            return;
        }

        if (!event.getChannel().getId().equals(Config.get("whitelist.channel.id"))) {
            event.reply("This command can only be used in the whitelist channel.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        String playerName = Objects.requireNonNull(event.getOption("playername")).getAsString();
        String userId = event.getUser().getId();

        // Check if the playername is already pending
        Set<String> pendingPlayernames = whitelistService.getPendingPlayers()
                .stream()
                .map(it -> it.playerName())
                .collect(Collectors.toSet());
        if (pendingPlayernames.contains(playerName)) {
            event.reply("A request for player `" + playerName + "` is already pending approval.")
                    .setEphemeral(true)
                    .queue(message -> message.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));
            return;
        }

        Set<String> whitelistedPlayernames = new HashSet<>(whitelistService.getWhitelistedPlayers());
        if (whitelistedPlayernames.contains(playerName)) {
            event.reply("Player `" + playerName + "` is already whitelisted.")
                    .setEphemeral(true)
                    .queue(message -> message.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));
            return;
        }

        event.reply("Your request to whitelist player `" + playerName + "` has been submitted for approval.")
                .setEphemeral(true)
                .queue(message -> message.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));

        TextChannel approvalChannel = event.getGuild().getTextChannelById(Config.get("whitelist.approvals.channel.id"));
        if (approvalChannel != null) {
            MessageEmbed approvalEmbed = new MessageEmbed(
                    null,
                    "Whitelist Request: " + playerName,
                    "User: " + event.getUser().getAsMention() + "\nPlayer Name: `" + playerName + "`",
                    null,
                    null,
                    15158332,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null);

            approvalChannel.sendMessageEmbeds(approvalEmbed)
                    .setActionRow(
                            Button.success("approve:" + userId + ":" + playerName, "Approve"),
                            Button.danger("deny:" + userId + ":" + playerName, "Deny"))
                    .queue();
        }
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
                whitelistService.addToWhitelist(new WhitelistRequest(userId, playerName));
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
        TextChannel whitelistChannel = jda.getTextChannelById(Config.get("whitelist.channel.id"));
        if (whitelistChannel != null) {
            whitelistChannel.getHistory().retrievePast(1).queue(messages -> {
                if (!messages.isEmpty() && messages.get(0).getAuthor().isBot()) {
                    messages.get(0).delete().queue();
                }

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
                whitelistChannel.sendMessageEmbeds(whitelistEmbed).queue();
            });
        }
    }
}
