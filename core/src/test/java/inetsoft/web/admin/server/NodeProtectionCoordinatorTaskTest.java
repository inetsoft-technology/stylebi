/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package inetsoft.web.admin.server;

/*
 * Test strategy
 *
 * The task is run with a mocked Cluster and plain HashMaps injected for the sheet count and
 * protection maps, so no Spring bean or ConfigurationContext is reached.
 *
 * Behavioral guarantees covered:
 *
 * [G1] Bug #78652: nodes that hold no runtime sheets are never protected, so a rolling
 *      deployment or a scale-in can stop any of them (the old task set of a deployment has
 *      no sheets, but used to stay protected because a new node sorted first).
 * [G2] Nodes that hold sheets are protected, except the one with the fewest, so a scale-in
 *      is still possible when every node is in use.
 * [G3] Ties for the fewest sheets go to the first node in sorted order.
 * [G4] A change of the sheet counts is reflected on the next run.
 * [G5] A value is only written to the map when it changes.
 */

import inetsoft.sree.internal.cluster.Cluster;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("core")
class NodeProtectionCoordinatorTaskTest {
   @BeforeEach
   void setUp() {
      nodes = new LinkedHashSet<>();
      cluster = mock(Cluster.class);
      when(cluster.getClusterNodes()).thenAnswer(inv -> new LinkedHashSet<>(nodes));
      sheetCounts = new HashMap<>();
      protection = new RecordingMap();
      task = new NodeProtectionCoordinatorTask();
      ReflectionTestUtils.setField(task, "cluster", cluster);
      ReflectionTestUtils.setField(task, "sheetCountMap", sheetCounts);
      ReflectionTestUtils.setField(task, "nodeProtectionMap", protection);
   }

   // [G1]
   @Test
   void nodesWithoutSheetsAreNotProtected() {
      // a rolling deployment: the new node sorts before every old node
      nodes.addAll(List.of("172.31.19.85:1", "172.31.3.1:1", "172.31.44.241:1", "172.31.3.4:1"));

      task.run();

      for(String node : nodes) {
         assertFalse(protection.getOrDefault(node, false), node + " has no sheets");
      }
   }

   // [G1]
   @Test
   void previouslyProtectedNodeIsReleasedWhenItsSheetsClose() {
      nodes.addAll(List.of("a:1", "b:1", "c:1"));
      sheetCounts.putAll(Map.of("a:1", 2, "b:1", 3, "c:1", 1));
      task.run();
      assertTrue(protection.get("b:1"));

      sheetCounts.put("b:1", 0);
      task.run();

      assertFalse(protection.get("b:1"));
   }

   // [G2]
   @Test
   void nodeWithFewestSheetsIsUnprotectedWhenAllNodesHoldSheets() {
      nodes.addAll(List.of("a:1", "b:1", "c:1"));
      sheetCounts.putAll(Map.of("a:1", 2, "b:1", 3, "c:1", 1));

      task.run();

      assertTrue(protection.get("a:1"));
      assertTrue(protection.get("b:1"));
      assertFalse(protection.getOrDefault("c:1", false));
   }

   // [G2]
   @Test
   void nodeWithSheetsIsProtectedNextToAnEmptyNode() {
      nodes.addAll(List.of("a:1", "b:1"));
      sheetCounts.put("a:1", 4);

      task.run();

      assertTrue(protection.get("a:1"));
      assertFalse(protection.getOrDefault("b:1", false));
   }

   // [G3]
   @Test
   void tieForFewestSheetsGoesToFirstSortedNode() {
      nodes.addAll(List.of("b:1", "a:1"));
      sheetCounts.putAll(Map.of("a:1", 2, "b:1", 2));

      task.run();

      assertFalse(protection.getOrDefault("a:1", false));
      assertTrue(protection.get("b:1"));
   }

   // [G2]
   @Test
   void singleNodeWithSheetsIsNotProtected() {
      nodes.add("a:1");
      sheetCounts.put("a:1", 5);

      task.run();

      assertFalse(protection.getOrDefault("a:1", false));
   }

   // [G4]
   @Test
   void unprotectedSlotMovesWhenCountsChange() {
      nodes.addAll(List.of("a:1", "b:1"));
      sheetCounts.putAll(Map.of("a:1", 1, "b:1", 2));
      task.run();
      assertFalse(protection.getOrDefault("a:1", false));
      assertTrue(protection.get("b:1"));

      sheetCounts.putAll(Map.of("a:1", 3, "b:1", 2));
      task.run();

      assertTrue(protection.get("a:1"));
      assertFalse(protection.get("b:1"));
   }

   // [G5]
   @Test
   void unchangedValuesAreNotWrittenAgain() {
      nodes.addAll(List.of("a:1", "b:1"));
      sheetCounts.putAll(Map.of("a:1", 1, "b:1", 2));
      task.run();
      int puts = protection.puts;

      task.run();

      assertEquals(puts, protection.puts);
   }

   /** Counts the writes, because each one raises a map event on every node. */
   private static final class RecordingMap extends HashMap<String, Boolean> {
      @Override
      public Boolean put(String key, Boolean value) {
         puts++;
         return super.put(key, value);
      }

      int puts;
   }

   private Set<String> nodes;
   private Cluster cluster;
   private Map<String, Integer> sheetCounts;
   private RecordingMap protection;
   private NodeProtectionCoordinatorTask task;
}
