package net.ptcrys.topo.api.machine.data;

import net.minecraft.resources.Identifier;

import com.mojang.serialization.Codec;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DataTransactionTest {

    private static final DataKey<Integer> VALUE = DataKey.immutable(Identifier.fromNamespaceAndPath("example", "value"), Codec.INT, 0);
    private static final DataKey<Integer> DERIVED = DataKey.immutable(Identifier.fromNamespaceAndPath("example", "derived"), Codec.INT, -1);

    @Test
    void defaultValueCanBeAnObservedFactAndCannotBeOverwrittenDuringVerification() {
        var work = DataSnapshot.defaults(List.of(VALUE, DERIVED)).edit();
        assertFalse(work.assigned(VALUE));
        work.set(VALUE, 0);
        var verified = work.snapshot().verify();
        assertTrue(verified.assigned(VALUE));
        assertThrows(DataTransaction.Conflict.class, () -> verified.set(VALUE, 1));
        verified.set(VALUE, 0);
        verified.set(DERIVED, 4);
        assertEquals(0, work.get(VALUE));
        assertFalse(work.assigned(DERIVED));
    }

    @Test
    void forkRollbackAndExplicitCommitDoNotAliasMutableValues() {
        List<String> initial = new ArrayList<>();
        var key = new DataKey<List<String>>(Identifier.fromNamespaceAndPath("example", "list"), Codec.STRING.listOf(), initial, ArrayList::new);
        initial.add("outside");
        var work = DataSnapshot.defaults(List.of(key)).edit();
        assertTrue(work.get(key).isEmpty());
        var branch = work.fork();
        branch.set(key, new ArrayList<>(List.of("accepted")));
        assertTrue(work.get(key).isEmpty());
        work.accept(branch);
        var frozen = work.snapshot();
        work.get(key).add("escaped read");
        branch.set(key, List.of("other branch"));
        assertEquals(List.of("accepted"), work.get(key));
        assertEquals(List.of("accepted"), frozen.get(key));
    }

    @Test
    void refreshPermissionIsLimitedToSelectedDerivedKeys() {
        var work = DataSnapshot.defaults(List.of(VALUE, DERIVED)).edit();
        work.set(VALUE, 7);
        work.set(DERIVED, 1);
        var verified = work.snapshot().verify().forkRefreshing(Set.of(DERIVED));
        verified.set(DERIVED, 2);
        assertThrows(DataTransaction.Conflict.class, () -> verified.set(VALUE, 8));
        assertEquals(7, verified.get(VALUE));
    }

    @Test
    void requirementsStayDistinctFromObservedFacts() {
        var work = DataSnapshot.defaults(List.of(VALUE, DERIVED)).edit();
        work.require(VALUE, 5);
        assertTrue(work.required(VALUE));
        assertFalse(work.assigned(VALUE));
        assertEquals(5, work.get(VALUE));
        assertEquals(5, work.requirement(VALUE));
        work.set(VALUE, 6);
        assertTrue(work.assigned(VALUE));
        assertEquals(6, work.get(VALUE));
        assertEquals(5, work.requirement(VALUE));
        var frozen = work.snapshot();
        assertTrue(frozen.required(VALUE));
        assertTrue(frozen.assignedKeys().contains(VALUE));
        assertEquals(6, frozen.get(VALUE));
        assertEquals(5, frozen.requirement(VALUE));
        var branch = work.fork();
        assertEquals(5, branch.requirement(VALUE));
        branch.require(VALUE, 5);
        assertThrows(IllegalArgumentException.class, () -> branch.require(VALUE, 7));
        assertThrows(IllegalArgumentException.class, () -> work.require(DataKey.immutable(Identifier.fromNamespaceAndPath("example", "undeclared"), Codec.INT, 0), 1));
    }

    @Test
    void schemaOrderIsStableAndConflictingNamesAreRejected() {
        var source = DataSnapshot.defaults(List.of(VALUE, DERIVED, VALUE));
        assertEquals(List.of(VALUE, DERIVED), new ArrayList<>(source.keys()));
        assertEquals(source, source.edit().snapshot());
        var conflicting = DataKey.immutable(VALUE.id(), Codec.STRING, "");
        assertThrows(IllegalArgumentException.class, () -> DataSnapshot.defaults(List.of(VALUE, conflicting)));
        assertThrows(IllegalArgumentException.class, () -> source.edit().accept(DataSnapshot.defaults(List.of(VALUE)).edit()));
    }
}
