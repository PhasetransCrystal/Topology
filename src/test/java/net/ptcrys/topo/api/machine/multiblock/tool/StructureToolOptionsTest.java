package net.ptcrys.topo.api.machine.multiblock.tool;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StructureToolOptionsTest {

    @Test
    void builtInOptionsValidateWithoutNeedingAnItemStackOrComponents() {
        var module = StructureToolOptions.all().stream().filter(option -> option.id().equals(StructureToolOptions.MODULE)).findFirst().orElseThrow();
        assertEquals("2", StructureToolOptions.validate(module, " 02 "));
        assertThrows(IllegalArgumentException.class, () -> StructureToolOptions.validate(module, "-1"));
        assertThrows(IllegalArgumentException.class, () -> StructureToolOptions.validate(module, "65536"));
        var repeats = StructureToolOptions.all().stream().filter(option -> option.id().equals(StructureToolOptions.REPEATS)).findFirst().orElseThrow();
        assertEquals("", StructureToolOptions.validate(repeats, "  "));
        assertEquals("1,2,0", StructureToolOptions.validate(repeats, "1, 2, 0"));
        assertThrows(IllegalArgumentException.class, () -> StructureToolOptions.validate(repeats, "1,,2"));
        assertThrows(IllegalArgumentException.class, () -> StructureToolOptions.validate(repeats, "1,-1"));
        assertThrows(IllegalArgumentException.class, () -> StructureToolOptions.validate(repeats, "0".repeat(257)));
        assertThrows(UnsupportedOperationException.class, () -> StructureToolOptions.all().clear());
    }

    @Test
    void extensionOptionsAreRegisteredWithoutAddingDedicatedMenuFields() {
        var option = new StructureToolOptions.Option() {

            public Identifier id() {
                return Identifier.fromNamespaceAndPath("topo_test", "casing_style");
            }

            public Component label() {
                return Component.literal("Style");
            }

            public String defaultValue() {
                return "standard";
            }

            public List<String> choices() {
                return List.of("standard", "alternate");
            }

            public String normalize(String value) {
                if (!choices().contains(value)) throw new IllegalArgumentException("Invalid style");
                return value;
            }
        };
        StructureToolOptions.register(option);
        assertTrue(StructureToolOptions.all().contains(option));
        assertEquals("alternate", StructureToolOptions.validate(option, "alternate"));
        assertThrows(IllegalArgumentException.class, () -> StructureToolOptions.validate(option, "unknown"));
        assertThrows(IllegalArgumentException.class, () -> StructureToolOptions.register(option));
    }
}
