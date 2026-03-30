package sh.harold.creative.library.example.minestom;

import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.entity.Player;
import sh.harold.creative.library.message.Click;
import sh.harold.creative.library.message.Message;
import sh.harold.creative.library.message.MessageBlock;
import sh.harold.creative.library.message.NoticeMessage;
import sh.harold.creative.library.message.Tag;
import sh.harold.creative.library.message.Topic;
import sh.harold.creative.library.message.TopicMessage;
import sh.harold.creative.library.message.minestom.MinestomMessageSender;

import java.util.List;

public final class MinestomMessageFacadeExamples {

    private static final Topic SOUL = Topic.of("SOUL!", NamedTextColor.LIGHT_PURPLE);
    private static final Topic AREA = Topic.of("AREA!", NamedTextColor.GOLD);
    private static final Topic SKILL = Topic.of("SKILL!", NamedTextColor.AQUA);
    private static final Topic EVENT = Topic.of("EVENT!", NamedTextColor.DARK_PURPLE);
    private static final Topic BOSS = Topic.of("BOSS!", NamedTextColor.RED);
    private static final Topic QUEST = Topic.of("QUEST!", NamedTextColor.GREEN);
    private static final Topic WARNING = Topic.of("WARNING!", NamedTextColor.RED);
    private static final Topic DUEL = Topic.of("DUEL!", NamedTextColor.YELLOW);

    private final MinestomMessageSender sender = new MinestomMessageSender();

    public void sendAll(Player player) {
        sendNotices(player);
        sendTopics(player);
        sendClickPrompts(player);
        sendBlocks(player);
    }

    public void sendNotices(Player player) {
        for (NoticeMessage message : noticeExamples()) {
            sender.send(player, message);
        }
    }

    public void sendTopics(Player player) {
        for (TopicMessage message : topicExamples()) {
            sender.send(player, message);
        }
    }

    public void sendClickPrompts(Player player) {
        for (MessageBlock block : clickPromptExamples()) {
            sender.send(player, block);
        }
    }

    public void sendBlocks(Player player) {
        sender.send(player, questCompleteBlock());
    }

    private List<NoticeMessage> noticeExamples() {
        return List.of(
                Message.info(
                        "Set rank of {player} to {rank}.",
                        Message.slot("player", "ZeCheeseLord"),
                        Message.slot("rank", ExampleMessageValues.rank("ADMIN"))
                ).tag(Tag.STAFF),
                Message.info(
                        "Synced {count} player documents.",
                        Message.slot("count", 142)
                ).tag(Tag.DAEMON),
                Message.success(
                        "Granted {amount} coins.",
                        Message.slot("amount", 500)
                ),
                Message.success(
                        "Saved kit {kit}.",
                        Message.slot("kit", "Archer")
                ),
                Message.error(
                        "You need {rank} to use this command.",
                        Message.slot("rank", "MVP+")
                ),
                Message.error(
                        "Muted {player} for {duration}.",
                        Message.slot("player", "Notch"),
                        Message.slot("duration", "7 days")
                ),
                Message.debug(
                        "Debug mode enabled for {match}.",
                        Message.slot("match", "Match-12")
                ),
                Message.debug(
                        "Loaded arena {arena}.",
                        Message.slot("arena", "Frozen Peak")
                ),
                Message.error(
                        "Could not find player {player}.",
                        Message.slot("player", "CheeseMage")
                )
        );
    }

    private List<TopicMessage> topicExamples() {
        return List.of(
                Message.topic(
                        SOUL,
                        "You found {count} fairy soul!",
                        Message.slot("count", 1)
                ),
                Message.topic(
                        AREA,
                        "You discovered {area}!",
                        Message.slot("area", "The Barn")
                ),
                Message.topic(
                        SKILL,
                        "Your Mining skill leveled up to {level}!",
                        Message.slot("level", 12)
                ),
                Message.topic(
                        EVENT,
                        "Spooky Festival has begun!"
                ),
                Message.topic(
                        BOSS,
                        "The {boss} has spawned!",
                        Message.slot("boss", "Broodmother")
                ),
                Message.topic(
                        QUEST,
                        "Talk to the {npc}!",
                        Message.slot("npc", "Blacksmith")
                ),
                Message.topic(
                        WARNING,
                        "Server restart in {time}!",
                        Message.slot("time", "5 minutes")
                ),
                Message.topic(
                        DUEL,
                        "{challenger} challenged you!",
                        Message.slot("challenger", "ZeCheeseLord")
                )
        );
    }

    private List<MessageBlock> clickPromptExamples() {
        return List.of(
                clickPrompt("view on the", "wiki", "Official Skyblock Wiki", "https://example.com/wiki"),
                clickPrompt("join the", "discord", "Official Discord", "https://example.com/discord"),
                clickPrompt("open the", "store", "Store", "https://example.com/store"),
                clickPrompt("read the", "rules", "Rules", "https://example.com/rules")
        );
    }

    private MessageBlock questCompleteBlock() {
        return Message.block()
                .title("QUEST COMPLETE", 0x55FF55)
                .line(
                        "You completed {quest}!",
                        Message.slot("quest", ExampleMessageValues.highlight("Farmer's Favor"))
                )
                .blank()
                .line("Rewards:")
                .bullet(
                        "{reward}",
                        Message.slot("reward", ExampleMessageValues.coinsReward(2_500))
                )
                .bullet(
                        "{reward}",
                        Message.slot("reward", ExampleMessageValues.xpReward(450, "Farming"))
                )
                .bullet(
                        "{reward}",
                        Message.slot("reward", ExampleMessageValues.unlock("Access to the Windmill"))
                )
                .build();
    }

    private MessageBlock clickPrompt(String action, String slotName, String label, String url) {
        return Message.block()
                .line(
                        "{click:" + slotName + "} to " + action + " {" + slotName + "}!",
                        Message.slot(slotName, Message.value(ExampleMessageValues.linkLabel(label))
                                .click(Click.openUrl(url)))
                )
                .build();
    }
}
