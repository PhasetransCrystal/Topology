package net.ptcrys.topo.config;

import net.ptcrys.topo.api.OfficialTopoAPIPlugin;
import net.ptcrys.topo.api.api.builtin.LangDomainRegistration;

/**
 * Translation keys for {@link TopoConfiguration}. The comment text in the config annotations is
 * file-only Chinese text; the config GUI reads these keys instead. Labels use vanilla formatting
 * codes: yellow marks adjustable performance settings, red marks settings that can affect network
 * safety, world generation, or require a restart.
 */
public final class TopoConfigLang {

    private static final String YELLOW = "§e";
    private static final String RED = "§c";
    private static final LangDomainRegistration LANG = OfficialTopoAPIPlugin.INSTANCE.lang();

    static {
        LANG.absolute("config.screen.topo", "Topo Configuration", "Topo 配置");
        LANG.key("config", "option.MACHINES", "Machines", "机器");
        LANG.key("config", "option.MULTIBLOCK", "Multiblock", "多方块");
        LANG.key("config", "option.PIPES", "Pipes", "管道");
        LANG.key("config", "option.RECIPE", "Recipe", "配方");
        LANG.key("config", "option.ORE", "Ore", "矿脉");
        LANG.key("config", "option.ASYNC", "Async", "异步");

        option("recipeLogicParkedBackoffMaxInterval",
                YELLOW + "Idle machine check interval cap",
                YELLOW + "空闲配方机器检查间隔上限",
                new String[] {
                        "Maximum interval between checks when a recipe machine is idle, in ticks.",
                        "Idle intervals double until this cap; resource or state changes restore every-tick checks immediately.",
                        "Default: 20 ticks"
                },
                new String[] {
                        "空闲配方机器的检查间隔上限（tick）。",
                        "机器空闲时检查间隔会逐次翻倍到此上限；资源变化或状态切换会立即恢复每 tick 检查。",
                        "默认：20 ticks"
                });

        option("dataSyncMaxBatchBytes",
                RED + "Machine sync packet size",
                RED + "机器同步包大小",
                new String[] {
                        "Maximum machine sync data sent to one player at once, in bytes.",
                        "Larger values may exceed the network packet limit.",
                        "Default: 262144 bytes (256 KiB)"
                },
                new String[] {
                        "单个玩家一次收到的机器同步数据上限（字节）。",
                        "设置过大可能超过网络包限制。",
                        "默认：262144 字节（256 KiB）"
                });

        option("persistRecoveryIntervalTicks",
                YELLOW + "Machine save retry interval",
                YELLOW + "机器存档重试间隔",
                new String[] {
                        "Retry interval after a failed machine save, in ticks.",
                        "Re-marks chunks whose save did not complete so machine data is not left unsaved.",
                        "Default: 200 ticks (10s)"
                },
                new String[] {
                        "机器存档失败后的重试间隔（tick）。",
                        "用于重新标记未完成保存的区块，避免机器数据一直未写入存档。",
                        "默认：200 ticks（10 秒）"
                });

        option("backstopIntervalTicks",
                YELLOW + "Structure integrity check interval",
                YELLOW + "结构完整度自检间隔",
                new String[] {
                        "How often formed structures check themselves, in ticks (staggered by position).",
                        "This catches missed world changes. The check is cheap on an intact structure.",
                        "Shorter intervals detect damage sooner but run more often; longer intervals cost less.",
                        "Default: 150 ticks (7.5s)"
                },
                new String[] {
                        "已成型结构定期自检的间隔（tick，按位置错峰）。",
                        "用于发现遗漏的结构变化；完整结构上的检查开销较低。",
                        "间隔越短发现越快，但检查更频繁；间隔越长负载越低，但破损发现更慢。",
                        "默认：150 ticks（7.5 秒）"
                });

        option("asyncCellThreshold",
                YELLOW + "Large structure async threshold",
                YELLOW + "大型结构异步检测阈值",
                new String[] {
                        "Structures at or above this cell count are checked on a background thread.",
                        "Small structures stay on the server thread for lower latency; large structures avoid tick lag.",
                        "Default: 2048 cells"
                },
                new String[] {
                        "结构体积达到该单元格数后改为后台线程检测。",
                        "小型结构在主线程检测，延迟更低；大型结构异步检测可避免卡顿。",
                        "默认：2048 个单元格"
                });

        option("enableTargetedRecheck",
                YELLOW + "Targeted structure recheck",
                YELLOW + "局部结构重检",
                new String[] {
                        "When a formed structure changes, recheck only the changed cells.",
                        "Targeted checks find small breaks faster; disable to fully recheck the structure on every change.",
                        "Default: true"
                },
                new String[] {
                        "已成型结构被改动时，是否只重检变化的单元格。",
                        "局部重检能更快发现小范围破坏；关闭后每次改动都会完整检测结构。",
                        "默认：true"
                });

        option("maxChangedCellsPerNote",
                YELLOW + "Targeted recheck change limit",
                YELLOW + "局部重检变更上限",
                new String[] {
                        "Maximum changed cells recorded for one targeted recheck.",
                        "Above this count the check falls back to a full structure check.",
                        "Default: 32 cells"
                },
                new String[] {
                        "一次局部重检最多记录多少个变化单元格。",
                        "超过该数量时退化为完整结构检测。",
                        "默认：32 个单元格"
                });

        option("structureExecutorThreads",
                RED + "Structure check threads",
                RED + "结构检测线程数",
                new String[] {
                        "Worker threads used for async structure checks.",
                        "Read when the thread pool is created; restart the server to apply a change.",
                        "Default: 2 threads"
                },
                new String[] {
                        "结构异步检测使用的线程数。",
                        "线程池创建后读取；修改后需要重启服务器。",
                        "默认：2 个线程"
                });

        option("maxAugmentSearchesPerBatch",
                YELLOW + "Pipe route search limit",
                YELLOW + "管道路由搜索上限",
                new String[] {
                        "Maximum extra routes one extraction port may search for per batch.",
                        "Higher values find more paths in complex networks at a higher routing cost.",
                        "Default: 16 searches"
                },
                new String[] {
                        "单个抽取端口每批最多搜索多少条备用路线。",
                        "数值越大，复杂网络中越容易找到额外路线，但路由计算开销也越高。",
                        "默认：16 条"
                });

        option("surveyMinSendIntervalTicks",
                YELLOW + "Survey refresh interval",
                YELLOW + "勘测刷新间隔",
                new String[] {
                        "Minimum interval between survey overlay updates for one player, in ticks.",
                        "Lower values refresh faster but increase network and rendering work.",
                        "Default: 10 ticks"
                },
                new String[] {
                        "同一玩家的管道勘测界面最短刷新间隔（tick）。",
                        "数值越小刷新越快，但网络和渲染开销更高。",
                        "默认：10 ticks"
                });

        option("tinyLinearSearchLimit",
                YELLOW + "Small recipe table linear search threshold",
                YELLOW + "小型配方表线性搜索阈值",
                new String[] {
                        "Recipe tables at or below this size may be searched linearly instead of building an index.",
                        "Performance tuning only; it does not change which recipe matches.",
                        "Default: 64 recipes"
                },
                new String[] {
                        "配方数量不超过该值时，直接线性搜索而不建立索引。",
                        "只影响搜索速度，不影响配方匹配结果。",
                        "默认：64 个配方"
                });

        option("fallbackDominanceRatio",
                YELLOW + "Recipe search fallback ratio",
                YELLOW + "配方搜索回退比例",
                new String[] {
                        "Fall back to linear search when precise candidates exceed indexed recipes times this ratio.",
                        "Performance tuning only; it does not change which recipe matches.",
                        "Default: 4"
                },
                new String[] {
                        "精确候选超过已索引配方数的一定倍数时，改用线性搜索。",
                        "只影响搜索速度，不影响配方匹配结果。",
                        "默认：4 倍"
                });

        option("maxBlockScansPerChunk",
                RED + "Ore block scan limit",
                RED + "矿脉方块扫描上限",
                new String[] {
                        "Maximum blocks scanned per chunk while planning ore veins.",
                        "Exceeding it aborts that chunk's ore planning with an error instead of stalling world generation.",
                        "Default: 262144 blocks"
                },
                new String[] {
                        "每个区块生成矿脉时最多扫描多少方块。",
                        "超过上限会中止该区块的矿脉规划并输出错误，避免世界生成卡住。",
                        "默认：262144 个方块"
                });

        option("maxCellCandidatesPerChunk",
                RED + "Ore candidate cell limit",
                RED + "矿脉候选区域上限",
                new String[] {
                        "Maximum ore candidate cells enumerated per chunk.",
                        "Exceeding it rejects the vein definition with an error; normally no change is needed.",
                        "Default: 65536 cells"
                },
                new String[] {
                        "每个区块最多枚举多少个矿脉候选区域。",
                        "超过上限会拒绝该矿脉定义并输出错误；通常不需要修改。",
                        "默认：65536 个区域"
                });

        option("tickSchedulerMaxThreads",
                RED + "Tick scheduler threads",
                RED + "Tick 调度线程数",
                new String[] {
                        "Maximum threads used by the framework tick scheduler.",
                        "One thread is used automatically on machines with three or fewer logical processors.",
                        "Read when the thread pool is created; restart the server to apply a change.",
                        "Default: 2 threads"
                },
                new String[] {
                        "框架 Tick 调度器的最大线程数。",
                        "逻辑处理器不超过 3 个时固定使用 1 个线程。",
                        "线程池创建后读取；修改后需要重启服务器。",
                        "默认：2 个线程"
                });
    }

    private TopoConfigLang() {}

    /** Only activates class initialization; keys are registered in the static initializer. */
    public static void init() {}

    private static void option(String field, String en, String cn,
                               String[] enComments, String[] cnComments) {
        if (enComments.length != cnComments.length) {
            throw new IllegalStateException(
                    "Config comment translation count mismatch for " + field + ": " + enComments.length + " en vs " + cnComments.length + " cn");
        }
        LANG.key("config", "option." + field, en, cn);
        for (int i = 0; i < enComments.length; i++) {
            LANG.key("config", "option." + field + ".comment." + i, enComments[i], cnComments[i]);
        }
    }
}
