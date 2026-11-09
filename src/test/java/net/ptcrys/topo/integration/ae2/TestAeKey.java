package net.ptcrys.topo.integration.ae2;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueOutput;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;

import java.util.List;

/** 只用于资源身份比较，不依赖游戏注册表或物品组件。 */
public final class TestAeKey extends AEKey {

    private final Identifier id;

    public TestAeKey(String path) {
        id = Identifier.fromNamespaceAndPath("topo_test", path);
    }

    @Override
    public AEKeyType getType() {
        throw new UnsupportedOperationException();
    }

    @Override
    public AEKey dropSecondary() {
        return this;
    }

    @Override
    public void toTag(ValueOutput output) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object getPrimaryKey() {
        return id;
    }

    @Override
    public Identifier getId() {
        return id;
    }

    @Override
    public void writeToPacket(RegistryFriendlyByteBuf data) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected Component computeDisplayName() {
        return Component.literal(id.toString());
    }

    @Override
    public void addDrops(long amount, List<ItemStack> drops, Level level, BlockPos pos) {}

    @Override
    public boolean hasComponents() {
        return false;
    }
}
