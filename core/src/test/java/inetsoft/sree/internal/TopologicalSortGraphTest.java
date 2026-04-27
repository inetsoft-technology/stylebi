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
package inetsoft.sree.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

class TopologicalSortGraphTest {

   private TopologicalSortGraph<String> graph;

   @BeforeEach
   void setUp() {
      graph = new TopologicalSortGraph<>();
   }

   // ---- addNode / getAllNodes ----

   @Test
   void addNode_singleNode_appearsInGetAllNodes() {
      TopologicalSortGraph<String>.GraphNode node = graph.new GraphNode("A");
      graph.addNode(node);

      assertThat(graph.getAllNodes(), contains(node));
   }

   @Test
   void addNode_multipleNodes_allAppearInGetAllNodes() {
      TopologicalSortGraph<String>.GraphNode nodeA = graph.new GraphNode("A");
      TopologicalSortGraph<String>.GraphNode nodeB = graph.new GraphNode("B");
      TopologicalSortGraph<String>.GraphNode nodeC = graph.new GraphNode("C");
      graph.addNode(nodeA);
      graph.addNode(nodeB);
      graph.addNode(nodeC);

      Collection<TopologicalSortGraph<String>.GraphNode> all = graph.getAllNodes();
      assertEquals(3, all.size());
      assertThat(all, containsInAnyOrder(nodeA, nodeB, nodeC));
   }

   @Test
   void emptyGraph_getAllNodes_returnsEmptyCollection() {
      assertTrue(graph.getAllNodes().isEmpty());
   }

   // ---- getNodeByData ----

   @Test
   void getNodeByData_existingData_returnsNode() {
      TopologicalSortGraph<String>.GraphNode node = graph.new GraphNode("A");
      graph.addNode(node);

      assertSame(node, graph.getNodeByData("A"));
   }

   @Test
   void getNodeByData_missingData_returnsNull() {
      assertNull(graph.getNodeByData("MISSING"));
   }

   // ---- removeNode ----

   @Test
   void removeNode_removesFromGetAllNodes() {
      TopologicalSortGraph<String>.GraphNode nodeA = graph.new GraphNode("A");
      TopologicalSortGraph<String>.GraphNode nodeB = graph.new GraphNode("B");
      graph.addNode(nodeA);
      graph.addNode(nodeB);

      graph.removeNode(nodeA);

      assertThat(graph.getAllNodes(), contains(nodeB));
      assertNull(graph.getNodeByData("A"));
   }

   @Test
   void removeNode_cleansUpEdgesFromParents() {
      TopologicalSortGraph<String>.GraphNode parent = graph.new GraphNode("parent");
      TopologicalSortGraph<String>.GraphNode child = graph.new GraphNode("child");
      graph.addNode(parent);
      parent.addChild(child);

      assertThat(parent.getChildren(), hasItem(child));

      graph.removeNode(child);

      assertThat(parent.getChildren(), not(hasItem(child)));
   }

   @Test
   void removeNode_idempotent_noExceptionOnSecondRemove() {
      TopologicalSortGraph<String>.GraphNode node = graph.new GraphNode("A");
      graph.addNode(node);
      graph.removeNode(node);
      graph.removeNode(node); // second removal should be silent
      assertTrue(graph.getAllNodes().isEmpty());
   }

   // ---- getLeafNodes ----

   @Test
   void getLeafNodes_noChildren_allNodesAreLeafs() {
      TopologicalSortGraph<String>.GraphNode nodeA = graph.new GraphNode("A");
      TopologicalSortGraph<String>.GraphNode nodeB = graph.new GraphNode("B");
      graph.addNode(nodeA);
      graph.addNode(nodeB);

      List<TopologicalSortGraph<String>.GraphNode> leafs = graph.getLeafNodes();
      assertThat(leafs, containsInAnyOrder(nodeA, nodeB));
   }

   @Test
   void getLeafNodes_parentHasChild_onlyChildIsLeaf() {
      TopologicalSortGraph<String>.GraphNode parent = graph.new GraphNode("parent");
      TopologicalSortGraph<String>.GraphNode child = graph.new GraphNode("child");
      graph.addNode(parent);
      parent.addChild(child);

      List<TopologicalSortGraph<String>.GraphNode> leafs = graph.getLeafNodes();
      assertThat(leafs, contains(child));
      assertThat(leafs, not(hasItem(parent)));
   }

   @Test
   void getLeafNodes_emptyGraph_returnsEmptyList() {
      assertTrue(graph.getLeafNodes().isEmpty());
   }

   @Test
   void getLeafNodes_chainOfNodes_onlyTailIsLeaf() {
      TopologicalSortGraph<String>.GraphNode a = graph.new GraphNode("A");
      TopologicalSortGraph<String>.GraphNode b = graph.new GraphNode("B");
      TopologicalSortGraph<String>.GraphNode c = graph.new GraphNode("C");
      graph.addNode(a);
      a.addChild(b);
      b.addChild(c);

      List<TopologicalSortGraph<String>.GraphNode> leafs = graph.getLeafNodes();
      assertThat(leafs, contains(c));
   }

   // ---- getNode(Function) ----

   @Test
   void getNode_nullPredicate_returnsAnyNode() {
      TopologicalSortGraph<String>.GraphNode node = graph.new GraphNode("A");
      graph.addNode(node);

      assertNotNull(graph.getNode(null));
   }

   @Test
   void getNode_nullPredicate_emptyGraph_returnsNull() {
      assertNull(graph.getNode(null));
   }

   @Test
   void getNode_matchingPredicate_returnsMatchingNode() {
      TopologicalSortGraph<String>.GraphNode nodeA = graph.new GraphNode("A");
      TopologicalSortGraph<String>.GraphNode nodeB = graph.new GraphNode("B");
      graph.addNode(nodeA);
      graph.addNode(nodeB);

      TopologicalSortGraph<String>.GraphNode result =
         graph.getNode(n -> "B".equals(n.getData()));
      assertSame(nodeB, result);
   }

   @Test
   void getNode_noMatchingPredicate_returnsNull() {
      TopologicalSortGraph<String>.GraphNode nodeA = graph.new GraphNode("A");
      graph.addNode(nodeA);

      assertNull(graph.getNode(n -> "Z".equals(n.getData())));
   }

   // ---- getNodes(Function) ----

   @Test
   void getNodes_nullPredicate_returnsNull() {
      graph.addNode(graph.new GraphNode("A"));
      assertNull(graph.getNodes(null));
   }

   @Test
   void getNodes_matchingPredicate_returnsMultipleResults() {
      TopologicalSortGraph<Integer> intGraph = new TopologicalSortGraph<>();
      TopologicalSortGraph<Integer>.GraphNode a = intGraph.new GraphNode(2);
      TopologicalSortGraph<Integer>.GraphNode b = intGraph.new GraphNode(4);
      TopologicalSortGraph<Integer>.GraphNode c = intGraph.new GraphNode(3);
      intGraph.addNode(a);
      intGraph.addNode(b);
      intGraph.addNode(c);

      List<TopologicalSortGraph<Integer>.GraphNode> evens =
         intGraph.getNodes(n -> (Integer) n.getData() % 2 == 0);
      assertEquals(2, evens.size());
      assertThat(evens, containsInAnyOrder(a, b));
   }

   @Test
   void getNodes_noMatches_returnsEmptyList() {
      graph.addNode(graph.new GraphNode("A"));
      List<TopologicalSortGraph<String>.GraphNode> result =
         graph.getNodes(n -> false);
      assertTrue(result.isEmpty());
   }

   // ---- GraphNode.addChild ----

   @Test
   void addChild_childAddedToParentsChildren() {
      TopologicalSortGraph<String>.GraphNode parent = graph.new GraphNode("parent");
      TopologicalSortGraph<String>.GraphNode child = graph.new GraphNode("child");
      graph.addNode(parent);

      parent.addChild(child);

      assertThat(parent.getChildren(), contains(child));
   }

   @Test
   void addChild_autoRegistersChildInGraph() {
      TopologicalSortGraph<String>.GraphNode parent = graph.new GraphNode("parent");
      TopologicalSortGraph<String>.GraphNode child = graph.new GraphNode("child");
      graph.addNode(parent);

      // child was not explicitly added — addChild should register it
      parent.addChild(child);

      assertSame(child, graph.getNodeByData("child"));
   }

   @Test
   void addChild_alreadyInGraph_doesNotDuplicate() {
      TopologicalSortGraph<String>.GraphNode parent = graph.new GraphNode("parent");
      TopologicalSortGraph<String>.GraphNode child = graph.new GraphNode("child");
      graph.addNode(parent);
      graph.addNode(child);

      parent.addChild(child);

      assertEquals(2, graph.getAllNodes().size());
      assertThat(parent.getChildren(), contains(child));
   }

   @Test
   void addChild_multipleChildren_allPresent() {
      TopologicalSortGraph<String>.GraphNode parent = graph.new GraphNode("parent");
      TopologicalSortGraph<String>.GraphNode childA = graph.new GraphNode("A");
      TopologicalSortGraph<String>.GraphNode childB = graph.new GraphNode("B");
      graph.addNode(parent);

      parent.addChild(childA);
      parent.addChild(childB);

      assertThat(parent.getChildren(), containsInAnyOrder(childA, childB));
   }

   // ---- GraphNode.equals / hashCode ----

   @Test
   void graphNodeEquals_sameData_equal() {
      TopologicalSortGraph<String>.GraphNode a = graph.new GraphNode("X");
      TopologicalSortGraph<String>.GraphNode b = graph.new GraphNode("X");
      assertEquals(a, b);
   }

   @Test
   void graphNodeEquals_differentData_notEqual() {
      TopologicalSortGraph<String>.GraphNode a = graph.new GraphNode("X");
      TopologicalSortGraph<String>.GraphNode b = graph.new GraphNode("Y");
      assertNotEquals(a, b);
   }

   @Test
   void graphNodeHashCode_sameData_sameHash() {
      TopologicalSortGraph<String>.GraphNode a = graph.new GraphNode("X");
      TopologicalSortGraph<String>.GraphNode b = graph.new GraphNode("X");
      assertEquals(a.hashCode(), b.hashCode());
   }

   @Test
   void graphNodeHashCode_nullData_zeroHash() {
      TopologicalSortGraph<String>.GraphNode node = graph.new GraphNode(null);
      assertEquals(0, node.hashCode());
   }

   // ---- getData ----

   @Test
   void getData_returnsConstructorValue() {
      TopologicalSortGraph<String>.GraphNode node = graph.new GraphNode("hello");
      assertEquals("hello", node.getData());
   }
}
