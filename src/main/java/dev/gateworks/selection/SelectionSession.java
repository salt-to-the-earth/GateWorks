package dev.gateworks.selection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** In-memory working selection for one administrator. */
public final class SelectionSession {
    private final Map<BlockPosition, UUID> groupByBlock = new LinkedHashMap<>();
    private final Map<UUID, Set<BlockPosition>> blocksByGroup = new HashMap<>();
    private BlockPosition anchor;

    public void setAnchor(BlockPosition anchor) { this.anchor = anchor; }
    public BlockPosition getAnchor() { return anchor; }

    public void addSingle(BlockPosition position) {
        if (groupByBlock.containsKey(position)) return;
        UUID groupId = UUID.randomUUID();
        groupByBlock.put(position, groupId);
        blocksByGroup.put(groupId, new LinkedHashSet<>(Set.of(position)));
    }

    /** Adds the inclusive cuboid, or returns false without changes if it is too large. */
    public boolean addCuboid(BlockPosition first, BlockPosition second, int maxBlocks) {
        if (!first.worldId().equals(second.worldId()) || maxBlocks < 1) return false;
        long sizeX = Math.abs((long) first.x() - second.x()) + 1;
        long sizeY = Math.abs((long) first.y() - second.y()) + 1;
        long sizeZ = Math.abs((long) first.z() - second.z()) + 1;
        if (sizeX > maxBlocks || sizeY > maxBlocks / sizeX
                || sizeZ > maxBlocks / (sizeX * sizeY)) return false;

        UUID groupId = UUID.randomUUID();
        Set<BlockPosition> group = new LinkedHashSet<>();

        // The first corner is selected before the second shift-click. Absorb that
        // anchor group so the completed cuboid is represented as one selection group.
        UUID anchorGroupId = groupByBlock.get(first);
        if (anchorGroupId != null) {
            Set<BlockPosition> anchorGroup = blocksByGroup.remove(anchorGroupId);
            if (anchorGroup != null) {
                for (BlockPosition member : anchorGroup) {
                    groupByBlock.put(member, groupId);
                    group.add(member);
                }
            }
        }

        for (int x = Math.min(first.x(), second.x()); x <= Math.max(first.x(), second.x()); x++) {
            for (int y = Math.min(first.y(), second.y()); y <= Math.max(first.y(), second.y()); y++) {
                for (int z = Math.min(first.z(), second.z()); z <= Math.max(first.z(), second.z()); z++) {
                    BlockPosition position = new BlockPosition(first.worldId(), x, y, z);
                    if (!groupByBlock.containsKey(position)) {
                        groupByBlock.put(position, groupId);
                        group.add(position);
                    }
                }
            }
        }
        if (!group.isEmpty()) blocksByGroup.put(groupId, group);
        return true;
    }

    public boolean removeBlock(BlockPosition position) {
        UUID groupId = groupByBlock.remove(position);
        if (groupId == null) return false;
        Set<BlockPosition> group = blocksByGroup.get(groupId);
        if (group != null) {
            group.remove(position);
            if (group.isEmpty()) blocksByGroup.remove(groupId);
        }
        return true;
    }

    public int removeGroupAt(BlockPosition position) {
        UUID groupId = groupByBlock.get(position);
        if (groupId == null) return 0;
        Set<BlockPosition> group = blocksByGroup.remove(groupId);
        if (group == null) return 0;
        for (BlockPosition member : new ArrayList<>(group)) groupByBlock.remove(member);
        return group.size();
    }

    public boolean contains(BlockPosition position) { return groupByBlock.containsKey(position); }
    public int getBlockCount() { return groupByBlock.size(); }
    public int getGroupCount() { return blocksByGroup.size(); }

    /** Returns defensive copies for rendering; callers cannot mutate the selection. */
    public List<Set<BlockPosition>> getGroupsSnapshot() {
        List<Set<BlockPosition>> snapshot = new ArrayList<>();
        for (Set<BlockPosition> group : blocksByGroup.values()) {
            snapshot.add(Set.copyOf(group));
        }
        return List.copyOf(snapshot);
    }

    public void clear() {
        groupByBlock.clear();
        blocksByGroup.clear();
        anchor = null;
    }
}
