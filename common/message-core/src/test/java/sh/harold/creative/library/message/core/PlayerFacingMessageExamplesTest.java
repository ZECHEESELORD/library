package sh.harold.creative.library.message.core;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.junit.jupiter.api.Test;
import sh.harold.creative.library.message.Click;
import sh.harold.creative.library.message.Message;
import sh.harold.creative.library.message.MessageBlock;
import sh.harold.creative.library.message.NoticeMessage;
import sh.harold.creative.library.message.Tag;
import sh.harold.creative.library.message.Topic;
import sh.harold.creative.library.message.TopicMessage;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayerFacingMessageExamplesTest {

    private static final Topic SOUL = Topic.of("SOUL!", NamedTextColor.LIGHT_PURPLE);
    private static final Topic AREA = Topic.of("AREA!", NamedTextColor.GOLD);
    private static final Topic SKILL = Topic.of("SKILL!", NamedTextColor.AQUA);
    private static final Topic EVENT = Topic.of("EVENT!", NamedTextColor.DARK_PURPLE);
    private static final Topic BOSS = Topic.of("BOSS!", NamedTextColor.RED);
    private static final Topic QUEST = Topic.of("QUEST!", NamedTextColor.GREEN);
    private static final Topic WARNING = Topic.of("WARNING!", NamedTextColor.RED);
    private static final Topic DUEL = Topic.of("DUEL!", NamedTextColor.YELLOW);

    @Test
    void noticeExamplesMatchApprovedCatalog() {
        List<Component> rendered = List.of(
                renderChat(Message.info(
                        "Set rank of {player} to {rank}.",
                        Message.slot("player", "ZeCheeseLord"),
                        Message.slot("rank", Message.value("ADMIN").color(0x55FF55))
                ).tag(Tag.STAFF)),
                renderChat(Message.info(
                        "Synced {count} player documents.",
                        Message.slot("count", 142)
                ).tag(Tag.DAEMON)),
                renderChat(Message.success(
                        "Granted {amount} coins.",
                        Message.slot("amount", 500)
                )),
                renderChat(Message.success(
                        "Saved kit {kit}.",
                        Message.slot("kit", "Archer")
                )),
                renderChat(Message.error(
                        "You need {rank} to use this command.",
                        Message.slot("rank", "MVP+")
                )),
                renderChat(Message.error(
                        "Muted {player} for {duration}.",
                        Message.slot("player", "Notch"),
                        Message.slot("duration", "7 days")
                )),
                renderChat(Message.debug(
                        "Debug mode enabled for {match}.",
                        Message.slot("match", "Match-12")
                )),
                renderChat(Message.debug(
                        "Loaded arena {arena}.",
                        Message.slot("arena", "Frozen Peak")
                )),
                renderChat(Message.error(
                        "Could not find player {player}.",
                        Message.slot("player", "CheeseMage")
                ))
        );

        assertCatalogSnapshot(
                """
                TEXT:
                [STAFF] Set rank of ZeCheeseLord to ADMIN.
                TREE:
                text("")
                  text("[STAFF]") color=aqua
                  text(" ")
                  text("Set rank of ") color=gray
                  text("ZeCheeseLord") color=aqua
                  text(" to ") color=gray
                  text("ADMIN") color=green
                  text(".") color=gray
                ---
                TEXT:
                [DAEMON] Synced 142 player documents.
                TREE:
                text("")
                  text("[DAEMON]") color=aqua
                  text(" ")
                  text("Synced ") color=gray
                  text("142") color=aqua
                  text(" player documents.") color=gray
                ---
                TEXT:
                Granted 500 coins.
                TREE:
                text("")
                  text("Granted ") color=green
                  text("500") color=yellow
                  text(" coins.") color=green
                ---
                TEXT:
                Saved kit Archer.
                TREE:
                text("")
                  text("Saved kit ") color=green
                  text("Archer") color=yellow
                  text(".") color=green
                ---
                TEXT:
                You need MVP+ to use this command.
                TREE:
                text("")
                  text("You need ") color=red
                  text("MVP+") color=red
                  text(" to use this command.") color=red
                ---
                TEXT:
                Muted Notch for 7 days.
                TREE:
                text("")
                  text("Muted ") color=red
                  text("Notch") color=red
                  text(" for ") color=red
                  text("7 days") color=red
                  text(".") color=red
                ---
                TEXT:
                Debug mode enabled for Match-12.
                TREE:
                text("")
                  text("Debug mode enabled for ") color=gray
                  text("Match-12") color=dark_gray
                  text(".") color=gray
                ---
                TEXT:
                Loaded arena Frozen Peak.
                TREE:
                text("")
                  text("Loaded arena ") color=gray
                  text("Frozen Peak") color=dark_gray
                  text(".") color=gray
                ---
                TEXT:
                Could not find player CheeseMage.
                TREE:
                text("")
                  text("Could not find player ") color=red
                  text("CheeseMage") color=red
                  text(".") color=red
                """,
                rendered
        );
    }

    @Test
    void topicExamplesMatchApprovedCatalog() {
        List<Component> rendered = List.of(
                renderChat(Message.topic(
                        SOUL,
                        "You found {count} fairy soul!",
                        Message.slot("count", 1)
                )),
                renderChat(Message.topic(
                        AREA,
                        "You discovered {area}!",
                        Message.slot("area", "The Barn")
                )),
                renderChat(Message.topic(
                        SKILL,
                        "Your Mining skill leveled up to {level}!",
                        Message.slot("level", 12)
                )),
                renderChat(Message.topic(
                        EVENT,
                        "Spooky Festival has begun!"
                )),
                renderChat(Message.topic(
                        BOSS,
                        "The {boss} has spawned!",
                        Message.slot("boss", "Broodmother")
                )),
                renderChat(Message.topic(
                        QUEST,
                        "Talk to the {npc}!",
                        Message.slot("npc", "Blacksmith")
                )),
                renderChat(Message.topic(
                        WARNING,
                        "Server restart in {time}!",
                        Message.slot("time", "5 minutes")
                )),
                renderChat(Message.topic(
                        DUEL,
                        "{challenger} challenged you!",
                        Message.slot("challenger", "ZeCheeseLord")
                ))
        );

        assertCatalogSnapshot(
                """
                TEXT:
                SOUL! You found 1 fairy soul!
                TREE:
                text("")
                  text("SOUL!") color=light_purple bold
                  text(" ")
                  text("You found ") color=gray
                  text("1") color=yellow
                  text(" fairy soul!") color=gray
                ---
                TEXT:
                AREA! You discovered The Barn!
                TREE:
                text("")
                  text("AREA!") color=gold bold
                  text(" ")
                  text("You discovered ") color=gray
                  text("The Barn") color=yellow
                  text("!") color=gray
                ---
                TEXT:
                SKILL! Your Mining skill leveled up to 12!
                TREE:
                text("")
                  text("SKILL!") color=aqua bold
                  text(" ")
                  text("Your Mining skill leveled up to ") color=gray
                  text("12") color=yellow
                  text("!") color=gray
                ---
                TEXT:
                EVENT! Spooky Festival has begun!
                TREE:
                text("")
                  text("EVENT!") color=dark_purple bold
                  text(" ")
                  text("Spooky Festival has begun!") color=gray
                ---
                TEXT:
                BOSS! The Broodmother has spawned!
                TREE:
                text("")
                  text("BOSS!") color=red bold
                  text(" ")
                  text("The ") color=gray
                  text("Broodmother") color=yellow
                  text(" has spawned!") color=gray
                ---
                TEXT:
                QUEST! Talk to the Blacksmith!
                TREE:
                text("")
                  text("QUEST!") color=green bold
                  text(" ")
                  text("Talk to the ") color=gray
                  text("Blacksmith") color=yellow
                  text("!") color=gray
                ---
                TEXT:
                WARNING! Server restart in 5 minutes!
                TREE:
                text("")
                  text("WARNING!") color=red bold
                  text(" ")
                  text("Server restart in ") color=gray
                  text("5 minutes") color=yellow
                  text("!") color=gray
                ---
                TEXT:
                DUEL! ZeCheeseLord challenged you!
                TREE:
                text("")
                  text("DUEL!") color=yellow bold
                  text(" ")
                  text("ZeCheeseLord") color=yellow
                  text(" challenged you!") color=gray
                """,
                rendered
        );
    }

    @Test
    void clickPromptExamplesMatchApprovedCatalog() {
        List<Component> rendered = List.of(
                renderBlock(clickPrompt("view on the", "wiki", "Official Skyblock Wiki", "https://example.com/wiki")),
                renderBlock(clickPrompt("join the", "discord", "Official Discord", "https://example.com/discord")),
                renderBlock(clickPrompt("open the", "store", "Store", "https://example.com/store")),
                renderBlock(clickPrompt("read the", "rules", "Rules", "https://example.com/rules"))
        );

        assertCatalogSnapshot(
                """
                TEXT:

                 CLICK to view on the Official Skyblock Wiki!

                TREE:
                text("")
                  text("\\n")
                  text(" ") color=gray
                  text("CLICK") color=yellow bold click=open_url(https://example.com/wiki)
                  text(" to view on the ") color=gray
                  text("Official Skyblock Wiki") color=green click=open_url(https://example.com/wiki)
                  text("!") color=gray
                  text("\\n")
                ---
                TEXT:

                 CLICK to join the Official Discord!

                TREE:
                text("")
                  text("\\n")
                  text(" ") color=gray
                  text("CLICK") color=yellow bold click=open_url(https://example.com/discord)
                  text(" to join the ") color=gray
                  text("Official Discord") color=green click=open_url(https://example.com/discord)
                  text("!") color=gray
                  text("\\n")
                ---
                TEXT:

                 CLICK to open the Store!

                TREE:
                text("")
                  text("\\n")
                  text(" ") color=gray
                  text("CLICK") color=yellow bold click=open_url(https://example.com/store)
                  text(" to open the ") color=gray
                  text("Store") color=green click=open_url(https://example.com/store)
                  text("!") color=gray
                  text("\\n")
                ---
                TEXT:

                 CLICK to read the Rules!

                TREE:
                text("")
                  text("\\n")
                  text(" ") color=gray
                  text("CLICK") color=yellow bold click=open_url(https://example.com/rules)
                  text(" to read the ") color=gray
                  text("Rules") color=green click=open_url(https://example.com/rules)
                  text("!") color=gray
                  text("\\n")
                """,
                rendered
        );
    }

    @Test
    void blockExampleMatchesApprovedCatalog() {
        MessageBlock block = Message.block()
                .title("QUEST COMPLETE", 0x55FF55)
                .line(
                        "You completed {quest}!",
                        Message.slot("quest", Message.value("Farmer's Favor").color(NamedTextColor.YELLOW))
                )
                .blank()
                .line("Rewards:")
                .bullet(
                        "{reward}",
                        Message.slot("reward", Message.value("2,500 Coins").color(NamedTextColor.YELLOW))
                )
                .bullet(
                        "{reward}",
                        Message.slot("reward", Message.value("450 Farming XP").color(NamedTextColor.GREEN))
                )
                .bullet(
                        "{reward}",
                        Message.slot("reward", Message.value("Access to the Windmill").color(NamedTextColor.AQUA))
                )
                .build();

        assertEquals(
                normalize(
                        """
                        TEXT:

                        QUEST COMPLETE
                         You completed Farmer's Favor!
                        
                         Rewards:
                         • 2,500 Coins
                         • 450 Farming XP
                         • Access to the Windmill
                        
                        TREE:
                        text("")
                          text("\\n")
                          text("QUEST COMPLETE") color=green bold
                          text("\\n")
                          text(" ") color=gray
                          text("You completed ") color=gray
                          text("Farmer's Favor") color=yellow
                          text("!") color=gray
                          text("\\n")
                          text("\\n")
                          text(" ") color=gray
                          text("Rewards:") color=gray
                          text("\\n")
                          text(" ") color=gray
                          text("• ") color=dark_gray
                          text("2,500 Coins") color=yellow
                          text("\\n")
                          text(" ") color=gray
                          text("• ") color=dark_gray
                          text("450 Farming XP") color=green
                          text("\\n")
                          text(" ") color=gray
                          text("• ") color=dark_gray
                          text("Access to the Windmill") color=aqua
                          text("\\n")
                        """
                ),
                ComponentSnapshot.snapshot(renderBlock(block))
        );
    }

    private static MessageBlock clickPrompt(String action, String slotName, String label, String url) {
        return Message.block()
                .line(
                        "{click:" + slotName + "} to " + action + " {" + slotName + "}!",
                        Message.slot(slotName, Message.value(label)
                                .color(0x55FF55)
                                .click(Click.openUrl(url)))
                )
                .build();
    }

    private static Component renderChat(NoticeMessage message) {
        return DefaultMessageRenderer.INSTANCE.renderInline((CompiledInlineMessage) message, RenderTarget.CHAT);
    }

    private static Component renderChat(TopicMessage message) {
        return DefaultMessageRenderer.INSTANCE.renderInline((CompiledInlineMessage) message, RenderTarget.CHAT);
    }

    private static Component renderBlock(MessageBlock block) {
        return DefaultMessageRenderer.INSTANCE.renderBlock(block, RenderTarget.CHAT);
    }

    private static void assertCatalogSnapshot(String expected, List<Component> rendered) {
        assertEquals(
                normalize(expected),
                rendered.stream().map(ComponentSnapshot::snapshot).collect(Collectors.joining("\n---\n"))
        );
    }

    private static String normalize(String value) {
        return value.stripIndent().stripLeading().stripTrailing();
    }
}
