package net.ptcrys.topo.mixin;

import net.minecraft.gametest.framework.GameTestServer;

import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Suppresses {@link GameTestServer}'s repeated progress-bar log lines. */
@Mixin(GameTestServer.class)
public abstract class GameTestServerSilencerMixin {

    @Redirect(
              method = "tickServer",
              at = @At(
                       value = "INVOKE",
                       target = "Lorg/slf4j/Logger;info(Ljava/lang/String;)V",
                       ordinal = 0))
    private void topo$dropPeriodicProgressBar(Logger logger, String message) {
        // intentional no-op
    }

    @Redirect(
              method = "tickServer",
              at = @At(
                       value = "INVOKE",
                       target = "Lorg/slf4j/Logger;info(Ljava/lang/String;)V",
                       ordinal = 1))
    private void topo$dropFinalProgressBar(Logger logger, String message) {
        // intentional no-op
    }
}
