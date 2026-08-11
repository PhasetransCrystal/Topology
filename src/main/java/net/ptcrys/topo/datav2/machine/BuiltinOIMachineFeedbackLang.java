package net.ptcrys.topo.datav2.machine;

import net.ptcrys.topo.api.lang.LangKey;
import net.ptcrys.topo.apiv2.plugin.LangDomainRegistration;
import net.ptcrys.topo.datav2.OfficialOIPlugin;

/**
 * Chat lines for destroyed scalar machines. Placeholders: {@code %1$s} machine name, {@code %2$s}
 * resource name, then magnitude values.
 */
public final class BuiltinOIMachineFeedbackLang {

    private static final LangDomainRegistration LANG = OfficialOIPlugin.INSTANCE.lang();

    public static final LangKey MESSAGE_MACHINE_DESTROYED_ENERGY_DISSIPATE = LANG.key(
            "message",
            "machine_destroyed.energy.dissipate",
            "A %1$s's residual %2$s (%3$s) sparked away harmlessly as its casing broke.",
            "一台%1$s的残余%2$s（%3$s）随外壳破裂无害地迸散为火花。");

    public static final LangKey MESSAGE_MACHINE_DESTROYED_ENERGY_ARC = LANG.key(
            "message",
            "machine_destroyed.energy.arc",
            "A %1$s's stored %2$s (%3$s) discharged in an instant — " + "an arc lashed everything nearby for ~%4$s damage!",
            "一台%1$s储存的%2$s（%3$s）瞬间放电——" + "一道电弧鞭击了周围的一切，造成 ~%4$s 点伤害！");

    public static final LangKey MESSAGE_MACHINE_DESTROYED_ENERGY_BLAST = LANG.key(
            "message",
            "machine_destroyed.energy.blast",
            "A %1$s's stored %2$s (%3$s) hit thermal runaway and violently detonated!",
            "一台%1$s储存的%2$s（%3$s）发生热失控，剧烈爆炸！");

    public static final LangKey MESSAGE_MACHINE_DESTROYED_HEAT_FLASH = LANG.key(
            "message",
            "machine_destroyed.heat.flash",
            "A %1$s's pent-up %2$s (%3$s) gushed out as its shell cracked, " + "scorching and igniting the surroundings (%4$s s ablaze)!",
            "一台%1$s积聚的%2$s（%3$s）随外壳裂开喷涌而出，" + "灼烧并点燃了周围环境（燃烧 %4$s 秒）！");

    private BuiltinOIMachineFeedbackLang() {}

    public static void init() {}
}
