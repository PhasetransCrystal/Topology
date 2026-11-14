package net.ptcrys.topo.api.machine.multiblock.pattern;

import net.ptcrys.topo.helper.IdHelper;

import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 无位置的结构化单元结果。引擎补充定位；命名空间错误码允许外部能力定义原因，组合结果保留子错误。 */
public record CellResult(Status status, Identifier code, Map<String, String> expected, Map<String, String> actual, List<CellResult> children) {

    public enum Status {
        PASS,
        FAIL,
        UNKNOWN
    }

    private static final CellResult PASS = new CellResult(Status.PASS, IdHelper.topo("cell/pass"), Map.of(), Map.of(), List.of());

    public CellResult {
        Objects.requireNonNull(status);
        Objects.requireNonNull(code);
        expected = Map.copyOf(expected);
        actual = Map.copyOf(actual);
        children = List.copyOf(children);
    }

    public boolean passed() {
        return status == Status.PASS;
    }

    public static CellResult pass() {
        return PASS;
    }

    public static CellResult fail(Identifier code, Map<String, String> expected, Map<String, String> actual) {
        return new CellResult(Status.FAIL, code, expected, actual, List.of());
    }

    public static CellResult failure(String reason, Object expected, Object actual) {
        return fail(IdHelper.topo("cell/" + reason), Map.of("value", String.valueOf(expected)), Map.of("value", String.valueOf(actual)));
    }

    public static CellResult unloaded() {
        return new CellResult(Status.UNKNOWN, IdHelper.topo("cell/unloaded"), Map.of(), Map.of(), List.of());
    }

    public static CellResult unknown() {
        return new CellResult(Status.UNKNOWN, IdHelper.topo("cell/unknown"), Map.of(), Map.of(), List.of());
    }

    public static CellResult combine(String reason, Status status, List<CellResult> children) {
        return new CellResult(status, IdHelper.topo("cell/" + reason), Map.of(), Map.of(), children);
    }

    public boolean hasCode(String reason) {
        return code.equals(IdHelper.topo("cell/" + reason)) || children.stream().anyMatch(child -> child.hasCode(reason));
    }
}
