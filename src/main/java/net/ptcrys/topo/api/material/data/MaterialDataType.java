package net.ptcrys.topo.api.material.data;

import net.ptcrys.topo.api.material.Material;

import net.minecraft.resources.Identifier;

import org.jetbrains.annotations.ApiStatus;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Identity of one material property. A material has the capability exactly when it declares a
 * value of this type. Properties may require other properties (checked at injection time) and
 * exclude other properties (checked once at freeze time).
 */
public abstract class MaterialDataType<D> {

    private final Identifier id;
    private final Set<MaterialDataType<?>> dependencies = new LinkedHashSet<>();
    private final Set<MaterialDataType<?>> exclusions = new LinkedHashSet<>();

    protected MaterialDataType(Identifier id) {
        this.id = id;
    }

    public final Identifier id() {
        return id;
    }

    protected final MaterialDataUse<D> use(D data) {
        return MaterialDataRegistry.use(this, data);
    }

    /** Properties that must already be declared when this property is injected. */
    public final MaterialDataType<D> requires(MaterialDataType<?>... types) {
        requireRelationsOpen();
        for (MaterialDataType<?> type : types) {
            Objects.requireNonNull(type, "required data type");
            if (type == this) {
                throw new IllegalArgumentException("material data type " + id + " cannot require itself");
            }
            dependencies.add(type);
        }
        return this;
    }

    public final Set<MaterialDataType<?>> dependencies() {
        return Set.copyOf(dependencies);
    }

    /** Properties that must not coexist with this property; checked when materials freeze. */
    public final MaterialDataType<D> excludes(MaterialDataType<?>... types) {
        requireRelationsOpen();
        for (MaterialDataType<?> type : types) {
            Objects.requireNonNull(type, "excluded data type");
            if (type == this) {
                throw new IllegalArgumentException("material data type " + id + " cannot exclude itself");
            }
            exclusions.add(type);
        }
        return this;
    }

    private void requireRelationsOpen() {
        if (MaterialDataRegistry.isFrozen()) {
            throw new IllegalStateException(
                    "material data type " + id + ": dependency/exclusion relations are frozen");
        }
    }

    @ApiStatus.Internal
    public final void checkDependencies(Material material) {
        checkDependencies(material.id(), material::hasData);
    }

    /** Scope-aware variant used for form-local data, where dependencies may live in the same form. */
    @ApiStatus.Internal
    public final void checkDependencies(Identifier materialId, Predicate<MaterialDataType<?>> present) {
        for (MaterialDataType<?> dependency : dependencies) {
            if (!present.test(dependency)) {
                throw new IllegalStateException(
                        "material " + materialId + " declares " + id + " but is missing required property " + dependency.id());
            }
        }
    }

    @ApiStatus.Internal
    public final void checkExclusions(Material material) {
        checkExclusions(material.id(), material::hasData);
    }

    /** Scope-aware variant used for form-local data; exclusions are checked after data freezes. */
    @ApiStatus.Internal
    public final void checkExclusions(Identifier materialId, Predicate<MaterialDataType<?>> present) {
        for (MaterialDataType<?> exclusion : exclusions) {
            if (present.test(exclusion)) {
                throw new IllegalStateException(
                        "material " + materialId + " cannot declare both " + id + " and " + exclusion.id());
            }
        }
    }

    /** Optional material-level invariant; runs once after the declaration window closes. */
    public void validate(Material material, D data) {}

    /** True when the material declares any value for this capability. */
    public final Predicate<Material> present() {
        return material -> material.hasData(this);
    }

    /** True when the material declares exactly {@code expected}. */
    public final Predicate<Material> is(D expected) {
        Objects.requireNonNull(expected, "expected");
        return material -> material.data(this).filter(expected::equals).isPresent();
    }

    /** True when the material declares a value accepted by {@code predicate}. */
    public final Predicate<Material> matches(Predicate<? super D> predicate) {
        Objects.requireNonNull(predicate, "predicate");
        return material -> material.data(this).filter(predicate).isPresent();
    }
}
