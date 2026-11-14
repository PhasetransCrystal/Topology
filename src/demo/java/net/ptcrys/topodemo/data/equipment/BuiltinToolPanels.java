package net.ptcrys.topodemo.data.equipment;

import net.ptcrys.topo.api.api.builtin.RuntimeDomainRegistration;
import net.ptcrys.topo.api.equipment.EquipmentRegistry;
import net.ptcrys.topodemo.api.equipment.common.ToolHintPanel;
import net.ptcrys.topodemo.api.lang.ContentLang;

public final class BuiltinToolPanels {

    public static void register(RuntimeDomainRegistration runtime) {
        var panel = new ToolHintPanel(ContentLang.TOOL_HINT);
        for (var record : EquipmentRegistry.itemRecords()) {
            if (record.equipment() == BuiltinEquipment.REGULATOR) runtime.tooltip(record.entry().get(), panel);
        }
        runtime.tooltip(BuiltinTools.STRUCTURE_TOOL.get(), new ToolHintPanel(ContentLang.STRUCTURE_TOOL_HINT));
        runtime.tooltip(BuiltinTools.PIPE_SURVEYOR.get(), new ToolHintPanel(ContentLang.SURVEYOR_HINT));
    }

    private BuiltinToolPanels() {}
}
