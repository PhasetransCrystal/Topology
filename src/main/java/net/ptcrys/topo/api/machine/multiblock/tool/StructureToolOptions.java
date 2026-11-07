package net.ptcrys.topo.api.machine.multiblock.tool;

import net.ptcrys.topo.api.api.lang.TopoApiLang;
import net.ptcrys.topo.api.machine.MachineBlockEntity;
import net.ptcrys.topo.api.machine.multiblock.pattern.*;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.*;
import java.util.function.Function;

/** 工具配置注册表。扩展 mod 在公共初始化阶段注册命名空间选项；菜单按注册内容生成，不认识任何扩展 mod 的具体字段。 */
public final class StructureToolOptions {

    private static final String ROOT = "topo:structure_tool";
    private static final Map<Identifier, Option> OPTIONS = new LinkedHashMap<>();
    public static final Identifier MODULE = Identifier.fromNamespaceAndPath("topo", "module");
    public static final Identifier REPEATS = Identifier.fromNamespaceAndPath("topo", "repeats");
    public static final Identifier MIRROR = Identifier.fromNamespaceAndPath("topo", "mirror");
    public static final int MAX_VALUE_LENGTH = 256;

    static {
        register(new Option() {

            public Identifier id() {
                return MODULE;
            }

            public Component label() {
                return TopoApiLang.TOOL_STRUCTURE_MODULE.getComponent();
            }

            public String defaultValue() {
                return "0";
            }

            public String normalize(String value) {
                int index = Integer.parseInt(value.trim());
                if (index < 0 || index > 65535) throw new IllegalArgumentException("Module index outside 0..65535");
                return Integer.toString(index);
            }
        });
        register(new Option() {

            public Identifier id() {
                return REPEATS;
            }

            public Component label() {
                return TopoApiLang.TOOL_STRUCTURE_REPEATS.getComponent();
            }

            public String defaultValue() {
                return "";
            }

            public String normalize(String value) {
                if (value.isBlank()) return "";
                StringJoiner result = new StringJoiner(",");
                for (String number : value.split(",", -1)) {
                    int count = Integer.parseInt(number.trim());
                    if (count < 0 || count > 1000000) throw new IllegalArgumentException("Invalid repeat count");
                    result.add(Integer.toString(count));
                }
                return result.toString();
            }

            public void configure(Context context, String value, Settings settings) {
                if (value.isBlank()) return;
                int[] counts = Arrays.stream(value.split(",")).mapToInt(Integer::parseInt).toArray();
                settings.repeats(new RepeatResolution(counts));
            }
        });
        register(new Option() {

            public Identifier id() {
                return MIRROR;
            }

            public Component label() {
                return TopoApiLang.TOOL_STRUCTURE_MIRROR.getComponent();
            }

            public String defaultValue() {
                return "false";
            }

            public List<String> choices() {
                return List.of("false", "true");
            }

            public Component displayValue(String value) {
                return (Boolean.parseBoolean(value) ? TopoApiLang.TOOL_STRUCTURE_ENABLED : TopoApiLang.TOOL_STRUCTURE_DISABLED).getComponent();
            }

            public String normalize(String value) {
                if (!choices().contains(value)) throw new IllegalArgumentException("Invalid mirror choice");
                return value;
            }

            public void configure(Context context, String value, Settings settings) {
                settings.mirror(Boolean.parseBoolean(value));
            }
        });
    }

    private StructureToolOptions() {}

    /** 一个配置字段。normalize 在服务端提交时调用，非法值应抛出 IllegalArgumentException；configure 只在服务端生成方案前执行。 */
    public interface Option {

        /** 配置持久化键，必须使用所属 mod 的命名空间；同一键只允许注册一次。 */
        Identifier id();

        Component label();

        String defaultValue();

        String normalize(String value);

        /** 非空时生成循环选择按钮，空列表生成文本输入框；值仍须通过 normalize 校验。 */
        default List<String> choices() {
            return List.of();
        }

        default Component displayValue(String value) {
            return Component.literal(value);
        }

        default void configure(Context context, String value, Settings settings) {}
    }

    /** 配置应用时的完整上下文，模块索引已经解析，blueprint 为本次选择的本体或模块。 */
    public record Context(ServerPlayer player, MachineBlockEntity controller, Blueprint blueprint, int moduleIndex) {}

    /** 单次方案的可变参数，完成配置后由规划器校验。扩展可调整重复数、朝向或提供额外候选状态。 */
    public static final class Settings {

        private RepeatResolution repeats;
        private Orientation orientation;
        private final List<Function<Cell, List<net.minecraft.world.level.block.state.BlockState>>> candidates = new ArrayList<>();

        public Settings(Blueprint blueprint, Orientation orientation) {
            this.repeats = blueprint.minRepeats();
            this.orientation = orientation;
        }

        public RepeatResolution repeats() {
            return repeats;
        }

        public void repeats(RepeatResolution value) {
            repeats = Objects.requireNonNull(value);
        }

        public Orientation orientation() {
            return orientation;
        }

        public void orientation(Orientation value) {
            orientation = Objects.requireNonNull(value);
        }

        /** 明确设置镜像状态，关闭时恢复普通布局；不会由已有世界的检测结果改写。 */
        public void mirror(boolean value) {
            if (orientation.mirror() != value) orientation = orientation.mirrored();
        }

        /** 追加世界朝向的候选状态；规划器仍逐项验证蓝图谓词和属性要求，不能绕过结构约束。 */
        public void candidates(Function<Cell, List<net.minecraft.world.level.block.state.BlockState>> provider) {
            candidates.add(Objects.requireNonNull(provider));
        }

        public List<net.minecraft.world.level.block.state.BlockState> extraCandidates(Cell cell) {
            List<net.minecraft.world.level.block.state.BlockState> result = new ArrayList<>();
            for (var provider : candidates) result.addAll(provider.apply(cell));
            return result;
        }
    }

    /** 在公共初始化阶段注册选项，客户端与服务端必须注册相同的字段；默认值也须通过校验。 */
    public static synchronized void register(Option option) {
        Objects.requireNonNull(option);
        if (OPTIONS.size() >= 128) throw new IllegalStateException("Too many structure tool options");
        Objects.requireNonNull(option.id());
        validate(option, option.defaultValue());
        if (OPTIONS.putIfAbsent(option.id(), option) != null) throw new IllegalArgumentException("Duplicate tool option " + option.id());
    }

    public static synchronized List<Option> all() {
        return List.copyOf(OPTIONS.values());
    }

    public static String validate(Option option, String value) {
        if (value.length() > MAX_VALUE_LENGTH) throw new IllegalArgumentException("Tool option too long");
        String normalized = Objects.requireNonNull(option.normalize(value));
        if (normalized.length() > MAX_VALUE_LENGTH) throw new IllegalArgumentException("Normalized tool option too long");
        return normalized;
    }

    public static String get(ItemStack stack, Option option) {
        CompoundTag root = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getCompoundOrEmpty(ROOT);
        String stored = root.getStringOr(option.id().toString(), option.defaultValue());
        try {
            return validate(option, stored);
        } catch (IllegalArgumentException exception) {
            return option.defaultValue();
        }
    }

    public static void set(ItemStack stack, Option option, String value) {
        if (!all().contains(option)) throw new IllegalArgumentException("Unregistered tool option");
        String normalized = validate(option, value);
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            CompoundTag root = tag.getCompoundOrEmpty(ROOT).copy();
            root.putString(option.id().toString(), normalized);
            tag.put(ROOT, root);
        });
    }

    public static int module(ItemStack stack) {
        return Integer.parseInt(get(stack, all().stream().filter(option -> option.id().equals(MODULE)).findFirst().orElseThrow()));
    }

    public static Settings configure(Context context, ItemStack stack, Orientation orientation) {
        Settings result = new Settings(context.blueprint(), orientation);
        for (Option option : all()) option.configure(context, get(stack, option), result);
        context.blueprint().layout(result.repeats());
        return result;
    }
}
