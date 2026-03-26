package sh.harold.creative.library.message.core;

import net.kyori.adventure.audience.Audience;
import sh.harold.creative.library.message.MessageBlock;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class DefaultMessageBlock implements MessageBlock, CompiledMessageBlock {

    private final List<CompiledBlockEntry> compiledEntries;
    private final List<Entry> entries;

    public DefaultMessageBlock(List<CompiledBlockEntry> compiledEntries) {
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
    }

    @Override
    public List<Entry> entries() {
        return entries;
    }

    @Override
    public void send(Audience audience) {
        Objects.requireNonNull(audience, "audience");
        audience.sendMessage(DefaultMessageRenderer.INSTANCE.renderBlock(this, RenderTarget.CHAT));
    }

    @Override
    public List<CompiledBlockEntry> compiledEntries() {
        return compiledEntries;
    }
}
