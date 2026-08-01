package sh.harold.library.message.core;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import sh.harold.library.message.MessageBlock;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class DefaultMessageBlock implements MessageBlock, CompiledMessageBlock {

    private final List<CompiledBlockEntry> compiledEntries;
    private final List<Entry> entries;
    private final boolean centered;

    public DefaultMessageBlock(List<CompiledBlockEntry> compiledEntries) {
        this(compiledEntries, false);
    }

    public DefaultMessageBlock(List<CompiledBlockEntry> compiledEntries, boolean centered) {
        Objects.requireNonNull(compiledEntries, "compiledEntries");
        ArrayList<CompiledBlockEntry> copiedCompiled = new ArrayList<>();
        ArrayList<Entry> copiedEntries = new ArrayList<>();
        for (CompiledBlockEntry entry : compiledEntries) {
            Objects.requireNonNull(entry, "entry");
            copiedCompiled.add(entry);
            copiedEntries.add(entry.entry());
        }
        this.compiledEntries = List.copyOf(copiedCompiled);
        this.entries = List.copyOf(copiedEntries);
        this.centered = centered;
    }

    @Override
    public List<Entry> entries() {
        return entries;
    }

    @Override
    public boolean centered() {
        return centered;
    }

    @Override
    public Component component() {
        return DefaultMessageRenderer.INSTANCE.renderBlock(this, RenderTarget.CHAT);
    }

    @Override
    public void send(Audience audience) {
        Objects.requireNonNull(audience, "audience");
        audience.sendMessage(component());
    }

    @Override
    public List<CompiledBlockEntry> compiledEntries() {
        return compiledEntries;
    }
}
