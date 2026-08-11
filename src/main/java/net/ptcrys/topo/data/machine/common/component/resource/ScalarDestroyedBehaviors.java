package net.ptcrys.topo.data.machine.common.component.resource;

import net.ptcrys.topo.data.machine.BuiltinTopoMachineFeedbackLang;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 内建标量"被破坏"行为:电容放电模型(能量/高级能量共用,只差能量密度)与破壳泄热模型(热量)。
 * 全部阈值与曲线常数集中于此;曲线函数为只收原始数值的纯静态函数,供 JVM 单测直接验证。
 * 设计稿:{@code /specs/2026-06-11-machine-destroyed-resource-behavior-design.md}。
 */
public final class ScalarDestroyedBehaviors {

    /** 电弧档下界(含):低于此有效能量的残余电荷安全耗散。 */
    public static final long ARC_MIN_EFFECTIVE = 10_000L;
    /** 爆燃档下界(含):达到此有效能量的电容堆短路即爆燃。 */
    public static final long BLAST_MIN_EFFECTIVE = 1_000_000L;
    /** 爆燃强度封顶(原版 TNT 为 4,苦力怕为 3);防止后期巨型储能变成基地清除器。 */
    public static final float BLAST_STRENGTH_CAP = 6.0F;
    /** 热泄放激活门槛(含):低于此存量只是温热外壳。 */
    public static final long HEAT_MIN_STORED = 10_000L;
    /** 热曲线满档存量:达到此存量即满强度热闪。 */
    public static final long HEAT_FULL_STORED = 50_000L;

    private static final float ARC_DAMAGE_MIN = 2.0F;
    private static final float ARC_DAMAGE_MAX = 8.0F;
    private static final float ARC_RADIUS_MIN = 2.0F;
    private static final float ARC_RADIUS_MAX = 4.0F;
    private static final float BLAST_STRENGTH_BASE = 2.0F;
    private static final float HEAT_RADIUS_MIN = 2.0F;
    private static final float HEAT_RADIUS_MAX = 3.0F;
    private static final float HEAT_IGNITE_SECONDS_MIN = 3.0F;
    private static final float HEAT_IGNITE_SECONDS_MAX = 8.0F;
    private static final float HEAT_FIRE_DAMAGE_MIN = 2.0F;
    private static final float HEAT_FIRE_DAMAGE_MAX = 4.0F;
    private static final int HEAT_IGNITE_BUDGET_MIN = 4;
    private static final int HEAT_IGNITE_BUDGET_MAX = 10;

    private ScalarDestroyedBehaviors() {}

    /**
     * 电容放电:存量按 {@code energyDensity} 折算成有效能量后分三档——耗散(仅火花)、电弧
     * (半径内生物受雷击伤害)、爆燃(真实破坏方块的爆炸,强度随 log10 增长并封顶)。
     * 能量密度即该标量与基准能量的换算比(能量 = 1,高级能量 = 10,对应 10:1 转换率)。
     */
    public static ScalarDestroyedBehavior energyDischarge(int energyDensity) {
        if (energyDensity <= 0) {
            throw new IllegalArgumentException("Energy density must be positive: " + energyDensity);
        }
        return (level, pos, stored, capacity) -> {
            long effective = effectiveEnergy(stored, energyDensity);
            switch (energyTier(effective)) {
                case 1 -> arc(level, pos, effective);
                case 2 -> blast(level, pos, effective);
                default -> dissipate(level, pos, effective);
            }
            return energyReport(effective, stored);
        };
    }

    /**
     * 破壳泄热:存量过门槛后,半径内生物点燃并受火焰伤害,同时按确定性扫描顺序在周围
     * (含上方,热向上)可燃位置放置至多预算数量的火焰。不破坏方块、不产生爆炸。
     */
    public static ScalarDestroyedBehavior heatFlash() {
        return (level, pos, stored, capacity) -> {
            if (!heatActive(stored)) {
                return MachineDestroyedReport.SILENT;
            }
            Vec3 center = Vec3.atCenterOf(pos);
            float radius = heatRadius(stored);
            float igniteSeconds = heatIgniteSeconds(stored);
            float damage = heatFireDamage(stored);
            for (LivingEntity entity : livingWithin(level, center, radius)) {
                entity.igniteForSeconds(igniteSeconds);
                entity.hurtServer(level, level.damageSources().onFire(), damage);
            }
            igniteGround(level, pos, Mth.floor(radius), heatIgniteBudget(stored));
            level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.8F, 0.7F);
            level.sendParticles(ParticleTypes.FLAME, center.x, center.y, center.z, 30, 0.5, 0.5, 0.5, 0.05);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, center.x, center.y, center.z, 20, 0.5, 0.5, 0.5, 0.02);
            return heatReport(stored);
        };
    }

    /**
     * 电容放电的破坏报告(纯映射,不触世界):空机静默、微量耗散无害、电弧/爆燃有害;数值列出实际
     * 储量(千分位),电弧另附四舍五入雷击伤害。{@code effective} 为已折算密度的有效能量,{@code stored}
     * 为机器仪表实际储量。
     */
    static MachineDestroyedReport energyReport(long effective, long stored) {
        return switch (energyTier(effective)) {
            // 爆燃=红(爆炸)、电弧=黄(电)、微量耗散=灰(无害)。
            case 2 -> MachineDestroyedReport.harmful(
                    BuiltinTopoMachineFeedbackLang.MESSAGE_MACHINE_DESTROYED_ENERGY_BLAST,
                    ChatFormatting.RED,
                    amount(stored));
            case 1 -> MachineDestroyedReport.harmful(
                    BuiltinTopoMachineFeedbackLang.MESSAGE_MACHINE_DESTROYED_ENERGY_ARC,
                    ChatFormatting.YELLOW,
                    amount(stored),
                    rounded(arcDamage(effective)));
            default -> effective > 0L ? MachineDestroyedReport.benign(
                    BuiltinTopoMachineFeedbackLang.MESSAGE_MACHINE_DESTROYED_ENERGY_DISSIPATE,
                    ChatFormatting.GRAY,
                    amount(stored)) : MachineDestroyedReport.SILENT;
        };
    }

    /**
     * 破壳泄热的破坏报告(纯映射,不触世界):余温静默、达门槛即热闪有害(金色,火);数值列出实际
     * 储量(千分位)与四舍五入点燃秒数。
     */
    static MachineDestroyedReport heatReport(long stored) {
        return heatActive(stored) ? MachineDestroyedReport.harmful(
                BuiltinTopoMachineFeedbackLang.MESSAGE_MACHINE_DESTROYED_HEAT_FLASH,
                ChatFormatting.GOLD,
                amount(stored),
                rounded(heatIgniteSeconds(stored))) : MachineDestroyedReport.SILENT;
    }

    /** 储量千分位文案(固定 ROOT locale,与玩家语言无关,确保确定性)。 */
    private static String amount(long value) {
        return String.format(java.util.Locale.ROOT, "%,d", value);
    }

    /** 曲线量(伤害/秒数)四舍五入成整串,供文案直读。 */
    private static String rounded(float value) {
        return Integer.toString(Math.round(value));
    }

    /** 有效能量 = 存量 × 密度,饱和乘法(溢出钳到 {@link Long#MAX_VALUE},不回绕为负)。 */
    static long effectiveEnergy(long stored, int density) {
        if (stored <= 0L) {
            return 0L;
        }
        long effective = stored * density;
        if (effective / density != stored) {
            return Long.MAX_VALUE;
        }
        return effective;
    }

    /** 档位:0 = 耗散,1 = 电弧,2 = 爆燃。 */
    static int energyTier(long effective) {
        if (effective >= BLAST_MIN_EFFECTIVE) {
            return 2;
        }
        return effective >= ARC_MIN_EFFECTIVE ? 1 : 0;
    }

    /** 电弧档内插值:log10 缩放,10^4 → 0,10^6 → 1,两端 clamp。 */
    private static float arcT(long effective) {
        if (effective <= ARC_MIN_EFFECTIVE) {
            return 0.0F;
        }
        double t = Math.log10((double) effective / ARC_MIN_EFFECTIVE) / 2.0;
        return Mth.clamp((float) t, 0.0F, 1.0F);
    }

    static float arcDamage(long effective) {
        return Mth.lerp(arcT(effective), ARC_DAMAGE_MIN, ARC_DAMAGE_MAX);
    }

    static float arcRadius(long effective) {
        return Mth.lerp(arcT(effective), ARC_RADIUS_MIN, ARC_RADIUS_MAX);
    }

    /** 爆燃强度 = min(2 + log10(effective / 10^6), 6):1M → 2,100M → 4(TNT),10G 起封顶 6。 */
    static float blastStrength(long effective) {
        if (effective <= BLAST_MIN_EFFECTIVE) {
            return BLAST_STRENGTH_BASE;
        }
        double strength = BLAST_STRENGTH_BASE + Math.log10((double) effective / BLAST_MIN_EFFECTIVE);
        return (float) Math.min(strength, BLAST_STRENGTH_CAP);
    }

    /** 热泄放是否激活(门槛含端点)。 */
    static boolean heatActive(long stored) {
        return stored >= HEAT_MIN_STORED;
    }

    /** 热曲线插值:[10k, 50k] 线性,两端 clamp。 */
    static float heatT(long stored) {
        return Mth.clamp((stored - HEAT_MIN_STORED) / (float) (HEAT_FULL_STORED - HEAT_MIN_STORED), 0.0F, 1.0F);
    }

    static float heatRadius(long stored) {
        return Mth.lerp(heatT(stored), HEAT_RADIUS_MIN, HEAT_RADIUS_MAX);
    }

    static float heatIgniteSeconds(long stored) {
        return Mth.lerp(heatT(stored), HEAT_IGNITE_SECONDS_MIN, HEAT_IGNITE_SECONDS_MAX);
    }

    static float heatFireDamage(long stored) {
        return Mth.lerp(heatT(stored), HEAT_FIRE_DAMAGE_MIN, HEAT_FIRE_DAMAGE_MAX);
    }

    /** 点燃预算 4..10,随 t 线性增长,向下取整。 */
    static int heatIgniteBudget(long stored) {
        return HEAT_IGNITE_BUDGET_MIN + (int) (heatT(stored) * (HEAT_IGNITE_BUDGET_MAX - HEAT_IGNITE_BUDGET_MIN));
    }

    /** 耗散档:无伤害、无方块影响;残余电荷以火花粒子与轻微放电声放空,空仓则完全无声。 */
    private static void dissipate(ServerLevel level, BlockPos pos, long effective) {
        if (effective <= 0L) {
            return;
        }
        Vec3 center = Vec3.atCenterOf(pos);
        level.playSound(null, pos, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.BLOCKS, 0.2F, 1.8F);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, center.x, center.y, center.z, 12, 0.3, 0.3, 0.3, 0.1);
    }

    /** 电弧档:半径内生物受雷击伤害;电火花四溅。 */
    private static void arc(ServerLevel level, BlockPos pos, long effective) {
        Vec3 center = Vec3.atCenterOf(pos);
        float radius = arcRadius(effective);
        float damage = arcDamage(effective);
        for (LivingEntity entity : livingWithin(level, center, radius)) {
            entity.hurtServer(level, level.damageSources().lightningBolt(), damage);
        }
        level.playSound(null, pos, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.BLOCKS, 0.6F, 1.4F);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, center.x, center.y, center.z, 40, 0.6, 0.6, 0.6, 0.15);
    }

    /** 爆燃档:电气爆炸不点燃地面;伤害/击退/掉落全部继承原版爆炸语义。 */
    private static void blast(ServerLevel level, BlockPos pos, long effective) {
        Vec3 center = Vec3.atCenterOf(pos);
        level.explode(
                null,
                center.x,
                center.y,
                center.z,
                blastStrength(effective),
                false,
                Level.ExplosionInteraction.BLOCK);
    }

    private static java.util.List<LivingEntity> livingWithin(ServerLevel level, Vec3 center, float radius) {
        AABB box = AABB.ofSize(center, radius * 2.0, radius * 2.0, radius * 2.0);
        return level.getEntitiesOfClass(
                LivingEntity.class,
                box,
                entity -> entity.position().distanceToSqr(center) <= (double) radius * radius);
    }

    /**
     * 确定性点燃:按 {@code BlockPos.betweenClosed} 的固定遍历顺序扫描周围(纵向只向下一格、
     * 向上整个半径——热向上),在可放火的空气位放置原版火焰,至多 {@code budget} 处。跳过机器
     * 原位,避免点燃机器自身掉落物。
     */
    private static void igniteGround(ServerLevel level, BlockPos origin, int radius, int budget) {
        int placed = 0;
        for (BlockPos candidate : BlockPos.betweenClosed(
                origin.offset(-radius, -1, -radius),
                origin.offset(radius, radius, radius))) {
            if (placed >= budget) {
                return;
            }
            if (candidate.equals(origin) || !level.getBlockState(candidate).isAir() || !BaseFireBlock.canBePlacedAt(level, candidate, Direction.NORTH)) {
                continue;
            }
            level.setBlockAndUpdate(candidate.immutable(), BaseFireBlock.getState(level, candidate));
            placed++;
        }
    }
}
