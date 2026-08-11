package net.ptcrys.topo.datav2.machine.common.component.resource;

import net.ptcrys.topo.api.lang.LangKey;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import org.jspecify.annotations.Nullable;

/**
 * What a {@link ScalarDestroyedBehavior} reports back after running its world effect: how severe the
 * destruction was, which chat line describes it, the accent color that line is tinted with, and the
 * magnitude values that line interpolates. Pure data — the behavior owns "what happened", while the
 * dispatch layer ({@code ScalarResourcePort} → {@link MachineDestroyedFeedback}) owns who hears it.
 * Machine and resource display names are injected at delivery, not baked into the report.
 *
 * <ul>
 * <li>{@code SILENT} — empty machine / residual heat: no chat, no shockwave.
 * <li>{@code BENIGN} — a notable-but-harmless effect (e.g. residual charge sparking away): chat
 * only, no shockwave.
 * <li>{@code HARMFUL} — an effect that damages/explodes/ignites: chat plus the radius-16 shockwave.
 * </ul>
 *
 * <p>
 * {@code accent} is the per-hazard line color (electric arc = yellow, detonation = red, heat flash
 * = gold, harmless dissipation = gray); the chat line tints its connective prose with it while the
 * machine name and magnitudes render bold white and the resource name keeps its own resource color.
 */
public record MachineDestroyedReport(
                                     Severity severity, @Nullable LangKey messageLang, ChatFormatting accent, Object[] magnitudes) {

    public enum Severity {
        SILENT,
        BENIGN,
        HARMFUL
    }

    private static final Object[] NO_MAGNITUDES = new Object[0];

    /** No chat, no shockwave. Returned by {@link ScalarDestroyedBehavior#NONE} and empty/residual tiers. */
    public static final MachineDestroyedReport SILENT = new MachineDestroyedReport(Severity.SILENT, null, ChatFormatting.WHITE, NO_MAGNITUDES);

    /** Chat only (no shockwave): a harmless effect worth one line of explanation. */
    public static MachineDestroyedReport benign(LangKey messageLang, ChatFormatting accent, Object... magnitudes) {
        return new MachineDestroyedReport(Severity.BENIGN, messageLang, accent, magnitudes);
    }

    /** Chat plus the radius-16 shockwave: an effect that hurt, exploded, or ignited. */
    public static MachineDestroyedReport harmful(LangKey messageLang, ChatFormatting accent, Object... magnitudes) {
        return new MachineDestroyedReport(Severity.HARMFUL, messageLang, accent, magnitudes);
    }

    /**
     * Builds the styled chat component for this report: connective prose tinted with {@link #accent},
     * machine name ({@code %1$s}) and magnitudes ({@code %3$s}…) bold white, resource name
     * ({@code %2$s}) left as passed in (the caller pre-tints it with the resource's own color).
     * Returns {@code null} when there is nothing to say (no message key).
     */
    public @Nullable Component message(Component machineName, Component resourceName) {
        if (messageLang == null) {
            return null;
        }
        Object[] args = new Object[2 + magnitudes.length];
        args[0] = machineName.copy().withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD);
        args[1] = resourceName;
        for (int i = 0; i < magnitudes.length; i++) {
            args[2 + i] = Component.literal(String.valueOf(magnitudes[i]))
                    .withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD);
        }
        return messageLang.getComponent(args).withStyle(accent);
    }
}
