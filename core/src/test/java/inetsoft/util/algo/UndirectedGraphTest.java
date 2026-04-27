/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.util.algo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class UndirectedGraphTest {

   private UndirectedGraph<String> graph;

   @BeforeEach
   void setUp() {
      graph = new UndirectedGraph<>();
   }

   @Test
   void singleNodeIsItsOwnCluster() {
      graph.addCluster(Set.of("A"));
      Set<String> cluster = graph.getCluster("A");
      assertEquals(1, cluster.size());
      assertTrue(cluster.contains("A"));
   }

   @Test
   void twoConnectedNodesAreInSameCluster() {
      graph.addCluster(Set.of("A", "B"));

      Set<String> clusterA = graph.getCluster("A");
      Set<String> clusterB = graph.getCluster("B");

      assertTrue(clusterA.contains("A"));
      assertTrue(clusterA.contains("B"));
      assertEquals(clusterA, clusterB);
   }

   @Test
   void twoDisconnectedPairsProduceDifferentClusters() {
      graph.addCluster(Set.of("A", "B"));
      graph.addCluster(Set.of("C", "D"));

      Set<String> clusterAB = graph.getCluster("A");
      Set<String> clusterCD = graph.getCluster("C");

      assertTrue(clusterAB.contains("A"));
      assertTrue(clusterAB.contains("B"));
      assertFalse(clusterAB.contains("C"));
      assertFalse(clusterAB.contains("D"));

      assertTrue(clusterCD.contains("C"));
      assertTrue(clusterCD.contains("D"));
      assertFalse(clusterCD.contains("A"));
      assertFalse(clusterCD.contains("B"));
   }

   @Test
   void chainThreeNodesAllInSameCluster() {
      // a-b connected, then b-c connected → a, b, c all reachable
      graph.addCluster(Set.of("a", "b"));
      graph.addCluster(Set.of("b", "c"));

      Set<String> cluster = graph.getCluster("a");
      assertEquals(3, cluster.size());
      assertTrue(cluster.contains("a"));
      assertTrue(cluster.contains("b"));
      assertTrue(cluster.contains("c"));
   }

   @Test
   void getClusterReturnsAllReachableNodesBfs() {
      graph.addCluster(Set.of("1", "2"));
      graph.addCluster(Set.of("2", "3"));
      graph.addCluster(Set.of("3", "4"));

      Set<String> cluster = graph.getCluster("1");
      assertEquals(4, cluster.size());
      assertTrue(cluster.contains("1"));
      assertTrue(cluster.contains("2"));
      assertTrue(cluster.contains("3"));
      assertTrue(cluster.contains("4"));
   }

   @Test
   void addingAlreadyConnectedPairIsIdempotent() {
      graph.addCluster(Set.of("X", "Y"));
      graph.addCluster(Set.of("X", "Y"));

      Set<String> cluster = graph.getCluster("X");
      assertEquals(2, cluster.size());
      assertTrue(cluster.contains("X"));
      assertTrue(cluster.contains("Y"));
   }

   @Test
   void nodeNeverAddedHasNoCluster() {
      Set<String> cluster = graph.getCluster("ghost");
      assertNotNull(cluster);
      assertTrue(cluster.isEmpty());
   }
}
