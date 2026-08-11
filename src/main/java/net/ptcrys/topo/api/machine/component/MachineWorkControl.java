package net.ptcrys.topo.api.machine.component;

/**
 * Run/halt switch for traits that process work over time. Halting freezes progress, stops
 * per-tick input consumption and prevents new work from starting; resuming continues the held
 * work where it stopped. Sibling of {@link MachineWorkView} (the read-only face).
 */
public interface MachineWorkControl {

    ServiceKey<MachineWorkControl, Void> KEY = ServiceKey.oi("machine_work_control", MachineWorkControl.class, Void.class);

    WorkMode workMode();

    /** Flips the mode and returns the mode now in effect. */
    WorkMode toggleWorkMode();

    enum WorkMode {
        RUNNING,
        HALTED
    }
}
