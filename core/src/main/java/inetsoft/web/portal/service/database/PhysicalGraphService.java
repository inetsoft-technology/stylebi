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
      List<GraphBoundsInfo> graphs = buildGraphBoundsInfos(partition);
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
    * {@link #unfoldNoTopMiddleNodes}
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
    * Modify the position of node
    */
   private void setLocation(GraphBoundsInfo graph, Point location, XPartition partition) {
      graph.getBounds().setLocation(location);

      String tableName = graph.getTableName();
      partition.getBounds(tableName).setLocation(location);
   }

   /**
    * Unfold column height for portal to studio.
    * {@link #shrinkColumnHeight}
    */
   public void unfoldColumnHeight(RuntimePartitionService.RuntimeXPartition runtimeXPartition,
                                  XPartition originalPartition)
   {
      XPartition newPartition = runtimeXPartition.getPartition();
      Set<String> movedTables = runtimeXPartition.getMovedTables();
      final XPartition currentLayout = (XPartition) newPartition.deepClone(true);

      // 1. build graph tables and sort tables
      List<GraphBoundsInfo> graphs = buildGraphBoundsInfos(newPartition);
      List<GraphBoundsInfo> viewLayout = Tool.deepCloneCollection(graphs);

      // 2. process unfold tables
      for(int i = 0; i < graphs.size(); i++) {
         processBottomTables(graphs, newPartition, currentLayout, i, MOVE_INVALID);
      }

      // 3. process nodes that no top node and locate at middle level
      unfoldNoTopMiddleNodes(newPartition, graphs, viewLayout, currentLayout);

      // 4. Restore nodes that are not moving after process unfold tables.
      /*
       * This makes no sense, as Node 1 and Node2 still need to move after moving to Target.
       * +--------------+-------------+------------+
       * |              |             |  (Target)  |
       * +--------------+-------------+------------+
       * |     Node     |             |    Node1   |
       * +--------------+-------------+------------+
       * |              |             |    Node2   |
       * +--------------+-------------+------------+
       */

//      if(originalPartition != null) {
//         Enumeration<XPartition.PartitionTable> tables = newPartition.getTables(true);
//
//         while(tables.hasMoreElements()) {
//            XPartition.PartitionTable partitionTable = tables.nextElement();
//            String tableName = partitionTable.getName();
//
//            if(!movedTables.contains(tableName) && originalPartition.containsTable(tableName)) {
//               newPartition.setBounds(tableName, originalPartition.getBounds(tableName));
//            }
//         }
//      }

      // 5. Remove the moved flag of the nodes
      runtimeXPartition.clearMovedTables();
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
    *
    * When Node1 and Node2 has been unfold(move down to Node1(moved)/Node2(moved)),
    * Although movedTable does not have top node,
    * it should at least stay above Node1(moved) and Node2(moved) to (Target)
    *
    * +--------------+------------+--------------+
    * |  OtherNode   |            |  OtherNode   |
    * +--------------+------------+--------------+
    * |              | movedTable |              |
    * +--------------+------------+--------------+
    * |    Node1     |            |    Node2     |
    * +--------------+------------+--------------+
    * |              |  (Target)  |              |
    * +--------------+------------+--------------+
    * | Node1(moved) |            | Node2(moved) |
    * +--------------+------------+--------------+
    *
    * {@link #shrinkNoTopMiddleNodes}
    */
   private void unfoldNoTopMiddleNodes(XPartition newPartition,
                                       List<GraphBoundsInfo> graphs,
                                       List<GraphBoundsInfo> viewLayout,
                                       XPartition newCurrentLayout)
   {
      int moveSpace;

      for(int i = 0; i < viewLayout.size(); i++) {
         Integer top = findTop(viewLayout, i, null);

         if(top != null) {
            continue;
         }

         GraphBoundsInfo graphBoundsInfo = viewLayout.get(i);
         Rectangle movedTableCurrentBounds = graphBoundsInfo.getBounds();
         GraphBoundsInfo oldGraphBoundsInfo = viewLayout.get(i);
         Rectangle oldMovedTableBounds = oldGraphBoundsInfo.getBounds().getBounds();

         Rectangle intersect = new Rectangle(
            0, 0, Integer.MAX_VALUE, movedTableCurrentBounds.y);

         List<Integer> bottoms = shrinkIntersectGraph(viewLayout, i, intersect,
            (bounds, newlyBounds) -> (bounds.y > oldMovedTableBounds.y)
               && (newlyBounds == null || bounds.y < newlyBounds.y)
         );

         if(CollectionUtils.isEmpty(bottoms)) {
            continue;
         }

         Integer refNodeIndex = bottoms.get(0);
         GraphBoundsInfo newRefNode = graphs.get(refNodeIndex);
         GraphBoundsInfo oldRefNode = viewLayout.get(refNodeIndex);
         int space = oldRefNode.getBounds().y - movedTableCurrentBounds.y;

         if(space < 0) {
            continue;
         }

         space -= PORTAL_GRAPH_NODE_HEIGHT;
         int y = newRefNode.getBounds().y - space - PORTAL_GRAPH_NODE_HEIGHT;

         if(space < 0) {
            // mixed
            int refHeight = oldRefNode.getBounds().height;
            int mixedSpace = (int) Math.round(Math.abs(space) * 1.0d / PORTAL_GRAPH_NODE_HEIGHT
               * refHeight);
            y = newRefNode.getBounds().y - (refHeight - mixedSpace);
         }

         moveSpace = y - movedTableCurrentBounds.y;

         if(moveSpace <= 0) {
            continue;
         }

         processBottomTables(graphs, newPartition, newCurrentLayout, i, moveSpace);

         setLocation(graphBoundsInfo,
            new Point(movedTableCurrentBounds.x, movedTableCurrentBounds.y + moveSpace),
            newPartition);
      }
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
      result.sort((node1, node2) -> {
         Rectangle bounds1 = node1.getBounds();
         Rectangle bounds2 = node2.getBounds();

         if(bounds1.y == bounds2.y) {
            return bounds1.x - bounds2.x;
         }
         else {
            return bounds1.y - bounds2.y;
         }
      });

      return result;
   }

   @FunctionalInterface
   interface IntersectErrorHandler {
      void handle();
   }

   private static final int MOVE_INVALID = -1;

   private static final Logger LOGGER = LoggerFactory.getLogger(PhysicalGraphService.class);
}
