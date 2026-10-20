package net.ptcrys.topodemo.data.equipment;

import net.ptcrys.registrylib.util.TextureRef;
import net.ptcrys.topo.api.equipment.Equipment;
import net.ptcrys.topodemo.api.equipment.common.RegulatorBehavior;
import net.ptcrys.topodemo.api.equipment.common.RegulatorEquipment;
import net.ptcrys.topodemo.data.BuiltinCreativeTabs;
import net.ptcrys.topodemo.data.ContentLang;
import net.ptcrys.topodemo.data.TopoDemoPlugin;

public final class BuiltinEquipment {

    public static final Equipment REGULATOR = TopoDemoPlugin.INSTANCE.equipment().kind("regulator",
            RegulatorEquipment.builder()
                    .registryPath("%s_regulator").displayName("%s Regulator", "%s调节器")
                    .texture(TextureRef.mc("item/compass_16"))
                    .creativeTab(() -> BuiltinCreativeTabs.KEY.getKey())
                    .machineBehavior(new RegulatorBehavior(ContentLang.TOGGLED))
                    .build());

    private BuiltinEquipment() {}

    public static void init() {}
}
