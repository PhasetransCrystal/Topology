package net.ptcrys.topo.dev;

import net.ptcrys.topo.api.recipe.TopoRecipe;
import net.ptcrys.topo.api.recipe.TopoRecipeType;
import net.ptcrys.topo.api.recipe.TopoRecipeTypes;
import net.ptcrys.topo.api.recipe.content.TopoFluidIngredient;
import net.ptcrys.topo.api.recipe.content.TopoItemInput;
import net.ptcrys.topo.data.recipe.common.ScalarRecipeCapability;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.RecipeHolder;

import com.mojang.logging.LogUtils;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * On-demand recipe ambiguity scan behind {@code /topo-dev scanrecipe}; results go to the run log.
 *
 * <p>
 * Two recipes of one type conflict when a machine buffer satisfying one necessarily satisfies
 * the other, so which one runs depends on search order instead of player intent. The predicate is
 * conservative and monotone: every start input is reduced to an acceptance-key set ({@code Item}
 * for item inputs — tags expand through the server registries, which are bound in-world — and
 * resources for fluids); anything not statically provable counts as overlapping with everything.
 * Scalar start inputs are amount thresholds on shared buffers, never kind discriminators, so they
 * are skipped. Cold path only: the scan allocates transient buckets and discards them.
 */
public final class RecipeConflictScanner {

    private static final Logger LOGGER = LogUtils.getLogger();

    private RecipeConflictScanner() {}

    public record Conflict(Identifier first, Identifier second, String detail) {}

    public record TypeReport(Identifier typeId, int recipeCount, List<Conflict> conflicts) {}

    public record Report(List<TypeReport> types, int recipeCount) {

        public int conflictCount() {
            int total = 0;
            for (TypeReport type : types) {
                total += type.conflicts().size();
            }
            return total;
        }
    }

    public static Report scan(MinecraftServer server) {
        List<TypeReport> typeReports = new ArrayList<>();
        int recipeCount = 0;
        for (TopoRecipeType<?> type : TopoRecipeTypes.registered()) {
            var holders = server.getRecipeManager().recipeMap().byType(type.vanillaType());
            List<ScannedRecipe> bucket = new ArrayList<>(holders.size());
            for (RecipeHolder<? extends TopoRecipe> holder : holders) {
                bucket.add(new ScannedRecipe(
                        holder.id().identifier(),
                        describeInputs(holder.value(), server)));
            }
            bucket.sort(Comparator.comparing(recipe -> recipe.id().toString()));
            recipeCount += bucket.size();
            typeReports.add(new TypeReport(type.id(), bucket.size(), scanBucket(bucket)));
        }
        return new Report(List.copyOf(typeReports), recipeCount);
    }

    public static void log(Report report) {
        LOGGER.info("==== Topo recipe conflict scan ====");
        for (TypeReport type : report.types()) {
            if (type.conflicts().isEmpty()) {
                LOGGER.info("type {}: {} recipes, ok", type.typeId(), type.recipeCount());
                continue;
            }
            LOGGER.error("type {}: {} recipes, {} conflicts", type.typeId(), type.recipeCount(),
                    type.conflicts().size());
            for (Conflict conflict : type.conflicts()) {
                LOGGER.error("  [conflict] {} <-> {} ({})", conflict.first(), conflict.second(), conflict.detail());
            }
        }
        if (report.conflictCount() == 0) {
            LOGGER.info("==== scan ok: {} recipes, no conflicts ====", report.recipeCount());
        } else {
            LOGGER.error("==== scan FAILED: {} recipes, {} conflicts ====", report.recipeCount(),
                    report.conflictCount());
        }
    }

    // ---- predicate core (pure; unit-tested without a server) ---------------------------------

    /** Acceptance-key set for one start input; {@code null} keys = not statically provable. */
    record InputAccept(@Nullable Set<Object> keys) {

        static final InputAccept UNPROVABLE = new InputAccept(null);

        boolean unprovable() {
            return keys == null;
        }
    }

    record ScannedRecipe(Identifier id, List<InputAccept> inputs) {}

    static List<Conflict> scanBucket(List<ScannedRecipe> bucket) {
        List<Conflict> conflicts = new ArrayList<>();
        for (int first = 0; first < bucket.size(); first++) {
            for (int second = first + 1; second < bucket.size(); second++) {
                ScannedRecipe a = bucket.get(first);
                ScannedRecipe b = bucket.get(second);
                boolean aInB = shadows(a, b);
                boolean bInA = shadows(b, a);
                if (!aInB && !bInA) {
                    continue;
                }
                String detail = aInB && bInA ? "mutually ambiguous" : aInB ? "buffer satisfying " + b.id() + " also satisfies " + a.id() : "buffer satisfying " + a.id() + " also satisfies " + b.id();
                conflicts.add(new Conflict(a.id(), b.id(), detail + "; kinds: " + kindSummary(a)));
            }
        }
        return conflicts;
    }

    /** Whether every machine state matching {@code host} necessarily also matches {@code candidate}. */
    static boolean shadows(ScannedRecipe candidate, ScannedRecipe host) {
        for (InputAccept input : candidate.inputs()) {
            if (input.unprovable()) {
                continue; // 证明不了的输入按"可被覆盖"处理(保守)。
            }
            boolean covered = false;
            for (InputAccept hostInput : host.inputs()) {
                if (hostInput.unprovable() || intersects(input.keys(), hostInput.keys())) {
                    covered = true;
                    break;
                }
            }
            if (!covered) {
                return false;
            }
        }
        return true;
    }

    private static boolean intersects(Set<Object> first, Set<Object> second) {
        Set<Object> small = first.size() <= second.size() ? first : second;
        Set<Object> large = small == first ? second : first;
        for (Object key : small) {
            if (large.contains(key)) {
                return true;
            }
        }
        return false;
    }

    private static String kindSummary(ScannedRecipe recipe) {
        if (recipe.inputs().isEmpty()) {
            return "<no start inputs>";
        }
        StringBuilder summary = new StringBuilder();
        for (InputAccept input : recipe.inputs()) {
            if (!summary.isEmpty()) {
                summary.append(" + ");
            }
            if (input.unprovable()) {
                summary.append('?');
            } else {
                summary.append(keyName(input.keys().iterator().next()));
                if (input.keys().size() > 1) {
                    summary.append("(+").append(input.keys().size() - 1).append(')');
                }
            }
        }
        return summary.toString();
    }

    private static String keyName(Object key) {
        if (key instanceof Item item) {
            return BuiltInRegistries.ITEM.getKey(item).toString();
        }
        return String.valueOf(key);
    }

    // ---- machine-world extraction --------------------------------------------------------------

    private static List<InputAccept> describeInputs(TopoRecipe recipe, MinecraftServer server) {
        List<InputAccept> accepts = new ArrayList<>();
        for (TopoRecipe.InputEntry<?> entry : recipe.inputs()) {
            if (entry.capability() instanceof ScalarRecipeCapability) {
                continue; // 标量是阈值不是判别物种,跳过。
            }
            for (Object content : entry.contents()) {
                accepts.add(describeContent(content, server));
            }
        }
        return accepts;
    }

    private static InputAccept describeContent(Object content, MinecraftServer server) {
        // Fail closed: unknown content must not silently mean "overlaps everything".
        // Sealed TopoItemInput is compile-exhaustive.
        return switch (content) {
            case TopoItemInput item -> switch (item) {
                case TopoItemInput.Resource resource -> new InputAccept(Set.of(resource.template().item()));
                // Catalyst/die: presence discriminator; participates as a normal accept set.
                case TopoItemInput.Unconsumed unconsumed -> new InputAccept(Set.of(unconsumed.template().item()));
                case TopoItemInput.Tag tag -> tagAccept(tag.tag(), server);
                case TopoItemInput.AnyOf anyOf -> {
                    Set<Object> keys = new LinkedHashSet<>();
                    for (ItemStackTemplate template : anyOf.templates()) {
                        keys.add(template.item());
                    }
                    yield new InputAccept(Set.copyOf(keys));
                }
            };
            case TopoFluidIngredient fluid -> new InputAccept(Set.of(fluid.resource()));
            default -> throw new IllegalArgumentException(
                    "RecipeConflictScanner: unsupported recipe content type " + (content == null ? "null" : content.getClass().getName()) + "; add a describeContent branch (scalars must be filtered earlier)");
        };
    }

    private static InputAccept tagAccept(TagKey<Item> tag, MinecraftServer server) {
        Optional<HolderSet.Named<Item>> members = server.registryAccess().lookupOrThrow(Registries.ITEM).get(tag);
        if (members.isEmpty()) {
            return InputAccept.UNPROVABLE;
        }
        Set<Object> keys = new LinkedHashSet<>();
        for (Holder<Item> holder : members.get()) {
            keys.add(holder.value());
        }
        return new InputAccept(Set.copyOf(keys));
    }
}
