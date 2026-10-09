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
package inetsoft.sree.internal.cluster.ignite;

import inetsoft.sree.internal.cluster.ignite.MinorityIslandDetector.Island;
import org.apache.ignite.cluster.ClusterNode;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #78105: the rule that picks which cluster island keeps serving must give the same answer
 * on every island, so that exactly one survives.
 */
@Tag("core")
class MinorityIslandDetectorTest {
   @Test
   void moreServersWins() {
      Island large = Island.of(List.of(node(id(5), 300L), node(id(6), 400L)));
      Island small = Island.of(List.of(node(id(1), 100L)));

      assertTrue(large.beats(small));
      assertFalse(small.beats(large), "the older node alone doesn't beat a larger cluster");
   }

   @Test
   void tieGoesToTheEarliestStartedMember() {
      Island first = Island.of(List.of(node(id(9), 100L)));
      Island second = Island.of(List.of(node(id(1), 200L)));
      Island third = Island.of(List.of(node(id(2), 300L)));

      assertTrue(first.beats(second));
      assertTrue(first.beats(third));
      assertFalse(second.beats(first));
      assertFalse(third.beats(first));
      assertTrue(second.beats(third));
   }

   @Test
   void tieWithoutStartTimesGoesToTheSmallestNodeId() {
      Island a = Island.of(List.of(node(id(1), null)));
      Island b = Island.of(List.of(node(id(2), null)));

      assertTrue(a.beats(b));
      assertFalse(b.beats(a));
      assertFalse(a.beats(a), "an island never beats itself");
   }

   private static UUID id(int n) {
      return new UUID(0L, n);
   }

   private static ClusterNode node(UUID id, Long startTime) {
      ClusterNode node = mock(ClusterNode.class);
      when(node.id()).thenReturn(id);
      when(node.attribute("local.ip.addr")).thenReturn("10.0.0." + id.getLeastSignificantBits());
      when(node.attribute(IgniteCluster.START_TIME_ATTR)).thenReturn(startTime);
      return node;
   }
}
