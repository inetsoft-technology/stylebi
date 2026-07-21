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

import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCompute;
import org.apache.ignite.cluster.ClusterGroup;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies {@code IgniteCluster.getIgniteCompute(Ignite, ClusterGroup, int)} routes tasks to one
 * of the two named executor pools (never the bare, unnamed compute) for every task level, and
 * that both pools actually get used across a range of levels -- a regression guard for the
 * off-by-one bug where the second named pool ("IGNITE_EXECUTE_POOL1") was never assigned any
 * task.
 */
@Tag("core")
class IgniteClusterComputeRoutingTest {
   @ParameterizedTest(name = "level {0} routes through a named executor (cluster group given)")
   @ValueSource(ints = { 0, 1, 2, 3, 4, 5 })
   void routesEveryLevelThroughANamedExecutor(int level) throws Exception {
      Ignite ignite = mock(Ignite.class);
      ClusterGroup clusterGroup = mock(ClusterGroup.class);
      IgniteCompute clusterCompute = mock(IgniteCompute.class);
      IgniteCompute namedCompute = mock(IgniteCompute.class);
      when(ignite.compute(clusterGroup)).thenReturn(clusterCompute);
      when(clusterCompute.withExecutor(anyString())).thenReturn(namedCompute);

      IgniteCompute result = invokeGetIgniteCompute(ignite, clusterGroup, level);

      assertSame(namedCompute, result,
                 "getIgniteCompute() must return the executor-bound compute instance, " +
                 "never the bare cluster compute");
   }

   @ParameterizedTest(name = "level {0} routes through a named executor (no cluster group)")
   @ValueSource(ints = { 0, 1, 2, 3 })
   void routesEveryLevelThroughANamedExecutorWithNoClusterGroup(int level) throws Exception {
      Ignite ignite = mock(Ignite.class);
      IgniteCompute defaultCompute = mock(IgniteCompute.class);
      IgniteCompute namedCompute = mock(IgniteCompute.class);
      when(ignite.compute()).thenReturn(defaultCompute);
      when(defaultCompute.withExecutor(anyString())).thenReturn(namedCompute);

      IgniteCompute result = invokeGetIgniteCompute(ignite, null, level);

      assertSame(namedCompute, result);
   }

   @Test
   void bothNamedExecutorPoolsAreUsedAcrossConsecutiveLevels() throws Exception {
      Set<String> poolNamesUsed = new HashSet<>();

      for(int level = 0; level < 4; level++) {
         Ignite ignite = mock(Ignite.class);
         IgniteCompute defaultCompute = mock(IgniteCompute.class);
         when(ignite.compute()).thenReturn(defaultCompute);
         when(defaultCompute.withExecutor(anyString())).thenReturn(mock(IgniteCompute.class));

         invokeGetIgniteCompute(ignite, null, level);

         ArgumentCaptor<String> poolNameCaptor = ArgumentCaptor.forClass(String.class);
         verify(defaultCompute).withExecutor(poolNameCaptor.capture());
         poolNamesUsed.add(poolNameCaptor.getValue());
      }

      assertEquals(2, poolNamesUsed.size(),
                   "both named executor pools should be used across levels 0-3, " +
                   "got: " + poolNamesUsed);
   }

   private static IgniteCompute invokeGetIgniteCompute(
      Ignite ignite, ClusterGroup clusterGroup, int level) throws Exception
   {
      Method method = IgniteCluster.class.getDeclaredMethod(
         "getIgniteCompute", Ignite.class, ClusterGroup.class, int.class);
      method.setAccessible(true);
      return (IgniteCompute) method.invoke(null, ignite, clusterGroup, level);
   }
}
