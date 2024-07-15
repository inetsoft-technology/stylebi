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

import inetsoft.util.Tool;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A simple graph implement for Topological sort.
 *
 * @version 13.6
 * @author InetSoft Technology Corp
 */
public class TopologicalSortGraph<T> {
   public GraphNode getNodeByData(T data) {
      return dataNodeMap.get(data);
   }

   public void addNode(GraphNode node) {
      dataNodeMap.put(node.data, node);
   }

   public void removeNode(GraphNode node) {
      for(GraphNode value : dataNodeMap.values()) {
         value.removeChild(node);
      }

      dataNodeMap.remove(node.data);
   }

   public Collection<GraphNode> getAllNodes() {
      return dataNodeMap.values();
   }

   public List<GraphNode> getLeafNodes() {
      List<GraphNode> leafNodes = new ArrayList<>();

      for(GraphNode value : dataNodeMap.values()) {
         if(value == null) {
            continue;
         }

         Set<GraphNode> children = value.getChildren();

         if(children == null || children.size() == 0) {
            leafNodes.add(value);
         }
      }

      return leafNodes;
   }

   public GraphNode getNode(Function<GraphNode, Boolean> matchFunction) {
      if(matchFunction == null) {
         return dataNodeMap.values().size() > 0 ? dataNodeMap.values().iterator().next() : null;
      }

      Optional<GraphNode> first = dataNodeMap.values().stream()
         .filter(node -> matchFunction.apply(node))
         .findFirst();

      return first.isPresent() ? first.get() : null;
   }

   public List<GraphNode> getNodes(Function<GraphNode, Boolean> matchFunction) {
      if(matchFunction == null) {
         return null;
      }

      return dataNodeMap.values().stream()
         .filter(node -> matchFunction.apply(node)).collect(Collectors.toList());
   }

   public class GraphNode implements Node<T> {
      public GraphNode(T data) {
         this.data = data;
      }

      public void addChild(GraphNode child) {
         if(children == null) {
            children = new HashSet<>();
         }

         children.add(child);

         if(TopologicalSortGraph.this.getNodeByData(child.data) == null) {
            TopologicalSortGraph.this.addNode(child);
         }
      }

      private void removeChild(GraphNode child) {
         if(children == null) {
            return;
         }

         children.remove(child);
      }

      public Set<GraphNode> getChildren() {
         return children;
      }

      @Override
      public int hashCode() {
         return data == null ? 0 : data.hashCode();
      }

      @Override
      public boolean equals(Object obj) {
         return obj instanceof Node && Tool.equals(data, ((Node) obj).getData());
      }

      private Set<GraphNode> children;
      private T data;

      @Override
      public T getData() {
         return data;
      }
   }

   private interface Node<R> {
      R getData();
   }

   private Map<T, GraphNode> dataNodeMap = new HashMap<>();
}
