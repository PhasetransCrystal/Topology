package net.ptcrys.topo.data.equipment;

import net.ptcrys.topo.api.api.lang.LangKey;

import net.minecraft.network.chat.Component;

import java.util.Objects;

/**
 * One equipment function-description line: registered name + description handles. Created only at
 * equipment registration ({@code StandardEquipmentBase.addTooltipLine}); call sites never rebuild
 * keys by string concat.
 */
public record EquipmentTooltipLine(LangKey nameLang, LangKey descriptionLang) {

    public EquipmentTooltipLine {
        Objects.requireNonNull(nameLang, "nameLang");
        Objects.requireNonNull(descriptionLang, "descriptionLang");
    }

    public Component name() {
        return nameLang.getComponent();
    }

    public Component description() {
        return descriptionLang.getComponent();
    }
}
