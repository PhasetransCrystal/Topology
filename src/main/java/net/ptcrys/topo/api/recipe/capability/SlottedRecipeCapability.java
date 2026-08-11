package net.ptcrys.topo.api.recipe.capability;

import net.ptcrys.topo.api.machine.resource.MachineResourceType;
import net.ptcrys.topo.api.machine.resource.ResourcePort;
import net.ptcrys.topo.api.machine.resource.ResourcePortMetadata;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.transfer.resource.Resource;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.mojang.serialization.Codec;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * 逐槽位形态的配方内容通道（物品/流体这类"每槽一份"的资源家族）。
 *
 * <p>
 * 类型即证明：SLOTTED 家族必须同时给出预览槽计数、JEI 预览槽与机器实况槽——配对由抽象方法
 * 在编译期强制成立，不存在"注册了通道却没有槽位 widget"的中间态。聚合形态（标量资源条）的
 * 家族直接继承 {@link RecipeCapability}，不参与槽位管线。
 */
public abstract class SlottedRecipeCapability<I, O, R extends Resource> extends RecipeCapability<I, O> {

    protected SlottedRecipeCapability(
                                      Identifier id,
                                      Class<I> inputType,
                                      Class<O> outputType,
                                      Codec<I> inputCodec,
                                      Codec<O> outputCodec,
                                      StreamCodec<RegistryFriendlyByteBuf, I> inputStreamCodec,
                                      StreamCodec<RegistryFriendlyByteBuf, O> outputStreamCodec) {
        super(id, inputType, outputType, inputCodec, outputCodec, inputStreamCodec, outputStreamCodec);
    }

    @Override
    public abstract MachineResourceType<R> resourceType();

    /**
     * 一个端口贡献的预览槽数。调用方（预览计划 planner）只会传入本家族资源类型的端口元数据，
     * 实现无须再做资源类型过滤。
     */
    public abstract int countPreviewSlots(ResourcePortMetadata port);

    public abstract @NonNull UIElement createPreviewInputSlotWidget(@Nullable I input);

    public abstract @NonNull UIElement createPreviewOutputSlotWidget(@Nullable O output);

    /**
     * 含每刻通道内容的预览槽。{@code perTick} 是纯展示提示（如 "/t" 后缀）；默认回落到
     * 开始内容槽实现。
     */
    public @NonNull UIElement createPreviewInputSlotWidget(@Nullable I input, boolean perTick) {
        return createPreviewInputSlotWidget(input);
    }

    /** 每刻提示版输出预览槽；见 {@link #createPreviewInputSlotWidget(Object, boolean)}。 */
    public @NonNull UIElement createPreviewOutputSlotWidget(@Nullable O output, boolean perTick) {
        return createPreviewOutputSlotWidget(output);
    }

    /** 机器实况槽位：绑定 {@code storage} 端口的第 {@code slot} 槽，供配方页与存储页共用。 */
    public abstract @NonNull UIElement createLiveSlotWidget(ResourcePort<?, R> storage, int slot);

    /**
     * 预览槽位指派：把一张配方里本家族的组合内容序（开始通道在前、每刻通道拼接其后）指派到
     * {@code slotCount} 个预览输入槽。返回数组长度恒为 {@code slotCount}，元素是组合内容索引，
     * {@code -1} = 空槽；槽位不足时溢出内容不指派（与既有"槽外内容不展示"语义一致）。
     *
     * <p>
     * 缺省恒等铺排——内容 i 进槽 i，只按计数、不读内容值。插头重排时必须与机器实况侧配对：
     * 实况槽序 = 存储端口声明序，预览槽序由本方法决定，两边对不上 JEI 就会教玩家摆错（模具案：
     * 机器把模具端口殿后声明，故物品插头把催化剂内容钉到尾部槽）。
     */
    public int[] previewInputSlotAssignment(int slotCount, List<I> startContents, List<I> tickContents) {
        int total = startContents.size() + tickContents.size();
        int[] assignment = new int[slotCount];
        for (int slot = 0; slot < slotCount; slot++) {
            assignment[slot] = slot < total ? slot : -1;
        }
        return assignment;
    }
}
