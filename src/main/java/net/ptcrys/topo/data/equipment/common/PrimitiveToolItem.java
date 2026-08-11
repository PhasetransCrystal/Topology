package net.ptcrys.topo.data.equipment.common;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;

import org.jspecify.annotations.Nullable;

/**
 * 损耗式手工工具（GregTech 手感）：作为合成材料下到工作台参与合成，每次合成消耗 1 点耐久后返还，
 * 耐久耗尽则在合成中损毁消失。
 *
 * <p>
 * 语义实现于"合成余料（crafting remainder）"钩子。NeoForge 26.1 把按栈敏感的余料查询挪到了
 * {@link net.neoforged.neoforge.common.extensions.IItemExtension#getCraftingRemainder(ItemInstance)}
 * （返回不可变的 {@link ItemStackTemplate}）；原版配方装配处对每个输入槽调用
 * {@code stack.getCraftingRemainder()} 并以 {@code remainder != null ? remainder.create() : ItemStack.EMPTY}
 * 写回结果格。因此本类重写该按栈钩子：
 * <ul>
 * <li>把输入栈的损坏值 +1；</li>
 * <li>未达上限：返回一个损坏值 +1 的工具模板（{@link ItemStackTemplate#fromNonEmptyStack(ItemStack)}
 * 会保留 DAMAGE 等数据组件），工具留在网格里继续可用；</li>
 * <li>达到/超过 {@link ItemStack#getMaxDamage()}：返回 {@code null}，由配方装配处写成 EMPTY，工具损毁。</li>
 * </ul>
 *
 * <p>
 * 耐久通过 {@link Item.Properties#durability(int)} 在注册期声明（DataComponents 的 MAX_DAMAGE / DAMAGE）。
 * Opt-in via {@link ToolEquipment.Builder#craftingRemainderTool()}.
 */
public class PrimitiveToolItem extends Item {

    public PrimitiveToolItem(Properties properties) {
        super(properties);
    }

    /**
     * 合成余料：返回扣 1 耐久后的工具副本；耐久耗尽返回 {@code null}（损毁）。
     *
     * @param instance 参与合成的输入栈（装配期传入的即工作台格中的 {@link ItemStack}）
     * @return 损坏值 +1 的工具模板，或 {@code null} 表示工具在本次合成中损毁
     */
    @Override
    public @Nullable ItemStackTemplate getCraftingRemainder(ItemInstance instance) {
        if (!(instance instanceof ItemStack stack) || stack.isEmpty()) {
            return super.getCraftingRemainder(instance);
        }
        if (!stack.isDamageableItem()) {
            return null;
        }
        int nextDamage = stack.getDamageValue() + 1;
        if (nextDamage >= stack.getMaxDamage()) {
            return null;
        }
        ItemStack remainder = stack.copyWithCount(1);
        remainder.setDamageValue(nextDamage);
        return ItemStackTemplate.fromNonEmptyStack(remainder);
    }
}
