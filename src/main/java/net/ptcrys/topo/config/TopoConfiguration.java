package net.ptcrys.topo.config;

import net.ptcrys.topo.Topology;

import dev.toma.configuration.config.*;

@Config(id = Topology.MODID)
public final class TopoConfiguration {

    @Configurable
    public Machines MACHINES = new Machines();

    @Configurable
    public Multiblock MULTIBLOCK = new Multiblock();

    @Configurable
    public Pipes PIPES = new Pipes();

    @Configurable
    public Recipe RECIPE = new Recipe();

    @Configurable
    public Ore ORE = new Ore();

    @Configurable
    public Async ASYNC = new Async();

    public static class Machines {

        @Configurable
        @Configurable.Range(min = 1, max = 200)
        @Configurable.Comment(value = {
                "空闲配方机器的检查间隔上限（tick）。",
                "机器空闲时检查间隔会逐次翻倍到此上限；资源变化或状态切换会立即恢复每 tick 检查。",
                "默认：20 ticks"
        }, localize = true)
        public int recipeLogicParkedBackoffMaxInterval = 20;

        @Configurable
        @Configurable.Range(min = 16_384, max = 1_048_576)
        @Configurable.Comment(value = {
                "单个玩家一次收到的机器同步数据上限（字节）。",
                "同步数据会合并到接近该上限；设置过大可能超过网络包限制。",
                "默认：262144 字节（256 KiB）"
        }, localize = true)
        public int dataSyncMaxBatchBytes = 262_144;

        @Configurable
        @Configurable.Range(min = 20, max = 2_400)
        @Configurable.Comment(value = {
                "机器存档失败后的重试间隔（tick）。",
                "用于重新标记未完成保存的区块，避免机器数据一直未写入存档。",
                "默认：200 ticks（10 秒）"
        }, localize = true)
        public int persistRecoveryIntervalTicks = 200;
    }

    public static class Multiblock {

        @Configurable
        @Configurable.Range(min = 20, max = 1200)
        @Configurable.Comment(value = {
                "已成型结构定期自检的间隔（tick，按位置错峰）。",
                "用于发现遗漏的结构变化；完整结构上的检查开销较低。",
                "间隔越短发现越快，但检查更频繁；间隔越长负载越低，但破损发现更慢。",
                "默认：150 ticks（7.5 秒）"
        }, localize = true)
        public int backstopIntervalTicks = 150;

        @Configurable
        @Configurable.Range(min = 64, max = 16384)
        @Configurable.Comment(value = {
                "结构体积达到该单元格数后改为后台线程检测。",
                "小型结构在主线程检测，延迟更低；大型结构异步检测可避免卡顿。",
                "默认：2048 个单元格"
        }, localize = true)
        public int asyncCellThreshold = 2048;

        @Configurable
        @Configurable.Comment(value = {
                "已成型结构被改动时，是否只重检变化的单元格。",
                "局部重检能更快发现小范围破坏；关闭后每次改动都会完整检测结构。",
                "默认：true"
        }, localize = true)
        public boolean enableTargetedRecheck = true;

        @Configurable
        @Configurable.Range(min = 1, max = 256)
        @Configurable.Comment(value = {
                "一次局部重检最多记录多少个变化单元格。",
                "超过该数量时退化为完整结构检测。",
                "默认：32 个单元格"
        }, localize = true)
        public int maxChangedCellsPerNote = 32;

        @Configurable
        @Configurable.UpdateRestriction(UpdateRestrictions.GAME_RESTART)
        @Configurable.Range(min = 1, max = 16)
        @Configurable.Comment(value = {
                "结构异步检测使用的线程数。",
                "线程池创建后读取；修改后需要重启服务器。",
                "默认：2 个线程"
        }, localize = true)
        public int structureExecutorThreads = 2;
    }

    public static class Pipes {

        @Configurable
        @Configurable.Range(min = 1, max = 64)
        @Configurable.Comment(value = {
                "单个抽取端口每批最多搜索多少条备用路线。",
                "数值越大，复杂网络中越容易找到额外路线，但路由计算开销也越高。",
                "默认：16 条"
        }, localize = true)
        public int maxAugmentSearchesPerBatch = 16;

        @Configurable
        @Configurable.Range(min = 1, max = 200)
        @Configurable.Comment(value = {
                "同一玩家的管道勘测界面最短刷新间隔（tick）。",
                "用于降低勘测界面的刷新频率。",
                "默认：10 ticks"
        }, localize = true)
        public int surveyMinSendIntervalTicks = 10;
    }

    public static class Recipe {

        @Configurable
        @Configurable.Range(min = 0, max = 4096)
        @Configurable.Comment(value = {
                "配方数量不超过该值时，直接线性搜索而不建立索引。",
                "高级性能参数：越小越早使用索引，越大越倾向线性搜索。",
                "默认：64 个配方"
        }, localize = true)
        public int tinyLinearSearchLimit = 64;

        @Configurable
        @Configurable.Range(min = 1, max = 64)
        @Configurable.Comment(value = {
                "精确候选超过已索引配方数的一定倍数时，改用线性搜索。",
                "只影响搜索速度，不影响配方匹配结果。",
                "默认：4 倍"
        }, localize = true)
        public int fallbackDominanceRatio = 4;
    }

    public static class Ore {

        @Configurable
        @Configurable.Range(min = 1_024, max = 4_194_304)
        @Configurable.Comment(value = {
                "每个区块生成矿脉时最多扫描多少方块。",
                "超过上限会中止该区块的矿脉规划并输出错误，避免世界生成卡住。",
                "默认：262144 个方块"
        }, localize = true)
        public int maxBlockScansPerChunk = 262_144;

        @Configurable
        @Configurable.Range(min = 64, max = 1_048_576)
        @Configurable.Comment(value = {
                "每个区块最多枚举多少个矿脉候选区域。",
                "超过上限会拒绝该矿脉定义并输出错误；通常不需要修改。",
                "默认：65536 个区域"
        }, localize = true)
        public int maxCellCandidatesPerChunk = 65_536;
    }

    public static class Async {

        @Configurable
        @Configurable.UpdateRestriction(UpdateRestrictions.GAME_RESTART)
        @Configurable.Range(min = 1, max = 8)
        @Configurable.Comment(value = {
                "框架 Tick 调度器的最大线程数。",
                "逻辑处理器不超过 3 个时固定使用 1 个线程。",
                "线程池创建后读取；修改后需要重启服务器。",
                "默认：2 个线程"
        }, localize = true)
        public int tickSchedulerMaxThreads = 2;
    }
}
