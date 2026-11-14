package net.ptcrys.topo.api.machine.multiblock.tool;

import net.ptcrys.topo.api.machine.data.DataKey;
import net.ptcrys.topo.api.machine.data.DataSnapshot;
import net.ptcrys.topo.api.machine.data.DataTransaction;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import com.mojang.serialization.Codec;
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
    void extensionOptionsCanWriteDetectionRequirements() {
        var tier = DataKey.immutable(Identifier.fromNamespaceAndPath("topo_test", "required_tier"), Codec.INT, -1);
        var option = new StructureToolOptions.Option() {

            public Identifier id() {
                return Identifier.fromNamespaceAndPath("topo_test", "tier");
            }

            public Component label() {
                return Component.literal("Tier");
            }

            public String defaultValue() {
                return "2";
            }

            public String normalize(String value) {
                return Integer.toString(Integer.parseInt(value.trim()));
            }

            public void require(StructureToolOptions.Context context, String value, DataTransaction metadata) {
                StructureToolOptions.require(metadata, tier, Integer.parseInt(value));
            }
        };
        StructureToolOptions.register(option);
        var metadata = DataSnapshot.defaults(List.of(tier)).edit();
        option.require(null, "2", metadata);
        assertTrue(metadata.required(tier));
        assertEquals(2, metadata.get(tier));
        assertFalse(metadata.assigned(tier));
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
