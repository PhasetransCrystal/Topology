package net.ptcrys.topo.dev;

import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import com.mojang.brigadier.Command;

/** Developer commands under {@code /topo-dev}; detailed results go to the run console log. */
public final class TopoDevCommands {

    private TopoDevCommands() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(TopoDevCommands::onRegisterCommands);
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("topo-dev")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("scanrecipe").executes(context -> {
                    RecipeConflictScanner.Report report = RecipeConflictScanner.scan(context.getSource().getServer());
                    RecipeConflictScanner.log(report);
                    int conflicts = report.conflictCount();
                    context.getSource().sendSuccess(() -> Component.literal(
                            "Topo recipe scan: " + report.recipeCount() + " recipes, " + conflicts + " conflicts (details in log)"), false);
                    return conflicts == 0 ? Command.SINGLE_SUCCESS : 0;
                })));
    }
}
