package net.ptcrys.topo.helper;

import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.resource.Resource;

import java.util.Objects;
import java.util.function.Predicate;

/**
 * Shared content predicates for resource ports. Filtering is an orthogonal port axis
 * (geometry × access × filter) — domain roles such as "die slot" compose these, they do not own them.
 */
public final class ResourceFilterHelper {

    private ResourceFilterHelper() {}

    public static Predicate<Resource> itemTag(TagKey<Item> tag) {
        return new ItemTagFilter(tag);
    }

    public static Predicate<Resource> fluidTag(TagKey<Fluid> tag) {
        return new FluidTagFilter(tag);
    }

    /** Accepts only the exact non-empty item resource identity. */
    public static Predicate<Resource> itemExact(ItemResource expected) {
        Objects.requireNonNull(expected, "expected item resource");
        return resource -> resource instanceof ItemResource item && !item.isEmpty() && item.equals(expected);
    }

    public static Predicate<Resource> any() {
        return resource -> true;
    }

    public record ItemTagFilter(TagKey<Item> tag) implements Predicate<Resource> {

        public ItemTagFilter {
            Objects.requireNonNull(tag, "item filter tag");
        }

        @Override
        public boolean test(Resource resource) {
            return resource instanceof ItemResource itemResource && !itemResource.isEmpty() && itemResource.typeHolder().is(tag);
        }
    }

    public record FluidTagFilter(TagKey<Fluid> tag) implements Predicate<Resource> {

        public FluidTagFilter {
            Objects.requireNonNull(tag, "fluid filter tag");
        }

        @Override
        public boolean test(Resource resource) {
            return resource instanceof FluidResource fluidResource && !fluidResource.isEmpty() && fluidResource.typeHolder().is(tag);
        }
    }
}
