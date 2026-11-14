package net.ptcrys.topo.api.material.form;

import net.ptcrys.topo.api.material.MaterialContentContext;
import net.ptcrys.topo.api.material.MaterialFormTargets;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Frozen execution plan for one material/form pair. The pipeline creates plans after the complete
 * form table is published, validates their declared outputs and form-specific constraints, and
 * executes {@link #register} only during the content phase. Outputs and external targets are
 * captured when the plan is created; a plan must not mutate material state or recompute them.
 */
public interface MaterialFormPlan {

    /** Registry entries this plan will create or reuse. */
    List<MaterialContentKey> outputs();

    /** Form-specific validation, run after all material data is frozen. */
    void validate();

    /** Generates or reuses this form's content for the owning material. */
    void register(MaterialContentContext context);

    /** External targets captured by the plan; forms that use external targets should override. */
    default MaterialFormTargets targets() {
        return MaterialFormTargets.empty();
    }

    /** Lambda-friendly plan for forms that do not use external targets. */
    static MaterialFormPlan of(
                               List<MaterialContentKey> outputs,
                               Runnable validate,
                               Consumer<? super MaterialContentContext> register) {
        return of(outputs, MaterialFormTargets.empty(), validate, register);
    }

    /** Creates a plan whose content declarations are fixed at construction time. */
    static MaterialFormPlan of(
                               List<MaterialContentKey> outputs,
                               MaterialFormTargets targets,
                               Runnable validate,
                               Consumer<? super MaterialContentContext> register) {
        List<MaterialContentKey> frozenOutputs = List.copyOf(
                Objects.requireNonNull(outputs, "form plan outputs"));
        MaterialFormTargets frozenTargets = Objects.requireNonNull(targets, "form plan targets");
        Objects.requireNonNull(validate, "form plan validation");
        Objects.requireNonNull(register, "form plan registration");
        return new MaterialFormPlan() {

            @Override
            public List<MaterialContentKey> outputs() {
                return frozenOutputs;
            }

            @Override
            public MaterialFormTargets targets() {
                return frozenTargets;
            }

            @Override
            public void validate() {
                validate.run();
            }

            @Override
            public void register(MaterialContentContext context) {
                register.accept(context);
            }
        };
    }
}
