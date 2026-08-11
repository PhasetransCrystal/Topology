package net.ptcrys.topo.datav2.material.common;

public final class MassData {

    private final int value;

    private MassData(int value) {
        this.value = value;
    }

    static MassData create(int value) {
        if (value <= 0) {
            throw new IllegalArgumentException("mass must be positive: " + value);
        }
        return new MassData(value);
    }

    public int value() {
        return value;
    }
}
