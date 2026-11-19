package dev.gateworks.selection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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

    /**
     * Adds the inclusive cuboid as one selection group. Existing groups that
     * intersect the cuboid are merged so either corner belongs to the new group.
     * Returns false without changes if the requested cuboid exceeds maxBlocks.
     */
    public boolean addCuboid(BlockPosition first, BlockPosition second, int maxBlocks) {
        if (!first.worldId().equals(second.worldId()) || maxBlocks < 1) return false;
        long sizeX = Math.abs((long) first.x() - second.x()) + 1;
        long sizeY = Math.abs((long) first.y() - second.y()) + 1;
        long sizeZ = Math.abs((long) first.z() - second.z()) + 1;
        if (sizeX > maxBlocks || sizeY > maxBlocks / sizeX
                || sizeZ > maxBlocks / (sizeX * sizeY)) return false;

        int minX = Math.min(first.x(), second.x());
        int maxX = Math.max(first.x(), second.x());
        int minY = Math.min(first.y(), second.y());
        int maxY = Math.max(first.y(), second.y());
        int minZ = Math.min(first.z(), second.z());
        int maxZ = Math.max(first.z(), second.z());

        Set<UUID> groupsToMerge = new LinkedHashSet<>();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    UUID existingGroup = groupByBlock.get(new BlockPosition(first.worldId(), x, y, z));
                    if (existingGroup != null) groupsToMerge.add(existingGroup);
                }
            }
        }

        UUID newGroupId = UUID.randomUUID();
        Set<BlockPosition> mergedGroup = new LinkedHashSet<>();
        for (UUID oldGroupId : groupsToMerge) {
            Set<BlockPosition> oldGroup = blocksByGroup.remove(oldGroupId);
            if (oldGroup != null) mergedGroup.addAll(oldGroup);
        }

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    mergedGroup.add(new BlockPosition(first.worldId(), x, y, z));
                }
            }
        }

        for (BlockPosition member : mergedGroup) groupByBlock.put(member, newGroupId);
        blocksByGroup.put(newGroupId, mergedGroup);
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

    public void clear() {
        groupByBlock.clear();
        blocksByGroup.clear();
        anchor = null;
    }
}
