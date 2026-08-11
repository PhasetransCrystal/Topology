package net.ptcrys.topo.api.pipe;

import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.transfer.resource.Resource;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * Player-authored white/black lists of one extraction port. Both lists are active at the same
 * time: a resource moves when it matches the whitelist (or the whitelist is empty) AND does not
 * match the blacklist. Entries are normalized strings — {@code minecraft:coal} (exact registry
 * id) or {@code #c:ingots} (tag) — resolved into a fast predicate by the pipe's
 * {@link PipeFilterAdapter} only when the port config changes.
 *
 * <p>
 * Persisted beside the port's strategy config in the pipe saved data; the runtime's clamp
 * funnel enforces the definition's {@link PipeFilterSettings} (capacity, tag permission) on every
 * read and write, mirroring how strategy configs heal.
 */
public record PipePortFilter(List<String> whitelist, List<String> blacklist) {

    public static final PipePortFilter EMPTY = new PipePortFilter(List.of(), List.of());

    public static final Codec<PipePortFilter> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.listOf().optionalFieldOf("whitelist", List.of()).forGetter(PipePortFilter::whitelist),
            Codec.STRING.listOf().optionalFieldOf("blacklist", List.of()).forGetter(PipePortFilter::blacklist)).apply(instance, PipePortFilter::new));

    public PipePortFilter {
        whitelist = List.copyOf(whitelist);
        blacklist = List.copyOf(blacklist);
    }

    public boolean isEmpty() {
        return whitelist.isEmpty() && blacklist.isEmpty();
    }

    public List<String> list(boolean white) {
        return white ? whitelist : blacklist;
    }

    /** A copy with {@code entry} appended to one list; duplicates within the list are no-ops. */
    public PipePortFilter withAdded(boolean white, String entry) {
        List<String> current = list(white);
        if (current.contains(entry)) {
            return this;
        }
        List<String> grown = new ArrayList<>(current.size() + 1);
        grown.addAll(current);
        grown.add(entry);
        return white ? new PipePortFilter(grown, blacklist) : new PipePortFilter(whitelist, grown);
    }

    /** A copy with the first occurrence of {@code entry} removed from one list. */
    public PipePortFilter withRemoved(boolean white, String entry) {
        List<String> current = list(white);
        int index = current.indexOf(entry);
        if (index < 0) {
            return this;
        }
        List<String> shrunk = new ArrayList<>(current);
        shrunk.remove(index);
        return white ? new PipePortFilter(shrunk, blacklist) : new PipePortFilter(whitelist, shrunk);
    }

    /** True for {@code #}-prefixed (tag) entries. */
    public static boolean isTagEntry(String entry) {
        return !entry.isEmpty() && entry.charAt(0) == '#';
    }

    /**
     * Normalize raw player input into a storable entry: trim, lowercase, validate the id syntax
     * (after stripping a single leading {@code #}). Returns {@code null} for input that cannot
     * become a valid entry — the caller rejects the edit instead of storing garbage.
     */
    public static @Nullable String normalizeEntry(String raw) {
        String trimmed = raw.trim().toLowerCase(Locale.ROOT);
        boolean tag = isTagEntry(trimmed);
        String id = tag ? trimmed.substring(1) : trimmed;
        if (id.isEmpty() || Identifier.tryParse(id) == null) {
            return null;
        }
        return tag ? "#" + id : id;
    }

    /**
     * Compose the combined white-then-black predicate through the pipe's adapter; {@code null}
     * means "no filtering" (both lists empty) so the engine's hot path stays a single null check.
     */
    public <R extends Resource> @Nullable Predicate<R> compile(PipeFilterAdapter<R> adapter) {
        if (isEmpty()) {
            return null;
        }
        Predicate<R> white = whitelist.isEmpty() ? null : adapter.compile(whitelist);
        Predicate<R> black = blacklist.isEmpty() ? null : adapter.compile(blacklist);
        if (white == null) {
            // Blacklist-only: pass everything the blacklist does not match.
            Predicate<R> blackOnly = black;
            return resource -> !blackOnly.test(resource);
        }
        if (black == null) {
            return white;
        }
        return resource -> white.test(resource) && !black.test(resource);
    }
}
