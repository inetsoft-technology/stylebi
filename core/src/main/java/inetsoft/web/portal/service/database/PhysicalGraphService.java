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
package inetsoft.web.portal.service.database;

import inetsoft.uql.erm.XPartition;
import inetsoft.util.Tool;
import inetsoft.web.portal.controller.database.RuntimePartitionService;
import inetsoft.web.portal.model.database.graph.GraphBoundsInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import static inetsoft.web.portal.model.database.JoinGraphModel.SCALE_Y;
import static inetsoft.web.portal.model.database.graph.PhysicalGraphLayout.PORTAL_GRAPH_NODE_HEIGHT;

@Service
public class PhysicalGraphService {

   /**
    * Independently handle the nodes of extend and base view.
    * when the extend node is in the middle of base nodes, the layout will be messed up
    */
   public void shrinkColumnHeight(RuntimePartitionService.RuntimeXPartition runtimeXPartition) {
      XPartition partition = runtimeXPartition.getPartition();

      if(partition.getBasePartition() != null) {
         shrinkColumnHeight(partition.getBasePartition());
      }

      shrinkColumnHeight(partition);
   }

   /**
    * Shrink column height for studio to portal.
    * {@link #unfoldColumnHeight}
    */
   public void shrinkColumnHeight(XPartition partition) {
      // 1. build graph tables and sort tables
      shrinkColumnHeight(buildGraphBoundsInfos(partition), partition);
   }

   /**
    * Shrink the stored layout held in <tt>graphs</tt> (sorted by y and x).
    * @param partition the partition to update, or {@code null} to only update <tt>graphs</tt>.
    */
   private void shrinkColumnHeight(List<GraphBoundsInfo> graphs, XPartition partition) {
      List<GraphBoundsInfo> viewLayout = Tool.deepCloneCollection(graphs);

      // 2. process shrink
      for(int i = 0; i < graphs.size(); i++) {
         doShrinkColumnHeight(graphs, viewLayout, i, partition, MOVE_INVALID);
      }

      // 3. process nodes that no top node and locate at middle level
      shrinkNoTopMiddleNodes(partition, graphs, viewLayout);
   }

   /**
    * from bottom to top
    */
   private void doShrinkColumnHeight(List<GraphBoundsInfo> graphs,
                                     List<GraphBoundsInfo> intersectView,
                                     int index,
                                     XPartition partition,
                                     int moveSpace)
   {
      if(index < 0 || index >= graphs.size() || moveSpace == 0) {
         return;
      }

      GraphBoundsInfo moveNode = graphs.get(index);
      Rectangle moveNodeBounds = moveNode.getBounds();

      if(moveSpace == MOVE_INVALID) {
         moveSpace = moveNodeBounds.height - PORTAL_GRAPH_NODE_HEIGHT;
      }

      List<Integer> bottomNodes = shrinkIntersect(intersectView, index);

      // process bottom nodes
      if(CollectionUtils.isEmpty(bottomNodes)) {
         return;
      }

      for(Integer bottom : bottomNodes) {
         GraphBoundsInfo bottomNode = graphs.get(bottom);

         // extend shouldn't move base
         if(!moveNode.isBase() && bottomNode.isBase()) {
            continue;
         }

         moveGraph(graphs, intersectView, bottom, partition, moveSpace);
      }
   }

   private void moveGraph(List<GraphBoundsInfo> graphs,
                          List<GraphBoundsInfo> intersectView,
                          int bottomNodeIndex,
                          XPartition partition, int moveHeight)
   {
      GraphBoundsInfo bottomNode = graphs.get(bottomNodeIndex);
      Point newLocation = new Point(bottomNode.getBounds().x,
         bottomNode.getBounds().y - moveHeight);

      // check/move associated bottom node
      doShrinkColumnHeight(graphs, intersectView, bottomNodeIndex, partition, moveHeight);

      // move node
      setLocation(bottomNode, newLocation, partition);
   }

   /**
    *
    * When Node1 and Node2 has been shrink(move up to Node1(moved)/Node2(moved)),
    * Although movedTable does not have top node,
    * it should at least stay above Node1(moved) and Node2(moved)
    *
    * +--------------+------------+--------------+
    * |  OtherNode   |            |  OtherNode   |
    * +--------------+------------+--------------+
    * | Node1(moved) |            | Node2(moved) |
    * +--------------+------------+--------------+
    * |              | movedTable |              |
    * +--------------+------------+--------------+
    * |    Node1     |            |    Node2     |
    * +--------------+------------+--------------+
    *
    * A node with no top and nothing below it keeps its distance to the lowest node above it,
    * so it moves up as far as that node did.
    *
    * {@link #unfoldNoTopNodes}
    */
   private void shrinkNoTopMiddleNodes(XPartition partition,
                                       List<GraphBoundsInfo> graphs,
                                       List<GraphBoundsInfo> viewLayout)
   {
      int moveSpace;

      for(int i = 0; i < viewLayout.size(); i++) {
         Integer top = findTop(viewLayout, i, null);

         if(top != null) {
            continue;
         }

         GraphBoundsInfo oldGraphBoundsInfo = viewLayout.get(i);
         Rectangle oldMovedTableBounds = oldGraphBoundsInfo.getBounds().getBounds();
         GraphBoundsInfo graphBoundsInfo = graphs.get(i);
         Rectangle movedTableBounds = graphBoundsInfo.getBounds().getBounds();

         Rectangle intersect = new Rectangle(
            0, oldMovedTableBounds.y, Integer.MAX_VALUE, Integer.MAX_VALUE);

         List<Integer> bottoms = shrinkIntersectGraph(viewLayout, i, intersect,
            (bounds, newlyBounds) -> (bounds.y > oldMovedTableBounds.y)
               && (newlyBounds == null || bounds.y < newlyBounds.y)
         );

         if(CollectionUtils.isEmpty(bottoms)) {
            shrinkBottomNode(partition, graphs, viewLayout, i);
            continue;
         }

         Integer refNodeIndex = bottoms.get(0);
         GraphBoundsInfo newRefNode = graphs.get(refNodeIndex);
         GraphBoundsInfo oldRefNode = viewLayout.get(refNodeIndex);

         int oldSpace = oldRefNode.getBounds().y - oldMovedTableBounds.y;

         if(oldSpace <= 0) {
            continue;
         }

         oldSpace -= oldMovedTableBounds.height;
         int y = newRefNode.getBounds().y - oldSpace - PORTAL_GRAPH_NODE_HEIGHT;

         if(oldSpace < 0) {
            // mixed
            int mixedSpace = (int) Math.round(Math.abs(oldSpace) * 1.0d / oldMovedTableBounds.height
               * PORTAL_GRAPH_NODE_HEIGHT);
            y = newRefNode.getBounds().y - (PORTAL_GRAPH_NODE_HEIGHT - mixedSpace);
         }

         int space = movedTableBounds.y - y;

         moveSpace = Math.max(space, 0);

         if(movedTableBounds.y - moveSpace > 0 && movedTableBounds.y > newRefNode.getBounds().y) {
            doShrinkColumnHeight(graphs, viewLayout, i, partition, moveSpace);

            setLocation(graphBoundsInfo,
               new Point(movedTableBounds.x, movedTableBounds.y - moveSpace),
               partition);
         }
      }
   }

   /**
    * Move a node that has no top and no node below it up by the distance the lowest node
    * above it moved, so it keeps its place below that node.
    */
   private void shrinkBottomNode(XPartition partition, List<GraphBoundsInfo> graphs,
                                 List<GraphBoundsInfo> viewLayout, int index)
   {
      int y = viewLayout.get(index).getBounds().y;
      Integer above = null;

      for(int i = 0; i < viewLayout.size(); i++) {
         int aboveY = viewLayout.get(i).getBounds().y;

         if(i != index && aboveY < y &&
            (above == null || aboveY > viewLayout.get(above).getBounds().y))
         {
            above = i;
         }
      }

      if(above == null) {
         return;
      }

      int shift = viewLayout.get(above).getBounds().y - graphs.get(above).getBounds().y;
      Rectangle bounds = graphs.get(index).getBounds();

      if(shift > 0 && bounds.y - shift > 0) {
         setLocation(graphs.get(index), new Point(bounds.x, bounds.y - shift), partition);
      }
   }

   /**
    * Modify the position of node
    */
   private void setLocation(GraphBoundsInfo graph, Point location, XPartition partition) {
      graph.getBounds().setLocation(location);

      if(partition != null) {
         partition.getBounds(graph.getTableName()).setLocation(location);
      }
   }

   /**
    * Unfold column height for portal to studio.
    * {@link #shrinkColumnHeight}
    *
    * @param originalPartition the stored layout the view was opened from, or {@code null}.
    */
   public void unfoldColumnHeight(RuntimePartitionService.RuntimeXPartition runtimeXPartition,
                                  XPartition originalPartition)
   {
      XPartition newPartition = runtimeXPartition.getPartition();

      // 1. a layout that still opens where it is shown keeps its stored positions
      if(!restoreOriginalLayout(newPartition, originalPartition)) {
         final XPartition currentLayout = (XPartition) newPartition.deepClone(true);

         // 2. build graph tables and sort tables
         List<GraphBoundsInfo> graphs = buildGraphBoundsInfos(newPartition);
         List<GraphBoundsInfo> viewLayout = Tool.deepCloneCollection(graphs);

         // 3. process unfold tables
         for(int i = 0; i < graphs.size(); i++) {
            processBottomTables(graphs, newPartition, currentLayout, i, MOVE_INVALID);
         }

         // 4. place nodes that no top node so that they open where they are shown
         unfoldNoTopNodes(newPartition, graphs, viewLayout, currentLayout);
      }

      // 5. Remove the moved flag of the nodes
      runtimeXPartition.clearMovedTables();
   }

   /**
    * Keep the original stored layout when the shown layout is exactly what it opens to, so
    * saving a view without moving its tables doesn't change the stored layout.
    */
   private boolean restoreOriginalLayout(XPartition partition, XPartition originalPartition) {
      if(originalPartition == null) {
         return false;
      }

      List<GraphBoundsInfo> shown = buildGraphBoundsInfos(partition);
      List<GraphBoundsInfo> stored = buildGraphBoundsInfos(originalPartition);

      if(shown.size() != stored.size()) {
         return false;
      }

      int[] opened = simulateShrink(stored);
      Map<String, Integer> openedY = new HashMap<>();

      for(int i = 0; i < stored.size(); i++) {
         openedY.put(stored.get(i).getTableName(), opened[i]);
      }

      for(GraphBoundsInfo node : shown) {
         Integer y = openedY.get(node.getTableName());
         Rectangle bounds = node.getBounds();
         Rectangle original = y == null ? null : originalPartition.getBounds(node.getTableName());

         if(original == null || y != bounds.y || original.x != bounds.x ||
            original.width != bounds.width || original.height != bounds.height)
         {
            return false;
         }
      }

      for(GraphBoundsInfo node : shown) {
         Rectangle original = originalPartition.getBounds(node.getTableName());
         partition.getBounds(node.getTableName()).setLocation(original.x, original.y);
      }

      return true;
   }

   public List<String> getTableNames(XPartition partition) {
      Enumeration<XPartition.PartitionTable> tables = partition.getTables(true);
      List<String> names = new ArrayList<>();

      while(tables.hasMoreElements()) {
         names.add(tables.nextElement().getName());
      }

      return names;
   }

   private int portalToRealSpace(int bottom, int top) {
      return (int) Math.round((SCALE_Y * (bottom - top) - PORTAL_GRAPH_NODE_HEIGHT)
         * 1.0 / SCALE_Y);
   }

   /**
    * determine the impact on the below node, move the below node
    * @param newPartition will saved partition
    * @param currentLayout portal view partition
    * @param movedTableIndex processing table index
    * @param moveSpace move space
    */
   private void processBottomTables(List<GraphBoundsInfo> graphs, XPartition newPartition,
                                    XPartition currentLayout,
                                    int movedTableIndex, int moveSpace)
   {
      if(movedTableIndex < 0 || moveSpace == 0) {
         return;
      }

      GraphBoundsInfo graphBoundsInfo = graphs.get(movedTableIndex);
      Rectangle movedTableCurrentBounds = graphBoundsInfo.getBounds();

      if(MOVE_INVALID == moveSpace) {
         moveSpace = movedTableCurrentBounds.height - PORTAL_GRAPH_NODE_HEIGHT;
      }

      List<XPartition.PartitionTable> bottoms = unfoldDoubleCheckIntersect(currentLayout,
         graphBoundsInfo.getTableName());

      for(XPartition.PartitionTable bottomTable : bottoms) {
         // 1. Calculate the space between the current node and bottom under current layout
         String bottomTableName = bottomTable.getName();

         if(!graphBoundsInfo.isBase() && newPartition.isBaseTable(bottomTableName)) {
            continue;
         }

         // 2. Then determine the location of the bottom
         int bottomTableIndex = findNodeIndex(graphs, bottomTableName);
         GraphBoundsInfo bottomNodeInfo = bottomTableIndex < 0 ? null
            : graphs.get(bottomTableIndex);

         if(bottomNodeInfo == null) {
            LOGGER.warn("Can't find bottom table({}).", bottomTableName);

            return;
         }

         Rectangle bottomBounds = bottomNodeInfo.getBounds().getBounds();
         bottomBounds.y += moveSpace;

         // 3. next level bottoms
         processBottomTables(graphs, newPartition, currentLayout,
            bottomTableIndex, moveSpace);

         // 4. change bottom node;
         setLocation(bottomNodeInfo, new Point(bottomBounds.x, bottomBounds.y), newPartition);
      }
   }

   /**
    * Nodes without a top node keep their portal y when unfolded, but shrinking moves such a
    * node relative to the nearest node below it (any column, see
    * {@link #shrinkNoTopMiddleNodes}), or relative to the lowest node above it when nothing
    * is below it. So after the other nodes are unfolded, a node with no top may no longer open
    * where it is shown.
    *
    * For each such node that would open somewhere else, try the stored positions that
    * shrinking maps back to its portal y, check each by running the real shrink, and keep the
    * best one that brings the node closer while no node opens further from where it is shown
    * and no new overlap appears. Nodes without a top that the move pushes away get one chance
    * to be placed again in the same step.
    */
   private void unfoldNoTopNodes(XPartition newPartition,
                                 List<GraphBoundsInfo> graphs,
                                 List<GraphBoundsInfo> viewLayout,
                                 XPartition currentLayout)
   {
      int count = graphs.size();
      int[] target = new int[count];

      for(int i = 0; i < count; i++) {
         target[i] = viewLayout.get(i).getBounds().y;
      }

      int[] opened = simulateShrink(graphs);

      if(Arrays.equals(opened, target)) {
         return;
      }

      List<Integer> roots = new ArrayList<>();
      Map<Integer, Set<Integer>> subtrees = new HashMap<>();

      for(int i = 0; i < count; i++) {
         if(findTop(viewLayout, i, null) == null) {
            roots.add(i);
            subtrees.put(i, unfoldSubtree(graphs, currentLayout, i));
         }
      }

      // each simulation costs about count^2, keep the search bounded for large views
      int budget = Math.max(MIN_SEARCH_SHRINKS, SEARCH_WORK / Math.max(1, count * count));
      int[] used = { 0 };

      for(int pass = 0; pass < MAX_SEARCH_PASSES && used[0] < budget; pass++) {
         boolean improved = false;

         for(int i : roots) {
            if(opened[i] == target[i] || used[0] >= budget) {
               continue;
            }

            List<int[]> bestMoves = null;
            int[] bestOpened = null;
            long bestError = error(opened, target);
            int overlaps = countOverlaps(graphs, opened, target);

            for(int y : candidates(i, graphs, opened, target, subtrees)) {
               if(used[0] >= budget) {
                  break;
               }

               List<int[]> moves = new ArrayList<>();
               moveRoot(graphs, subtrees, i, y - graphs.get(i).getBounds().y, moves, null);
               int[] result = simulateShrink(graphs);
               used[0]++;

               // let no-top nodes that were pushed away by this move be placed again
               for(int other : roots) {
                  if(other != i && distance(result, target, other) > distance(opened, target, other)) {
                     result = replace(other, graphs, opened, result, target, subtrees, moves, used);
                  }
               }

               long error = error(result, target);

               if(error < bestError && distance(result, target, i) < distance(opened, target, i) &&
                  noneFurther(opened, result, target) &&
                  countOverlaps(graphs, result, target) <= overlaps)
               {
                  bestError = error;
                  bestOpened = result;
                  bestMoves = new ArrayList<>(moves);
               }

               for(int k = moves.size() - 1; k >= 0; k--) {
                  moveRoot(graphs, subtrees, moves.get(k)[0], -moves.get(k)[1], null, null);
               }
            }

            if(bestMoves != null) {
               for(int[] move : bestMoves) {
                  moveRoot(graphs, subtrees, move[0], move[1], null, newPartition);
               }

               opened = bestOpened;
               improved = true;
            }
         }

         if(!improved) {
            break;
         }
      }
   }

   /**
    * Place no-top node <tt>index</tt> again after another node moved it away from its portal
    * y. The best position is applied to <tt>graphs</tt> and logged in <tt>moves</tt>.
    * @return the opened layout after the move, or <tt>result</tt> if nothing helps.
    */
   private int[] replace(int index, List<GraphBoundsInfo> graphs, int[] opened, int[] result,
                         int[] target, Map<Integer, Set<Integer>> subtrees, List<int[]> moves,
                         int[] used)
   {
      int y0 = graphs.get(index).getBounds().y;
      Integer bestY = null;
      int[] best = result;
      long bestError = Long.MAX_VALUE;

      for(int y : candidates(index, graphs, result, target, subtrees)) {
         moveRoot(graphs, subtrees, index, y - y0, null, null);
         int[] trial = simulateShrink(graphs);
         used[0]++;
         moveRoot(graphs, subtrees, index, y0 - y, null, null);
         long error = error(trial, target);

         if(distance(trial, target, index) <= distance(opened, target, index) &&
            error < bestError)
         {
            bestY = y;
            best = trial;
            bestError = error;
         }
      }

      if(bestY != null) {
         moveRoot(graphs, subtrees, index, bestY - y0, moves, null);
      }

      return best;
   }

   /**
    * Stored y candidates for no-top node <tt>index</tt>: the inverse of each shrink rule
    * (middle node below a reference node, bottom node below the lowest node), kept only
    * when the rule would really apply to that position.
    */
   private Set<Integer> candidates(int index, List<GraphBoundsInfo> graphs, int[] opened,
                                   int[] target, Map<Integer, Set<Integer>> subtrees)
   {
      Set<Integer> subtree = subtrees.get(index);
      Rectangle bounds = graphs.get(index).getBounds();
      int portalY = target[index];
      int height = bounds.height;
      TreeSet<Integer> result = new TreeSet<>();
      Integer lowest = null;
      Integer lowestAbove = null;
      result.add(portalY);

      for(int i = 0; i < graphs.size(); i++) {
         if(i == index || subtree.contains(i)) {
            continue;
         }

         int y = graphs.get(i).getBounds().y;
         int shift = y - opened[i];

         if(lowest == null || y > graphs.get(lowest).getBounds().y) {
            lowest = i;
         }

         if(target[i] < portalY &&
            (lowestAbove == null || y > graphs.get(lowestAbove).getBounds().y))
         {
            lowestAbove = i;
         }

         // middle node with reference i: keep the space to i
         addMiddleCandidate(result, portalY + shift - (height - PORTAL_GRAPH_NODE_HEIGHT),
            i, index, graphs);

         // middle node overlapping reference i ("mixed")
         double mixed = y - height + (portalY - opened[i] + PORTAL_GRAPH_NODE_HEIGHT) *
            (double) height / PORTAL_GRAPH_NODE_HEIGHT;

         for(int k = -1; k <= 2; k++) {
            addMiddleCandidate(result, (int) Math.floor(mixed) + k, i, index, graphs);
         }
      }

      // bottom node: keep the space to the lowest node
      if(lowest != null && subtree.isEmpty()) {
         int lowestY = graphs.get(lowest).getBounds().y;
         int y = portalY + lowestY - opened[lowest];

         if(y > lowestY) {
            result.add(y);
         }
      }

      // just below the lowest stored node of the nodes shown above it
      if(lowestAbove != null) {
         result.add(graphs.get(lowestAbove).getBounds().y + 1);
      }

      result.remove(bounds.y);
      return result.tailSet(portalY, true);
   }

   /**
    * Add <tt>y</tt> if node <tt>ref</tt> would be the nearest node below a node stored at y.
    */
   private void addMiddleCandidate(Set<Integer> result, int y, int ref, int index,
                                   List<GraphBoundsInfo> graphs)
   {
      int refY = graphs.get(ref).getBounds().y;

      if(y >= refY) {
         return;
      }

      int delta = y - graphs.get(index).getBounds().y;

      for(int i = 0; i < graphs.size(); i++) {
         if(i == index || i == ref) {
            continue;
         }

         int other = graphs.get(i).getBounds().y;

         // nodes below the moved node in its own column move with it
         if(other > graphs.get(index).getBounds().y && other + delta > y && other + delta < refY) {
            return;
         }

         if(other > y && other < refY) {
            return;
         }
      }

      result.add(y);
   }

   private void moveRoot(List<GraphBoundsInfo> graphs, Map<Integer, Set<Integer>> subtrees,
                         int root, int delta, List<int[]> log, XPartition partition)
   {
      if(delta == 0) {
         return;
      }

      List<Integer> nodes = new ArrayList<>(subtrees.get(root));
      nodes.add(root);

      for(int i : nodes) {
         Rectangle bounds = graphs.get(i).getBounds();
         setLocation(graphs.get(i), new Point(bounds.x, bounds.y + delta), partition);
      }

      if(log != null) {
         log.add(new int[] { root, delta });
      }
   }

   /**
    * The nodes processBottomTables moves together with node <tt>index</tt>.
    */
   private Set<Integer> unfoldSubtree(List<GraphBoundsInfo> graphs, XPartition currentLayout,
                                      int index)
   {
      Set<Integer> result = new HashSet<>();
      Deque<Integer> queue = new ArrayDeque<>();
      queue.add(index);

      while(!queue.isEmpty()) {
         GraphBoundsInfo node = graphs.get(queue.poll());

         for(XPartition.PartitionTable bottom :
            unfoldDoubleCheckIntersect(currentLayout, node.getTableName()))
         {
            if(!node.isBase() && currentLayout.isBaseTable(bottom.getName())) {
               continue;
            }

            int bottomIndex = findNodeIndex(graphs, bottom.getName());

            if(bottomIndex >= 0 && result.add(bottomIndex)) {
               queue.add(bottomIndex);
            }
         }
      }

      return result;
   }

   /**
    * Run the real shrink on a copy of a stored layout.
    * @return the y each node of <tt>graphs</tt> opens at.
    */
   private int[] simulateShrink(List<GraphBoundsInfo> graphs) {
      List<GraphBoundsInfo> copy = Tool.deepCloneCollection(graphs);
      copy.sort(NODE_ORDER);
      shrinkColumnHeight(copy, null);
      Map<String, Integer> ys = new HashMap<>();

      for(GraphBoundsInfo node : copy) {
         ys.put(node.getTableName(), node.getBounds().y);
      }

      int[] result = new int[graphs.size()];

      for(int i = 0; i < result.length; i++) {
         result[i] = ys.get(graphs.get(i).getTableName());
      }

      return result;
   }

   private static int distance(int[] opened, int[] target, int index) {
      return Math.abs(opened[index] - target[index]);
   }

   /**
    * Largest distance first, then total distance.
    */
   private static long error(int[] opened, int[] target) {
      long max = 0;
      long sum = 0;

      for(int i = 0; i < opened.length; i++) {
         max = Math.max(max, distance(opened, target, i));
         sum += distance(opened, target, i);
      }

      return max * 1_000_000_000L + sum;
   }

   private static boolean noneFurther(int[] before, int[] after, int[] target) {
      for(int i = 0; i < before.length; i++) {
         if(distance(after, target, i) > distance(before, target, i)) {
            return false;
         }
      }

      return true;
   }

   /**
    * Number of node pairs that overlap when opened but not where they are shown.
    */
   private static int countOverlaps(List<GraphBoundsInfo> graphs, int[] opened, int[] target) {
      int count = 0;

      for(int i = 0; i < opened.length; i++) {
         Rectangle a = graphs.get(i).getBounds();

         for(int j = i + 1; j < opened.length; j++) {
            Rectangle b = graphs.get(j).getBounds();

            if(a.x < b.x + b.width && b.x < a.x + a.width &&
               Math.abs(opened[i] - opened[j]) < PORTAL_GRAPH_NODE_HEIGHT &&
               Math.abs(target[i] - target[j]) >= PORTAL_GRAPH_NODE_HEIGHT)
            {
               count++;
            }
         }
      }

      return count;
   }

   private int findNodeIndex(List<GraphBoundsInfo> graphs, String tableName) {
      for(int i = 0; i < graphs.size(); i++) {
         if(graphs.get(i).getTableName().equals(tableName)) {
            return i;
         }
      }

      return -1;
   }

   /**
    * Double check intersect nodes for shrink column.
    * @param graphs graph nodes
    * @param index check node index
    * @return intersect bottom index list.
    */
   private List<Integer> shrinkIntersect(List<GraphBoundsInfo> graphs, int index) {
      GraphBoundsInfo graph = graphs.get(index);
      Rectangle currentBounds = graph.getBounds().getBounds();
      Rectangle intersect = new Rectangle(currentBounds.x,
         currentBounds.y, currentBounds.width, Integer.MAX_VALUE);

      // Double check 1: top (1)--->(n) bottoms, find all bottom
      List<Integer> bottoms = shrinkIntersectGraph(graphs, index, intersect,
         (bounds, newlyBounds) -> bounds.y > currentBounds.y);

      Iterator<Integer> iterator = bottoms.iterator();

      while(iterator.hasNext()) {
         Integer bottom = iterator.next();

         // Double check 2: bottom (1)--->(1) top, find a top
         Integer top = findTop(graphs, bottom, () ->
            LOGGER.warn("This node({}) find bottom nodes({}), but bottom({}) can't find top node.",
               graph.getTableName(),
               bottoms.stream()
                  .map(graphs::get)
                  .map(GraphBoundsInfo::getTableName)
                  .collect(Collectors.joining(", ")),
               graphs.get(bottom).getTableName())
         );

         // Double check failed: block moving
         if(top == null || !top.equals(index)) {
            iterator.remove();
         }
      }

      return bottoms;
   }

   private Integer findTop(List<GraphBoundsInfo> graphs,
                           Integer bottom,
                           IntersectErrorHandler errorHandler)
   {
      Rectangle bottomBounds = graphs.get(bottom).getBounds();
      Rectangle toTopIntersect = new Rectangle(bottomBounds.x,
         0, bottomBounds.width, bottomBounds.y);

      List<Integer> tops = shrinkIntersectGraph(graphs, bottom, toTopIntersect,
         (bounds, newlyBounds) -> (bounds.y <= bottomBounds.y)
            && (newlyBounds == null || bounds.y > newlyBounds.y));

      if(tops.size() == 0 && errorHandler != null) {
         errorHandler.handle();
      }

      return tops.size() > 0 ? tops.get(0) : null;
   }

   /**
    * Bounds intersect.
    * @param graphs graphs
    * @param index current graph index
    * @param intersect range
    * @param condition Additional condition
    */
   private List<Integer> shrinkIntersectGraph(List<GraphBoundsInfo> graphs,
                                              int index,
                                              Rectangle intersect,
                                              BiFunction<Rectangle, Rectangle, Boolean> condition)
   {
      List<Integer> result = new ArrayList<>();

      for(int i = 0; i < graphs.size(); i++) {
         if(i == index) {
            // skip self
            continue;
         }

         Rectangle bounds = graphs.get(i).getBounds();
         Rectangle newlyBounds = null;

         if(result.size() > 0) {
            newlyBounds = graphs.get(result.get(0)).getBounds();
         }

         if(intersect.intersects(bounds) && condition.apply(bounds, newlyBounds)) {
            result.add(0, i);
         }
      }

      return result;
   }

   /**
    * Double check intersect nodes for unfold column.
    * @param partition graph nodes
    * @param currentTableName check node index
    * @return tables intersect with <tt>currentTableName</tt>
    */
   private List<XPartition.PartitionTable> unfoldDoubleCheckIntersect(XPartition partition,
                                                                      String currentTableName)
   {
      Rectangle currentBounds = partition.getBounds(currentTableName);
      Rectangle intersect = new Rectangle(currentBounds.x, currentBounds.y,
         currentBounds.width, Integer.MAX_VALUE);

      // Double check 1: top (1)--->(n) bottoms, find all bottom
      List<XPartition.PartitionTable> bottoms = unfoldIntersect(partition, currentTableName,
         intersect, (bounds, newlyBounds) -> bounds.y >= currentBounds.y);

      Iterator<XPartition.PartitionTable> iterator = bottoms.iterator();

      while(iterator.hasNext()) {
         XPartition.PartitionTable bottom = iterator.next();
         String bottomName = bottom.getName();

         // Double check 2: bottom (1)--->(1) top, find a top
         Rectangle bottomBounds = partition.getBounds(bottomName).getBounds();
         Rectangle toTopIntersect = new Rectangle(bottomBounds.x, 0, bottomBounds.width,
            bottomBounds.y);
         List<XPartition.PartitionTable> list = unfoldIntersect(partition, bottomName,
            toTopIntersect, (bounds, newlyBounds) -> (bounds.y <= bottomBounds.y) &&
               (newlyBounds == null || bounds.y > newlyBounds.y));
         XPartition.PartitionTable top = list.size() > 0 ? list.get(0) : null;

         if(top == null) {
            LOGGER.warn("This node({}) find bottom nodes({}), but bottom({}) can't "
               + "find top node in unfolding.",
               currentTableName,
               bottoms.stream().map(XPartition.PartitionTable::getName)
                  .collect(Collectors.joining(", ")),
               bottomName);
         }

         // Double check failed: block moving
         if(top == null || !top.getName().equals(currentTableName)) {
            iterator.remove();
         }
      }

      return bottoms;
   }

   /**
    * Bounds intersect.
    * @param condition Additional condition
    */
   private List<XPartition.PartitionTable> unfoldIntersect(XPartition partition,
                                                           String currentName, Rectangle intersect,
                                                           BiFunction<Rectangle, Rectangle, Boolean> condition)
   {
      List<XPartition.PartitionTable> result = new ArrayList<>();

      Enumeration<XPartition.PartitionTable> tables = partition.getTables(true);

      while(tables.hasMoreElements()) {
         XPartition.PartitionTable partitionTable = tables.nextElement();

         if(partitionTable.getName().equals(currentName)) {
            // skip self
            continue;
         }

         Rectangle bounds = partition.getBounds(partitionTable).getBounds();
         Rectangle newlyBounds = null;

         if(result.size() > 0) {
            newlyBounds = partition.getBounds(result.get(0)).getBounds();
         }

         if(intersect.intersects(bounds) && condition.apply(bounds, newlyBounds)) {
            result.add(0, partitionTable);
         }
      }

      return result;
   }

   private List<GraphBoundsInfo> buildGraphBoundsInfos(XPartition partition) {
      return buildGraphBoundsInfos(partition, true);
   }

   /**
    * Build a graphInfos for graph layout.
    * @param partition partition
    * @return GraphBoundsInfo list
    */
   private List<GraphBoundsInfo> buildGraphBoundsInfos(XPartition partition, boolean self) {
      List<GraphBoundsInfo> result = new ArrayList<>();
      Enumeration<XPartition.PartitionTable> tables = partition.getTables(self);

      while(tables.hasMoreElements()) {
         XPartition.PartitionTable partitionTable = tables.nextElement();
         String tableName = partitionTable.getName();
         GraphBoundsInfo info = new GraphBoundsInfo();
         info.setTableName(tableName);
         info.setBounds(partition.getBounds(partitionTable).getBounds());
         // TODO fix isBase attr
         info.setBase(partition.isBaseTable(tableName));
         result.add(info);
      }

      // sort by y and x.
      result.sort(NODE_ORDER);

      return result;
   }

   @FunctionalInterface
   interface IntersectErrorHandler {
      void handle();
   }

   private static final Comparator<GraphBoundsInfo> NODE_ORDER = (node1, node2) -> {
      Rectangle bounds1 = node1.getBounds();
      Rectangle bounds2 = node2.getBounds();

      if(bounds1.y == bounds2.y) {
         return bounds1.x - bounds2.x;
      }
      else {
         return bounds1.y - bounds2.y;
      }
   };

   private static final int MOVE_INVALID = -1;
   private static final int MAX_SEARCH_PASSES = 3;
   private static final int MIN_SEARCH_SHRINKS = 20;
   // simulated shrinks * node count^2 allowed for one unfold
   private static final int SEARCH_WORK = 8_000_000;

   private static final Logger LOGGER = LoggerFactory.getLogger(PhysicalGraphService.class);
}
