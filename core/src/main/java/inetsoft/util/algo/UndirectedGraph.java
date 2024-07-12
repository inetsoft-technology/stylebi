/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.util.algo;

import java.util.*;
import java.util.stream.Collectors;

/**
 * An implementation of an undirected graph to find clusters of nodes.
 *
 * @param <T> the identifier of the graph. Expected to implement equals and hashcode.
 * @since 13.1
 */
public class UndirectedGraph<T> {
   /**
    * Add edges between the identifiers, creating new nodes if none exist.
    */
   public void addCluster(Set<T> identifiers) {
      final List<Node<T>> nodes = identifiers.stream()
         .map((id) -> this.nodes.computeIfAbsent(id, Node::new))
         .collect(Collectors.toList());

      for(int i = 0; i < nodes.size() - 1; i++) {
         final Node<T> baseNode = nodes.get(i);

         for(int j = i + 1; j < nodes.size(); j++) {
            final Node<T> neighborNode = nodes.get(j);
            baseNode.getNeighbors().add(neighborNode);
            neighborNode.getNeighbors().add(baseNode);
         }
      }
   }

   /**
    * Get the node cluster containing the identifier by doing breadth-first search through
    * neighboring nodes.
    *
    * @param identifier the identifier to find the cluster of.
    *
    * @return the identifiers in the cluster.
    */
   public Set<T> getCluster(T identifier) {
      final Node<T> baseNode = nodes.get(identifier);

      if(baseNode == null) {
         return new HashSet<>();
      }

      final Set<Node<T>> visited = new HashSet<>();
      visited.add(baseNode);
      final Set<Node<T>> frontier = new LinkedHashSet<>(baseNode.getNeighbors());

      while(!frontier.isEmpty()) {
         final Node<T> next = frontier.iterator().next();
         frontier.remove(next);
         visited.add(next);
         next.getNeighbors().stream()
            .filter((n) -> !visited.contains(n))
            .forEach(frontier::add);
      }

      return visited.stream()
         .map(Node::getData)
         .collect(Collectors.toSet());
   }

   private static class Node<T> {
      public Node(T data) {
         this.data = data;
         this.neighbors = new HashSet<>();
      }

      public T getData() {
         return data;
      }

      public Set<Node<T>> getNeighbors() {
         return neighbors;
      }

      @Override
      public boolean equals(Object o) {
         if(this == o) {
            return true;
         }

         if(o == null || getClass() != o.getClass()) {
            return false;
         }

         Node<?> node = (Node<?>) o;
         return Objects.equals(data, node.data);
      }

      @Override
      public int hashCode() {
         return Objects.hash(data);
      }

      private final T data;
      private final Set<Node<T>> neighbors;
   }

   private final Map<T, Node<T>> nodes = new HashMap<>();
}
