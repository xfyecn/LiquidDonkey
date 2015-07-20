/*
 * The MIT License
 *
 * Copyright 2015 Ahseya.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.github.horrorho.liquiddonkey.cloud;

import com.github.horrorho.liquiddonkey.cloud.protobuf.ICloud;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntUnaryOperator;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SnapshotIdReferences.
 * <p>
 * Snapshot id references from 1 inclusive are treated as absolute. Zero id references refers to the first available
 * snapshot. Negative id references refer to relative offsets from the latest snapshot.
 * <p>
 * Incomplete snapshots are not considered.
 * <p>
 * Non-existent id references are returned as -1.
 * <p>
 * Example. Snapshots available 5 10 11 12(incomplete):
 * <p>
 * id 0 > 1
 * <p>
 * id 1 > -1 (1 is unavailable)
 * <p>
 * id 5 > 5
 * <p>
 * id 12 > 12 (12 is unavailable)
 * <p>
 * id -1 > 11
 * <p>
 * id -2 > 10
 * <p>
 * id -3 > -1 (9 is unavailable)
 *
 * @author Ahseya
 */
public class SnapshotIdReferences implements IntUnaryOperator {

    public static SnapshotIdReferences from(Backup backup) {
        logger.trace("<< from() < backup: {}", backup);

        Map<Integer, Integer> resolved = new HashMap<>();

        List<ICloud.MBSSnapshot> snapshots = backup.snapshots().stream()
                .filter(SnapshotIdReferences::isComplete)
                .sorted(Comparator.comparingLong(ICloud.MBSSnapshot::getSnapshotID))
                .collect(Collectors.toList());

        if (!snapshots.isEmpty()) {
            resolved.put(0, snapshots.get(0).getSnapshotID());
            int max = snapshots.stream().mapToInt(ICloud.MBSSnapshot::getSnapshotID).max().orElse(0);

            for (int index = 0; index < snapshots.size(); index++) {
                ICloud.MBSSnapshot snapshot = snapshots.get(index);
                int id = snapshot.getSnapshotID();

                resolved.put(id, snapshot.getSnapshotID());
                resolved.put(max - index - 1, snapshot.getSnapshotID());
            }
        }

        SnapshotIdReferences instance = new SnapshotIdReferences(resolved);

        logger.trace(">> from() > {}", instance);
        return instance;
    }

    static boolean isComplete(ICloud.MBSSnapshot snapshot) {
        if (snapshot.getQuotaReserved() == 0) {
            logger.warn("-- from() > incomplete snapshot: {}", snapshot);
            return false;
        }
        return true;
    }

    private static final Logger logger = LoggerFactory.getLogger(SnapshotIdReferences.class);
    private final Map<Integer, Integer> resolved;

    SnapshotIdReferences(Map<Integer, Integer> resolved) {
        this.resolved = resolved;
    }

    @Override
    public int applyAsInt(int idReference) {
        return resolved.getOrDefault(idReference, -1);
    }

    @Override
    public String toString() {
        return "SnapshotIdResolver{" + "resolved=" + resolved + '}';
    }
}
