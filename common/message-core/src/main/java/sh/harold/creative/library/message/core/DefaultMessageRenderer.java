package sh.harold.creative.library.message.core;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentBuilder;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import sh.harold.creative.library.message.Click;
import sh.harold.creative.library.message.MessageBlock;
import sh.harold.creative.library.message.MessageValue;
import sh.harold.creative.library.message.NoticeType;

import java.util.ArrayList;
import java.util.List;

final class DefaultMessageRenderer {

    static final DefaultMessageRenderer INSTANCE = new DefaultMessageRenderer();

    private static final StyleProfile INFO = new StyleProfile(NamedTextColor.GRAY, NamedTextColor.AQUA);
    private static final StyleProfile SUCCESS = new StyleProfile(NamedTextColor.GREEN, NamedTextColor.YELLOW);
    private static final StyleProfile ERROR = new StyleProfile(NamedTextColor.RED, NamedTextColor.RED);
    private static final StyleProfile DEBUG = new StyleProfile(NamedTextColor.GRAY, NamedTextColor.DARK_GRAY);
    private static final StyleProfile TOPIC = new StyleProfile(NamedTextColor.GRAY, NamedTextColor.YELLOW);
    private static final TextColor BLOCK_BODY = NamedTextColor.GRAY;
    private static final TextColor BLOCK_BULLET_PREFIX = NamedTextColor.DARK_GRAY;
    private static final TextColor CLICK_PROMPT = NamedTextColor.YELLOW;

    private DefaultMessageRenderer() {
    }

    Component renderInline(CompiledInlineMessage message, RenderTarget target) {
        Component rendered = switch (message) {
            case DefaultNoticeMessage notice -> renderNotice(notice, target);
            case DefaultTopicMessage topic -> renderTopic(topic, target);
        };
        return message.hoverBlock().map(hover -> rendered.hoverEvent(HoverEvent.showText(renderBlock(hover, RenderTarget.HOVER)))).orElse(rendered);
    }

    Component renderBlock(MessageBlock block, RenderTarget target) {
        List<Component> lines = new ArrayList<>();
        for (CompiledBlockEntry entry : compiledEntries(block)) {
            lines.add(renderEntry(entry, target));
        }
        if (lines.isEmpty()) {
            return Component.empty();
        }
        Component rendered = Component.join(net.kyori.adventure.text.JoinConfiguration.separator(Component.newline()), lines);
        return target == RenderTarget.HOVER ? stripInteractive(rendered) : rendered;
    }

    private Component renderNotice(DefaultNoticeMessage message, RenderTarget target) {
        StyleProfile style = noticeProfile(message.type());
        ComponentBuilder<?, ?> builder = Component.text();
        boolean first = true;
        for (sh.harold.creative.library.message.Tag tag : message.tags()) {
            if (!first) {
                builder.append(Component.space());
            }
            builder.append(Component.text("[" + tag.label() + "]", style.defaultValueColor()));
            first = false;
        }
        if (!message.tags().isEmpty()) {
            builder.append(Component.space());
        }
        appendTemplate(builder, message.compiledTemplate(), message.bindings(), style.bodyColor(), style.defaultValueColor(), target);
        return builder.build();
    }

    private Component renderTopic(DefaultTopicMessage message, RenderTarget target) {
        ComponentBuilder<?, ?> builder = Component.text();
        builder.append(Component.text(message.topic().label(), message.topic().color(), TextDecoration.BOLD));
        builder.append(Component.space());
        appendTemplate(builder, message.compiledTemplate(), message.bindings(), TOPIC.bodyColor(), TOPIC.defaultValueColor(), target);
        return builder.build();
    }

    private Component renderEntry(CompiledBlockEntry entry, RenderTarget target) {
        return switch (entry.entry()) {
            case MessageBlock.BlankEntry ignored -> Component.empty();
            case MessageBlock.TitleEntry title -> Component.text(title.text(), title.color(), TextDecoration.BOLD);
            case MessageBlock.LineEntry ignored -> {
                ComponentBuilder<?, ?> builder = Component.text();
                appendTemplate(builder, entry.template(), entry.bindings(), BLOCK_BODY, BLOCK_BODY, target);
                yield builder.build();
            }
            case MessageBlock.BulletEntry ignored -> {
                ComponentBuilder<?, ?> builder = Component.text();
                builder.append(Component.text("- ", BLOCK_BULLET_PREFIX));
                appendTemplate(builder, entry.template(), entry.bindings(), BLOCK_BODY, BLOCK_BODY, target);
                yield builder.build();
            }
        };
    }

    private List<CompiledBlockEntry> compiledEntries(MessageBlock block) {
        if (block instanceof CompiledMessageBlock compiledMessageBlock) {
            return compiledMessageBlock.compiledEntries();
        }

        List<CompiledBlockEntry> compiledEntries = new ArrayList<>();
        for (MessageBlock.Entry entry : block.entries()) {
            switch (entry) {
                case MessageBlock.BlankEntry ignored -> compiledEntries.add(new CompiledBlockEntry(entry, null));
                case MessageBlock.TitleEntry ignored -> compiledEntries.add(new CompiledBlockEntry(entry, null));
                case MessageBlock.LineEntry line -> {
                    CompiledTemplate template = CompiledTemplate.parse(line.template());
                    template.validate(line.bindings());
                    compiledEntries.add(new CompiledBlockEntry(entry, template));
                }
                case MessageBlock.BulletEntry bullet -> {
                    CompiledTemplate template = CompiledTemplate.parse(bullet.template());
                    template.validate(bullet.bindings());
                    compiledEntries.add(new CompiledBlockEntry(entry, template));
                }
            }
        }
        return compiledEntries;
    }

    private void appendTemplate(
            ComponentBuilder<?, ?> builder,
            CompiledTemplate template,
            java.util.Map<String, MessageValue> bindings,
            TextColor bodyColor,
            TextColor defaultValueColor,
            RenderTarget target
    ) {
        for (CompiledTemplate.Segment segment : template.segments()) {
            switch (segment) {
                case CompiledTemplate.Segment.Text text -> builder.append(Component.text(text.value(), bodyColor));
                case CompiledTemplate.Segment.Slot slot -> builder.append(renderValue(bindings.get(slot.name()), defaultValueColor, target));
                case CompiledTemplate.Segment.ClickPrompt prompt -> builder.append(renderClickPrompt(bindings.get(prompt.name()), target));
            }
        }
    }

    private Component renderValue(MessageValue value, TextColor defaultValueColor, RenderTarget target) {
        Component component = value.component();
        if (value.colorOverride().isPresent()) {
            component = applyColor(component, value.colorOverride().orElseThrow(), true);
        } else {
            component = applyColor(component, defaultValueColor, false);
        }

        if (target != RenderTarget.HOVER) {
            if (value.clickAction().isPresent()) {
                component = component.clickEvent(toAdventureClick(value.clickAction().orElseThrow()));
            }
            if (value.hoverBlock().isPresent()) {
                component = component.hoverEvent(HoverEvent.showText(renderBlock(value.hoverBlock().orElseThrow(), RenderTarget.HOVER)));
            }
        } else {
            component = stripInteractive(component);
        }

        return component;
    }

    private Component renderClickPrompt(MessageValue value, RenderTarget target) {
        Component prompt = Component.text("CLICK", CLICK_PROMPT, TextDecoration.BOLD);
        if (target != RenderTarget.HOVER) {
            if (value.clickAction().isPresent()) {
                prompt = prompt.clickEvent(toAdventureClick(value.clickAction().orElseThrow()));
            }
            if (value.hoverBlock().isPresent()) {
                prompt = prompt.hoverEvent(HoverEvent.showText(renderBlock(value.hoverBlock().orElseThrow(), RenderTarget.HOVER)));
            }
        }
        return prompt;
    }

    private Component applyColor(Component component, TextColor color, boolean overwrite) {
        Component recolored = overwrite ? component.color(color) : component.colorIfAbsent(color);
        List<Component> children = component.children();
        if (children.isEmpty()) {
            return recolored;
        }
        List<Component> recoloredChildren = new ArrayList<>(children.size());
        for (Component child : children) {
            recoloredChildren.add(applyColor(child, color, overwrite));
        }
        return recolored.children(recoloredChildren);
    }

    private Component stripInteractive(Component component) {
        Component stripped = component.clickEvent(null).hoverEvent(null);
        List<Component> children = component.children();
        if (children.isEmpty()) {
            return stripped;
        }
        List<Component> strippedChildren = new ArrayList<>(children.size());
        for (Component child : children) {
            strippedChildren.add(stripInteractive(child));
        }
        return stripped.children(strippedChildren);
    }

    private ClickEvent toAdventureClick(Click click) {
        return switch (click) {
            case Click.OpenUrl openUrl -> ClickEvent.openUrl(openUrl.url());
            case Click.RunCommand runCommand -> ClickEvent.runCommand(runCommand.command());
            case Click.SuggestCommand suggestCommand -> ClickEvent.suggestCommand(suggestCommand.command());
            case Click.CopyToClipboard copyToClipboard -> ClickEvent.copyToClipboard(copyToClipboard.value());
            case Click.ChangePage changePage -> ClickEvent.changePage(changePage.page());
        };
    }

    private StyleProfile noticeProfile(NoticeType type) {
        return switch (type) {
            case INFO -> INFO;
            case SUCCESS -> SUCCESS;
            case ERROR -> ERROR;
            case DEBUG -> DEBUG;
        };
    }

    private record StyleProfile(TextColor bodyColor, TextColor defaultValueColor) {
    }
}
