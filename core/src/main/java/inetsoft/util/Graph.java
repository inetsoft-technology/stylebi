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
package inetsoft.util;

import java.io.Serializable;
import java.util.*;

/**
 * Implementation of the graph data structure. A graph is a collection of
 * nodes, or vectors, connected by edges. The edges can have weights which
 * can represent distances, times, costs, etc.
 */
public class Graph implements Serializable {
   /**
    * Create a new instance of Graph.
    * @param weighted determines whether the edges in this graph have an
    * associated weight.
    */
   public Graph(boolean weighted) {
      this.weighted = weighted;
      nodes = new Object[0];
      edges = new int[0][0];

      if(weighted) {
         weights = new double[0][0];
      }
   }

   /**
    * Add a node, or vertex, to the graph. This method does not check to see if
    * the node is already present before adding it.
    * @param node the node to be added.
    */
   public void addNode(Object node) {
      // take the performance hit here -- allocating the arrays in this way is
      // more expensive than using a Vector, but will improve performance when
      // searching. Since the typical usage will be to constuct a graph and then
      // perform multiple searches on it, this is the better approach.
      nodes = appendObject(nodes, node);
      edges = appendIntArray(edges, new int[] {});

      if(weighted) {
         weights = appendDoubleArray(weights, new double[] {});
      }
   }

   /**
    * Add a directed edge to this graph. If this graph is weighted, sets the
    * weight of the edge to <code>1.0</code>. If either node has not been added
    * to the graph, that node will be added before the edge is created.
    * @param node1 the node at which the edge starts.
    * @param node2 the node at which the edge ends.
    */
   public void addEdge(Object node1, Object node2) {
      addEdge(node1, node2, 1.0, true);
   }

   /**
    * Add an edge to this graph. If this graph is weighted, sets the weight
    * of the edge to <code>1.0</code>. If either node has not been added to
    * the graph, that node will be added before the edge is created.
    * @param node1 the node at which the edge starts.
    * @param node2 the node at which the edge ends.
    * @param directed <code>true</code> if the edge is directed (one-way).
    */
   public void addEdge(Object node1, Object node2, boolean directed) {
      addEdge(node1, node2, 1.0, directed);
   }

   /**
    * Add a directed edge to this graph. If either node has not been added to
    * the graph, that node will be added before the edge is created.
    * @param node1 the node at which the edge starts.
    * @param node2 the node at which the edge ends.
    * @param weight the weight of the edge. If this graph is not weighted, this
    * parameter is ignored.
    */
   public void addEdge(Object node1, Object node2, double weight) {
      addEdge(node1, node2, weight, true);
   }

   /**
    * Add an edge to this graph. If either node has not been added to the graph,
    * that node will be added before the edge is created.
    * @param node1 the node at which the edge starts.
    * @param node2 the node at which the edge ends.
    * @param weight the weight of the edge. If this graph is not weighted, this
    * parameter is ignored.
    * @param directed <code>true</code> if the edge is directed (one-way).
    */
   public void addEdge(Object node1, Object node2, double weight,
                       boolean directed) {
      int idx1 = indexOf(node1);
      int idx2 = indexOf(node2);

      if(idx1 < 0) {
         addNode(node1);
         idx1 = nodes.length - 1;
      }

      if(idx2 < 0) {
         addNode(node2);
         idx2 = nodes.length - 1;
      }

      edges[idx1] = appendInt(edges[idx1], idx2);

      if(weighted) {
         weights[idx1] = appendDouble(weights[idx1], weight);
      }

      if(!directed) {
         edges[idx2] = appendInt(edges[idx2], idx1);

         if(weighted) {
            weights[idx2] = appendDouble(weights[idx2], weight);
         }
      }
   }

   /**
    * Find all nodes that are neighbors (link directly to) the node.
    */
   public Object[] getNeighbors(Object node) {
      int idx = indexOf(node);

      if(idx < 0) {
         return new Object[] {};
      }

      int[] links = edges[idx];
      ArrayList<Object> vec = new ArrayList<>();

      for(int i = 0; i < links.length; i++) {
         if(links[i] >= 0) {
            vec.add(nodes[links[i]]);
         }
      }

      return vec.toArray();
   }


   /**
    * Finds the shortest path between two nodes by using a breadth-first search.
    * @param node1 the starting node.
    * @param node2 the destination node.
    * @param nodes all nodes that should be included as part of the path.
    * @param distances the distance from the origin to the node for weighted
    * graph. This is an output parameter and is populated on return. List of
    * Double objects.
    * @return an array of Objects containing every node in the shortest path,
    * from the starting node, at position <code>0</code>, to the destination
    * node, at position <code>n</code>. Returns <code>null</code> if no path
    * exists between the nodes.
    */
   public Object[] findPath(Object node1, Object node2, Collection nodes,
                            List distances) {
      return weighted ? findPathWeighted(node1, node2, nodes, distances)
         : findPath0(node1, node2);
   }

   /**
    * Finds the shortest path between two nodes by using a breadth-first search.
    * If this graph is weighted, the weights are ignored and this graph is
    * treated as an unweighted graph for the purpose of the search.
    * @param node1 the starting node.
    * @param node2 the destination node.
    * @return an array of Objects containing every node in the shortest path,
    * from the starting node, at position <code>0</code>, to the destination
    * node, at position <code>n</code>. Returns <code>null</code> if no path
    * exists between the nodes.
    */
   private Object[] findPath0(Object node1, Object node2) {
      int idx1 = indexOf(node1);
      int idx2 = indexOf(node2);

      if(idx1 < 0 || idx2 < 0) {
         return null;
      }

      if(idx1 == idx2) {
         return new Object[] { node1 };
      }

      boolean[] visited = new boolean[nodes.length];
      int[] previous = new int[nodes.length];

      for(int i = 0; i < nodes.length; i++) {
         visited[i] = false;
         previous[i] = -1;
      }

      Queue queue = new Queue();

      visited[idx1] = true;
      queue.enqueue(Integer.valueOf(idx1));

      searchLoop:
      while(!queue.isEmpty()) {
         int head = ((Integer) queue.dequeue()).intValue();
         int[] adjacent = edges[head];

         for(int i = 0; i < adjacent.length; i++) {
            if(!visited[adjacent[i]]) {
               visited[adjacent[i]] = true;
               previous[adjacent[i]] = head;

               if(adjacent[i] == idx2) {
                  break searchLoop;
               }

               queue.enqueue(Integer.valueOf(adjacent[i]));
            }
         }
      }

      if(previous[idx2] < 0) {
         return null;
      }

      int[] path = new int[nodes.length];
      int count = 0;

      path[count++] = idx2;

      for(int i = 0; i < nodes.length; i++, count++) {
         path[count] = previous[path[count - 1]];

         if(path[count] == idx1) {
            break;
         }
      }

      Object[] result = new Object[count + 1];

      for(int i = count, j = 0; j < result.length; i--, j++) {
         result[j] = nodes[path[i]];
      }

      return result;
   }

   /**
    * Finds the shortest path between two nodes by using the Dijkstra algorithm.
    * This works for weighted graph.<br>
    * This is a modified weighted path search. If the pathnodes are supplied,
    * any nodes that are not on the pathnodes are treated as heavier than the
    * heaviest link. In another word, nodes on the pathnodes are treated as
    * having a higher priority than the nodes not on the pathnodes.<br>
    * The modification is necessary for handling weak link. A weak link is
    * heavier than a regular link, but should be lighter than a link that
    * is not part of the selected tables. By introducing the weight modified
    * by pathnodes, the weak link can be handled with regular graph operation
    * instead of requiring special logic outside of the graph.
    * @param node1 the starting node.
    * @param node2 the destination node.
    * @param pathnodes all nodes that should be given a high priority.
    * @param distances the distance from the origin to the nodes. This is an
    * output parameter and is populated on return. Distance as Double.
    * @return an array of Objects containing every node in the shortest path,
    * from the starting node, at position <code>0</code>, to the destination
    * node, at position <code>n</code>. Returns <code>null</code> if no path
    * exists between the nodes.
    */
   private Object[] findPathWeighted(Object node1, Object node2,
                                     Collection pathnodes, List distances) {
      int idx1 = indexOf(node1);
      int idx2 = indexOf(node2);

      if(idx1 < 0 || idx2 < 0) {
         return null;
      }

      if(idx1 == idx2) {
         return new Object[] { node1 };
      }

      BitSet nodeset = new BitSet();
      int[] previous = new int[nodes.length];
      final double[] shortest = new double[nodes.length];

      // build the hi-priority nodes
      if(pathnodes != null) {
         Iterator iter = pathnodes.iterator();

         while(iter.hasNext()) {
            int idx = indexOf(iter.next());

            if(idx >= 0) {
               nodeset.set(idx);
            }
         }
      }

      for(int i = 0; i < nodes.length; i++) {
         previous[i] = -1;
         shortest[i] = Double.MAX_VALUE;
      }

      final Comparator comparator = new Comparator() {
         @Override
         public int compare(Object o1, Object o2) {
            int n1 = ((Integer) o1).intValue();
            int n2 = ((Integer) o2).intValue();

            if(shortest[n1] < shortest[n2]) {
               return -1;
            }
            else if(shortest[n1] > shortest[n2]) {
               return 1;
            }

            return 0;
         }
      };

      Queue queue = new Queue() {
         @Override
         public void enqueue(Object obj) {
            super.enqueue(obj);
            Collections.sort(this, comparator);
         }
      };

      shortest[idx1] = 0;
      queue.enqueue(Integer.valueOf(idx1));

      searchLoop:
      while(!queue.isEmpty()) {
         int head = ((Integer) queue.dequeue()).intValue();
         int[] adjacent = edges[head];

         for(int i = 0; i < adjacent.length; i++) {
            double w = weights[head][i];

            // @by larryl, the priority is such (from highest to lowest)
            // 1. regular link between two selected nodes
            // 2. weak link between two selected nodes
            // 3. regular link between two unselected nodes
            // 4. weak link between two unselected nodes
            if(pathnodes != null) {
               if(nodeset.get(adjacent[i]) && nodeset.get(head)) {
                  // 1 or WEAK_WEIGHT
               }
               else {
                  // 500*WEAK_WEIGHT or 250000*WEAK_WEIGHT
                  if(w >= WEAK_WEIGHT) {
                     w = 250000 * WEAK_WEIGHT;
                  }
                  else {
                     w = 500 * WEAK_WEIGHT;
                  }
               }
            }

            if(shortest[adjacent[i]] > w + shortest[head]) {
               previous[adjacent[i]] = head;
               shortest[adjacent[i]] = w + shortest[head];

               queue.enqueue(Integer.valueOf(adjacent[i]));
            }
         }
      }

      if(previous[idx2] < 0) {
         return null;
      }

      int[] path = new int[nodes.length];
      int count = 0;

      path[count++] = idx2;

      for(int i = 0; i < nodes.length; i++, count++) {
         path[count] = previous[path[count - 1]];

         if(path[count] == idx1) {
            break;
         }
      }

      Object[] result = new Object[count + 1];

      for(int i = count, j = 0; j < result.length; i--, j++) {
         result[j] = nodes[path[i]];
         distances.add(Double.valueOf(shortest[path[i]]));
      }

      return result;
   }

   /**
    * Finds the first cycle in the graph.
    * @param maxweight edges have a weight higher than or equal to the parameter
    * is ignored.
    * @return an array of Objects containing every node in a cycle. Or
    * <tt>null</tt> if no cycle exists in the graph.
    */
   public Object[] findCycle(int maxweight) {
      // @by larryl, for a fully connected graph, we don't need to check for
      // each node when looking for a cycle. But we don't make that assumption
      // here so it's more generic. If the performance becomes a problem, we
      // should consider checking only one node.
      for(int i = 0; i < nodes.length; i++) {
         Object[] cycle = findCycle0(i, maxweight);

         if(cycle != null) {
            return cycle;
         }
      }

      return null;
   }

   /**
    * Finds the first cycle starting for a node.
    * @param idx1 starting node to search for cycle.
    * @param maxweight edges have a weight higher than or equal to the parameter
    * is ignored.
    * @return an array of Objects containing every node in a cycle. Or
    * <tt>null</tt> if no cycle exists in the graph.
    */
   private Object[] findCycle0(int idx1, int maxweight) {
      boolean[] visited = new boolean[nodes.length];
      int[] previous = new int[nodes.length];
      // mark the edge that has been used by setting (idx1 << 16 | idx2)
      BitSet visitedEdge = new BitSet();

      for(int i = 0; i < nodes.length; i++) {
         visited[i] = false;
         previous[i] = -1;
      }

      Queue queue = new Queue();
      int cycleIdx = -1; // index of the last node that forms a cycle

      visited[idx1] = true;
      queue.enqueue(Integer.valueOf(idx1));
      int currentIdx = idx1;

      searchLoop:
      while(!queue.isEmpty()) {
         int head = ((Integer) queue.dequeue()).intValue();
         int[] adjacent = edges[head];
         currentIdx = head;

         for(int i = 0; i < adjacent.length; i++) {
            if(weights[head][i] < maxweight &&
               !visitedEdge.get(head << 16 | adjacent[i]))
            {
               // here we assume the edges are not directed, so if one
               // direction of an edge is used, we ignore the other direction.
               // Otherwise a single edge would always be counted as a cycle
               visitedEdge.set(adjacent[i] << 16 | head);

               if(!visited[adjacent[i]]) {
                  visited[adjacent[i]] = true;
                  previous[adjacent[i]] = head;
                  queue.enqueue(Integer.valueOf(adjacent[i]));
               }
               // if we reach a node that has already been reached, we have
               // found a cycle
               else {
                  cycleIdx = adjacent[i];
                  break searchLoop;
               }
            }
         }
      }

      if(cycleIdx < 0) {
         return null;
      }

      // construct the cycle path
      int idxCycle = cycleIdx;
      BitSet cycleBit = new BitSet();

      while(idxCycle != -1) {
         cycleBit.set(idxCycle);
         idxCycle = previous[idxCycle];
      }

      idxCycle = currentIdx;

      int[] path = new int[nodes.length];
      int count = 0;

      while(idxCycle != -1) {
         path[count ++] = idxCycle;

         if(cycleBit.get(idxCycle)) {
            break;
         }

         idxCycle = previous[idxCycle];
      }

      int[] path2 = new int[nodes.length];
      int count2 = 0;

      while(cycleIdx != -1) {
         path2[count2++] = cycleIdx;

         if(previous[cycleIdx] == idxCycle) {
            break;
         }

         cycleIdx = previous[cycleIdx];
      }

      for(int i = count2 -1; i >=0; i--) {
         path[count++] = path2[i];
      }

      Object[] result = new Object[count];

      for(int i = 0, j = 0; j < result.length; i++, j++) {
         result[j] = nodes[path[i]];
      }

      return result;
   }

   /**
    * Get the index of a node in the internal representation of the graph.
    * @param node the node to find.
    * @return the index of the node, or <code>-1</code> if the node is not in
    * the graph.
    */
   private int indexOf(Object node) {
      for(int i = 0; i < nodes.length; i++) {
         if(nodes[i].equals(node)) {
            return i;
         }
      }

      return -1;
   }

   /**
    * Utility method for appending an Object to an array of Objects.
    * @param oarray the original array to which the element is being appended.
    * @param obj the Object to be appended.
    * @return a new array that contains the original array with the specified
    * element appended.
    */
   private Object[] appendObject(Object[] oarray, Object obj) {
      Object[] result = new Object[oarray.length + 1];

      System.arraycopy(oarray, 0, result, 0, oarray.length);
      result[oarray.length] = obj;
      return result;
   }

   /**
    * Utility method for appending an integer to an array of integers.
    * @param oarray the original array to which the element is being appended.
    * @param n the integer to be appended.
    * @return a new array that contains the original array with the specified
    * element appended.
    */
   private int[] appendInt(int[] oarray, int n) {
      int[] result = new int[oarray.length + 1];

      System.arraycopy(oarray, 0, result, 0, oarray.length);
      result[oarray.length] = n;
      return result;
   }

   /**
    * Utility method for appending an array of integers to a two-dimensional
    * array of integers.
    * @param oarray the original array to which the subarray is being appended.
    * @param n the integer array to be appended.
    * @return a new array that contains the original array with the specified
    * subarray appended.
    */
   private int[][] appendIntArray(int[][] oarray, int[] n) {
      int[][] result = new int[oarray.length + 1][];

      System.arraycopy(oarray, 0, result, 0, oarray.length);
      result[oarray.length] = n;
      return result;
   }

   /**
    * Utility method for appending a double to an array of doubles.
    * @param oarray the original array to which the element is being appended.
    * @param d the double to be appended.
    * @return a new array that contains the original array with the specified
    * element appended .
    */
   private double[] appendDouble(double[] oarray, double d) {
      double[] result = new double[oarray.length + 1];

      System.arraycopy(oarray, 0, result, 0, oarray.length);
      result[oarray.length] = d;
      return result;
   }

   /**
    * Utility method for appending an array of doubles to a two-dimensional
    * array of doubles.
    * @param oarray the original array to which the subarray is being appended.
    * @param d the subarray to be appended.
    * @return a new array that contains the original array with the specified
    * subarray appended.
    */
   private double[][] appendDoubleArray(double[][] oarray, double[] d) {
      double[][] result = new double[oarray.length + 1][];

      System.arraycopy(oarray, 0, result, 0, oarray.length);
      result[oarray.length] = d;
      return result;
   }

   public String toString() {
      StringBuilder buf = new StringBuilder();

      buf.append("Graph: \n");
      buf.append("   Nodes: \n");

      for(int i = 0; i < nodes.length; i++) {
         buf.append("     node[" + i + "]=" + nodes[i] + "\n");
      }

      buf.append("   Edges:");

      for(int i = 0; i < edges.length; i++) {
         for(int j = 0; j < edges[i].length; j++) {
            buf.append("     edge[" + i + "][" + j + "]=" + edges[i][j] + "\n");
         }
      }

      buf.append("   Weights:");

      for(int i = 0; i < weights.length; i++) {
         for(int j = 0; j < weights[i].length; j++) {
            buf.append("     weight[" + i + "][" + j + "]=" + weights[i][j] +
                       "\n");
         }
      }

      buf.append("   Weighted:" + weighted + "\n");

      return buf.toString();
   }

   // weight for weak join in the join graph
   public static final int WEAK_WEIGHT = 500;

   private Object[] nodes;
   private int[][] edges;
   private double[][] weights;
   private boolean weighted;
}
