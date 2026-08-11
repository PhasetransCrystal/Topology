package net.ptcrys.topo.gametest;

import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.ResourceKey;

import java.util.function.Consumer;

final class InlineGameTestInstance extends FunctionGameTestInstance {

    private final Consumer<GameTestHelper> test;

    InlineGameTestInstance(
                           ResourceKey<Consumer<GameTestHelper>> function,
                           TestData<Holder<TestEnvironmentDefinition<?>>> info,
                           Consumer<GameTestHelper> test) {
        super(function, info);
        this.test = test;
    }

    @Override
    public void run(GameTestHelper helper) {
        test.accept(helper);
    }
}
