package net.ptcrys.topo.api.machine.multiblock.tool;

import net.ptcrys.topo.api.api.lang.TopoApiLang;
import net.ptcrys.topo.api.machine.MachineBlockEntity;
import net.ptcrys.topo.api.machine.data.DataKey;
import net.ptcrys.topo.api.machine.data.DataTransaction;
import net.ptcrys.topo.api.machine.multiblock.pattern.*;
import net.ptcrys.topo.helper.IdHelper;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentSyncHandler;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.registries.RegisterEvent;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

import java.util.*;
import java.util.function.Function;

/**
 * 工具配置注册表与玩家配置。扩展 mod 在公共初始化阶段注册命名空间选项；菜单按注册内容生成，
 * 不认识任何扩展 mod 的具体字段。配置保存到玩家数据，所有等级和物品的工具共享同一份。
 */
public final class StructureToolOptions {

    private static final Map<Identifier, Option> OPTIONS = new LinkedHashMap<>();
    public static final Identifier MODULE = IdHelper.topo("module");
    public static final Identifier REPEATS = IdHelper.topo("repeats");
    public static final Identifier MIRROR = IdHelper.topo("mirror");
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

    /** 单个玩家的配置；所有等级和物品的结构工具共享，保存到玩家数据并只同步给所属玩家。 */
    public static final class PlayerConfig {

        private final Map<String, String> values;

        PlayerConfig() {
            this(Map.of());
        }

        private PlayerConfig(Map<String, String> values) {
            this.values = Map.copyOf(values);
        }

        private String get(String id, String fallback) {
            return values.getOrDefault(id, fallback);
        }

        private PlayerConfig with(String id, String value) {
            Map<String, String> next = new HashMap<>(values);
            next.put(id, value);
            return new PlayerConfig(next);
        }

        private Map<String, String> values() {
            return values;
        }
    }

    private static final MapCodec<PlayerConfig> CONFIG_CODEC = Codec.unboundedMap(Codec.STRING, Codec.STRING)
            .xmap(PlayerConfig::new, PlayerConfig::values)
            .fieldOf("values");

    /** 玩家配置附件；保存到玩家数据，跨死亡/维度保留，并只同步给所属玩家。 */
    public static final AttachmentType<PlayerConfig> CONFIG = AttachmentType
            .builder(() -> new PlayerConfig())
            .serialize(CONFIG_CODEC)
            .copyOnDeath()
            .sync(new AttachmentSyncHandler<>() {

                @Override
                public boolean sendToPlayer(IAttachmentHolder holder, ServerPlayer to) {
                    return holder instanceof Player player && player.getUUID().equals(to.getUUID());
                }

                @Override
                public void write(RegistryFriendlyByteBuf buffer, PlayerConfig config, boolean initialSync) {
                    buffer.writeVarInt(config.values.size());
                    config.values.forEach((id, value) -> {
                        buffer.writeUtf(id);
                        buffer.writeUtf(value, MAX_VALUE_LENGTH);
                    });
                }

                @Override
                public PlayerConfig read(IAttachmentHolder holder, RegistryFriendlyByteBuf buffer, PlayerConfig previous) {
                    int size = buffer.readVarInt();
                    Map<String, String> values = new HashMap<>(size);
                    for (int i = 0; i < size; i++) values.put(buffer.readUtf(), buffer.readUtf(MAX_VALUE_LENGTH));
                    return new PlayerConfig(values);
                }
            })
            .build();

    /** 注册玩家配置附件；在 mod 构造阶段调用一次。 */
    public static void register(IEventBus modBus) {
        modBus.addListener(RegisterEvent.class, event -> event.register(
                NeoForgeRegistries.Keys.ATTACHMENT_TYPES, IdHelper.topo("structure_tool_config"), () -> CONFIG));
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

        /** 调整本次施工方案参数（重复数、整体朝向、候选状态）；不参与检测要求。 */
        default void configure(Context context, String value, Settings settings) {}

        /**
         * 将配置值作为本次结构检测的要求写入元数据工作区。要求不会记为已探测事实；
         * 只有蓝图 CellBinding 声明过的键可用，未声明时应跳过。
         */
        default void require(Context context, String value, DataTransaction metadata) {}
    }

    /** 配置应用时的完整上下文，模块索引已经解析，blueprint 为本次选择的本体或模块。 */
    public record Context(ServerPlayer player, MachineBlockEntity controller, Blueprint blueprint, int moduleIndex) {}

    /** 单次方案的可变参数，完成配置后由规划器校验。扩展可调整重复数、朝向或提供额外候选状态。 */
    public static final class Settings {

        private RepeatResolution repeats;
        private Orientation orientation;
        private final List<Function<Cell, List<BlockState>>> candidates = new ArrayList<>();

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
        public void candidates(Function<Cell, List<BlockState>> provider) {
            candidates.add(Objects.requireNonNull(provider));
        }

        public List<BlockState> extraCandidates(Cell cell) {
            List<BlockState> result = new ArrayList<>();
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

    public static String get(Player player, Option option) {
        String stored = player.getData(CONFIG).get(option.id().toString(), option.defaultValue());
        try {
            return validate(option, stored);
        } catch (IllegalArgumentException exception) {
            return option.defaultValue();
        }
    }

    public static void set(Player player, Option option, String value) {
        if (!(player instanceof ServerPlayer)) throw new IllegalStateException("Structure tool configuration is server-authoritative");
        if (!all().contains(option)) throw new IllegalArgumentException("Unregistered tool option");
        String normalized = validate(option, value);
        player.setData(CONFIG, player.getData(CONFIG).with(option.id().toString(), normalized));
    }

    public static int module(Player player) {
        return Integer.parseInt(get(player, all().stream().filter(option -> option.id().equals(MODULE)).findFirst().orElseThrow()));
    }

    public static Settings configure(Context context, Player player, Orientation orientation) {
        Settings result = new Settings(context.blueprint(), orientation);
        for (Option option : all()) option.configure(context, get(player, option), result);
        context.blueprint().layout(result.repeats());
        return result;
    }

    /** 将玩家配置的全部选项写入本次结构检测元数据；要求与已探测事实分开保存。 */
    public static void configureMetadata(Context context, Player player, DataTransaction metadata) {
        for (Option option : all()) option.require(context, get(player, option), metadata);
    }

    /** 仅在蓝图声明了该键时写入检测要求；供外部选项复用，未声明的键直接忽略。 */
    public static <T> void require(DataTransaction metadata, DataKey<T> key, T value) {
        if (metadata.declares(key)) metadata.require(key, value);
    }
}
