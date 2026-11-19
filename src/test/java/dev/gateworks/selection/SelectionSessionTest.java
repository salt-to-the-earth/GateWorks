package dev.gateworks.selection;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class SelectionSessionTest {
    private final UUID world = UUID.randomUUID();

    @Test void addsIndividualBlocksAsSeparateGroups() {
        SelectionSession session = new SelectionSession();
        session.addSingle(new BlockPosition(world, 1, 64, 1));
        session.addSingle(new BlockPosition(world, 8, 64, 8));
        assertEquals(2, session.getBlockCount());
        assertEquals(2, session.getGroupCount());
    }

    @Test void addsInclusiveCuboidAsOneGroup() {
        SelectionSession session = new SelectionSession();
        assertTrue(session.addCuboid(new BlockPosition(world, 1, 60, 1),
                new BlockPosition(world, 3, 62, 2), 100));
        assertEquals(18, session.getBlockCount());
        assertEquals(1, session.getGroupCount());
    }

    @Test void cuboidAbsorbsItsPreviouslySelectedFirstCorner() {
        SelectionSession session = new SelectionSession();
        BlockPosition first = new BlockPosition(world, 1, 64, 1);
        BlockPosition second = new BlockPosition(world, 3, 64, 1);
        session.addSingle(first);
        assertTrue(session.addCuboid(first, second, 10));
        assertEquals(3, session.getBlockCount());
        assertEquals(1, session.getGroupCount());
        assertEquals(3, session.removeGroupAt(first));
    }

    @Test void rejectsOversizedCuboidWithoutChangingSelection() {
        SelectionSession session = new SelectionSession();
        session.addSingle(new BlockPosition(world, 0, 64, 0));
        assertFalse(session.addCuboid(new BlockPosition(world, 0, 0, 0),
                new BlockPosition(world, 20, 20, 20), 4096));
        assertEquals(1, session.getBlockCount());
        assertEquals(1, session.getGroupCount());
    }

    @Test void removingOneBlockKeepsOtherGroupMembers() {
        SelectionSession session = new SelectionSession();
        BlockPosition first = new BlockPosition(world, 0, 64, 0);
        BlockPosition second = new BlockPosition(world, 1, 64, 0);
        session.addCuboid(first, second, 10);
        assertTrue(session.removeBlock(first));
        assertEquals(1, session.getBlockCount());
        assertEquals(1, session.getGroupCount());
        assertTrue(session.contains(second));
    }

    @Test void removingGroupRemovesEveryMember() {
        SelectionSession session = new SelectionSession();
        BlockPosition first = new BlockPosition(world, 0, 64, 0);
        BlockPosition second = new BlockPosition(world, 1, 64, 0);
        session.addCuboid(first, second, 10);
        assertEquals(2, session.removeGroupAt(first));
        assertEquals(0, session.getBlockCount());
        assertEquals(0, session.getGroupCount());
    }
}
