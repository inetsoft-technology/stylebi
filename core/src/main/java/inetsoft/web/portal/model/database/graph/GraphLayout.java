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
package inetsoft.web.portal.model.database.graph;

import inetsoft.web.portal.model.database.*;
import org.springframework.util.CollectionUtils;

import java.awt.*;
import java.awt.geom.Dimension2D;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static inetsoft.web.portal.model.database.JoinGraphModel.SCALE_Y;

public class GraphLayout {
   public GraphLayout(GraphViewModel graphModel, boolean colPriority) {
      this.colPriority = colPriority;
      this.graphModel = graphModel;
   }

   /**
    * auto layout.
    * @return Suggest Scale. Auto layout may be cause table moving to outside the viewport
    */
   public void layout() {
      List<GraphModel> graphs = graphModel.getGraphs();

      if(CollectionUtils.isEmpty(graphs)) {
         return;
      }

      int viewport = getGraphWidth();
      int maxColumnCount = Math.round(viewport / GRAPH_DEFAULT_COLUMN_WIDTH);
      maxColumnCount = Math.min(maxColumnCount, 5); // < 5 for designer compatibility

      // Save each node and all its joined nodes (including in/out)
      Map<GraphModel, List<GraphModel>> relmap = new HashMap<>();
      GraphModel rootTbl = null; // node with most edges
      int rootCnt = 0; // Maximum number of join connections

      for(int i = graphs.size() - 1; i >= 0; i--) {
         GraphModel currentGraph = graphs.get(i);
         GraphEdgeModel edge = currentGraph.getEdge();
         List<NodeConnectionInfo> input = edge.getInput();
         List<NodeConnectionInfo> output = edge.getOutput();

         List<GraphModel> connectGraphs = Stream.concat(input.stream().map(NodeConnectionInfo::getId),
                                                        output.stream().map(connInfo -> connInfo.getJoinModel().getForeignTable()))
            .map(graphModel::findGraphModel)
            .filter(graph -> graph != null)
            .collect(Collectors.toList());

         relmap.put(currentGraph, connectGraphs);

         if(connectGraphs.size() > rootCnt) {
            rootTbl = currentGraph;
            rootCnt = connectGraphs.size();
         }
      }

      // build the vector of all nodes
      List<List<GraphModel>> elements = new ArrayList<>();
      List<GraphModel> col1 = new ArrayList<>(graphs);
      elements.add(col1);

      // layout the nodes by:
      // 1. The one node with the most edges is placed in the first column
      // 2. All nodes connected to by that node are added to the 2nd col
      // 3. All nodes connected to nodes in the 2nd col are added to 3rd
      //    ...
      // n. All nodes with no edges are added to the columns with the
      //    least number of nodes
      List<List<GraphModel>> nelements = new ArrayList<>();
      GraphModel gelem = extractElement(rootTbl, elements);

      if(rootTbl != null && gelem != null) {
         List<GraphModel> col = new ArrayList<>();

         nelements.add(col);
         setLocation(gelem, new Point(-1, -1));
         col.add(gelem);

         while(addNextColumn(col, nelements, elements, relmap)) {
            col = nelements.get(nelements.size() - 1);
         }
      }

      // copy the orphan elements to the nelements
      for(int i = 0; i < elements.size(); i++) {
         List<GraphModel> col = elements.get(i);

         for(int j = 0; j < col.size(); j++) {
            GraphModel elem = col.get(j);
            List<GraphModel> shortest = null;

            if(nelements.size() < maxColumnCount) {
               nelements.add(new ArrayList<>());
            }

            // find the shortest column
            for(int k = 0; k < nelements.size(); k++) {
               List<GraphModel> scol = nelements.get(k);
               long scolSize = scol.size() + scol.stream()
                  .flatMap(g -> g.getCols().stream())
                  .count();

               long shortestSize = shortest == null ? Integer.MAX_VALUE :
                  shortest.size() + shortest.stream()
                     .flatMap(g -> g.getCols().stream())
                     .count();

               if(scolSize < shortestSize) {
                  shortest = scol;
               }
            }

            if(shortest == null) {
               nelements.add(shortest = new ArrayList<>());
            }

            setLocation(elem, new Point(-1, -1));
            shortest.add(elem);
         }
      }

      nelements = organizeColumns(graphModel, nelements);
      nelements = moveSimpleNodesToFirstColumn(nelements);

      doLayout(nelements);

      moveExtendToBaseBottom();
   }

   private void doLayout(List<List<GraphModel>> cols) {
      // 1. analytic data struct
      shrinkElements(cols);
      int maxRowDepth = 0, maxColDepth = 0;
      int maxRowDepthIndex = 0, maxColDepthIndex = 0;

      for(int i = 0; i < cols.size(); i++) {
         if(cols.get(i).size() > maxRowDepth) {
            maxRowDepth = cols.get(i).size();
            maxRowDepthIndex = i;
         }
         else if(cols.get(i).size() == maxRowDepth) {
            long oldMaxDepth = sumWidth(cols.get(maxRowDepthIndex));
            long newMaxDepth = sumWidth(cols.get(i));

            if(newMaxDepth > oldMaxDepth) {
               maxRowDepth = cols.get(i).size();
               maxRowDepthIndex = i;
            }
         }

         if(cols.get(i).size() > maxColDepth) {
            maxColDepth = cols.get(i).size();
            maxColDepthIndex = i;
         }
      }

      // 2. select layout method
      if(colPriority && maxColDepth > cols.size()) {
         colPriority = false;
      }

      // 3. do layout and calc width of viewport
      if(!colPriority) {
         doRowPriorityLayout(cols, maxRowDepthIndex);

         return;
      }

      doColPriorityLayout(cols, maxColDepthIndex);
   }

   private long sumWidth(List<GraphModel> list) {
      return list.stream().map(GraphModel::getBounds)
         .map(bounds -> bounds.width > DEFAULT_GRAPH_LEFT_GAP ? bounds.width : DEFAULT_GRAPH_LEFT_GAP)
         .collect(Collectors.summarizingInt(Integer::intValue)).getSum();
   }

   private void doRowPriorityLayout(List<List<GraphModel>> rows,
                                    final int maxDepthIndex)
   {
      List<GraphModel> standardRow = rows.get(maxDepthIndex);
      final int maxColCount = standardRow.size();
      final long maxRowWidth = sumWidth(standardRow) + (long) (maxColCount - 1) * ROW_PRIORITY_NODE_X_GAP;
      double top = DEFAULT_GRAPH_TOP_GAP;

      for(int r = 0; r < rows.size(); r++) {
         List<GraphModel> row = rows.get(r);
         int currentColCount = row.size();
         double left = ROW_PRIORITY_LEFT_GAP;

         if(row != standardRow) {
            long currentColWidth = sumWidth(row) + (long) (currentColCount - 1) * ROW_PRIORITY_NODE_X_GAP;
            left = (maxRowWidth - currentColWidth) * 1.0 / 2 + ROW_PRIORITY_LEFT_GAP;
         }

         for(int c = 0; c < row.size(); c++) {
            GraphModel node = row.get(c);
            Point pt = node.getBounds().getLocation();
            Dimension2D d = node.getBounds().getSize();
            double nodeWidth = d.getWidth() < DEFAULT_GRAPH_LEFT_GAP
               ? GRAPH_DEFAULT_COLUMN_WIDTH : d.getWidth();

            if(pt.getX() < 0D && pt.getY() < 0D) {
               setLocation(node, new Point((int) Math.round(left), (int) Math.round(top)));
            }

            left = left + nodeWidth + ROW_PRIORITY_NODE_X_GAP;
         }

         top += ROW_PRIORITY_NODE_Y_GAP + PORTAL_GRAPH_NODE_HEIGHT;
      }
   }

   private List<List<GraphModel>> shrinkElements(List<List<GraphModel>> elements) {
      Set<String> ids = new HashSet<>();

      for(List<GraphModel> element : elements) {
         Iterator<GraphModel> it = element.iterator();

         while(it.hasNext()) {
            GraphModel node = it.next();

            if(!ids.contains(node.getNode().getId())) {
               ids.add(node.getNode().getId());
            }
            else {
               it.remove();
            }
         }
      }

      return elements;
   }

   private void doColPriorityLayout(List<List<GraphModel>> cols, int maxDepthIndex) {
      List<GraphModel> standardCol = cols.get(maxDepthIndex);
      final int maxColCount = standardCol.size();

      // calc a suitable top gap
      final double maxColHeight = calcColHeight(maxColCount);
      int viewportHeight = getGraphHeight();
      int topGap = calcSuitableStartPoint(maxColHeight, viewportHeight) - COL_PRIORITY_TOP_ADJUST;

      // when exist base view, do not center in viewport.
      if(topGap < DEFAULT_GRAPH_TOP_GAP || this.baseViewMaxTop > 0) {
         topGap = DEFAULT_GRAPH_TOP_GAP;
      }

      double x = DEFAULT_GRAPH_LEFT_GAP;

      for(int i = 0; i < cols.size(); i++) {
         double y = topGap, colw = 0D;
         List<GraphModel> column = cols.get(i);

         // Calculate a suitable starting point
         long colHeight = calcColHeight(column.size());
         y += calcSuitableStartPoint(colHeight, maxColHeight);

         for(int j = 0; j < column.size(); j++) {
            GraphModel elem = column.get(j);
            Point pt = elem.getBounds().getLocation();
            Dimension2D d = elem.getBounds().getSize();
            double nodeWidth = d.getWidth() < DEFAULT_GRAPH_LEFT_GAP
               ? GRAPH_DEFAULT_COLUMN_WIDTH : d.getWidth();

            if(pt.getX() < 0D && pt.getY() < 0D) {
               setLocation(elem, new Point((int) Math.round(x), (int) Math.round(y)));
            }

            y += PORTAL_GRAPH_NODE_HEIGHT + COL_PRIORITY_NODE_Y_GAP;
            colw = Math.max(colw, nodeWidth);
         }

         x += colw + COL_PRIORITY_NODE_X_GAP;
      }
   }

   private long calcColHeight(int colCount) {
      // note scale
      return Math.round(PORTAL_GRAPH_NODE_HEIGHT
                           + (colCount - 1) * (PORTAL_GRAPH_NODE_HEIGHT + COL_PRIORITY_NODE_Y_GAP) * SCALE_Y);
   }

   private int calcSuitableStartPoint(double inner, double outer) {
      return (int) Math.round((outer - inner) * 1.0d / 2 / SCALE_Y);
   }

   /**
    * Move the extend view to the bottom of the base view
    */
   private void moveExtendToBaseBottom() {
      if(baseViewMaxTop > 0) {
         baseViewMaxTop += PORTAL_GRAPH_NODE_HEIGHT;
         List<GraphModel> graphs = graphModel.getGraphs();
         Rectangle bounds;

         for(GraphModel graph : graphs) {
            bounds = graph.getBounds().getBounds();
            bounds.y += baseViewMaxTop;
            setLocation(graph, new Point(bounds.x, bounds.y));
         }
      }
   }

   private List<List<GraphModel>> moveSimpleNodesToFirstColumn(
      List<List<GraphModel>> nelements)
   {
      if(nelements.size() > 1) {
         List<List<GraphModel >> newColumns = new ArrayList<>();
         List<GraphModel > newFirstColumn = new ArrayList<>();
         List<GraphModel > secondColumn = new ArrayList<>();

         secondColumn.addAll(nelements.get(1));

         // Iterate through second column and find simple edges
         for(GraphModel elem : secondColumn) {
            if((elem.getEdge().getInput().size() +
               elem.getEdge().getOutput().size()) == 1)
            {
               newFirstColumn.add(elem);
            }
         }

         if(newFirstColumn.size() > 0) {
            // Remove the references from the second column
            // and then add the rest of the original columns.
            secondColumn.removeAll(newFirstColumn);
            newColumns.add(newFirstColumn);
            newColumns.add(nelements.get(0));
            newColumns.add(secondColumn);

            for(int i = 2; i < nelements.size(); i++) {
               newColumns.add(nelements.get(i));
            }

            return newColumns;
         } else {
            return nelements;
         }
      }

      return nelements;
   }

   private List<List<GraphModel>> organizeColumns(GraphViewModel graphModel,
                                                  List<List<GraphModel>> nelements)
   {
      List<List<GraphModel>> newColumns = new ArrayList<>();
      List<GraphModel> traverseColumn = null;   // a pointer to the next column

      if(nelements.size() <= 1) {
         return nelements;
      }

      // Iterate through columns. Each iteration processes the ith column
      // while arranging the i+1 column. The next iteration then uses the
      // arranged column instead of the original arrangement.
      for(int i = 0; i < nelements.size() - 1; i++) {
         List<GraphModel> nextOldColumn = nelements.get(i + 1);

         // The first time, use the original order of the first column,
         // subsequent iterations should use the new order.
         if(traverseColumn == null) {
            traverseColumn = nelements.get(i);
            newColumns.add(traverseColumn);
         }

         List<GraphModel> nextNewColumn = new ArrayList<>();
         newColumns.add(nextNewColumn);

         // Iterate through elements in the column
         for(int j = 0; j < traverseColumn.size(); j++) {
            GraphModel elem = traverseColumn.get(j);

            // Get all ports and edges
            List<GraphColumnInfo> ports = elem.getCols();
            Collection<NodeConnectionInfo> edges = new ArrayList<>();
            edges.addAll(elem.getEdge().getInput());
            edges.addAll(elem.getEdge().getOutput());

            // Iterate the ports in natural order, and find edges that are
            // connected to it. When we find a matching edge, then add it
            // to the new list.
            for(GraphColumnInfo port : ports) {
               List<GraphModel> portNodes = new ArrayList<>();

               for(NodeConnectionInfo edge : edges) {
                  boolean useHead = !elem.equals(graphModel.findGraphModel(edge.getId()));
                  GraphColumnInfo matchPort = (useHead)
                     ? elem.findColumnInfo(edge.getJoinModel().getForeignColumn())
                     : elem.findColumnInfo(edge.getJoinModel().getColumn());

                  if(matchPort != null && matchPort.equals(port)) {
                     GraphModel otherVertex = (useHead)
                        ? graphModel.findGraphModel(edge.getId())
                        : graphModel.findGraphModel(edge.getJoinModel().getForeignTable());

                     if(!nextOldColumn.contains(otherVertex)) {
                        continue;
                     }

                     portNodes.add(otherVertex);
                  }
               }

               // Prioritize nodes that have more connections per port.
               Collections.sort(portNodes,(o1, o2) -> {
                  int c1 = o1.getEdge().getInput().size() +
                     o1.getEdge().getOutput().size();
                  int c2 = o2.getEdge().getInput().size() +
                     o2.getEdge().getOutput().size();

                  return c2 - c1;
               });

               nextNewColumn.addAll(portNodes);
            }
         }

         // Add any remaining nodes
         for(int j = 0; j < nextOldColumn.size(); j++) {
            GraphModel elem = nextOldColumn.get(j);
            if(!nextNewColumn.contains(elem)) {
               nextNewColumn.add(elem);
            }
         }

         // Next iteration should use the newly arranged column structure.
         traverseColumn = nextNewColumn;
      }

      return newColumns;
   }

   /**
    * Add all the nodes connected from col to the next column.
    * The node is deleted from oelements and added to the new column in nelements
    *
    * @param col       the current column
    * @param nelements the new set of columns.
    * @param oelements the old set of columns.
    * @param relmap    the adjacency list for the graph.
    *
    * @return <tt>true</tt> if a column was added.
    */
   private boolean addNextColumn(List<GraphModel> col,
                                 List<List<GraphModel>> nelements,
                                 List<List<GraphModel>> oelements,
                                 Map<GraphModel, List<GraphModel>> relmap)
   {
      boolean added = false;
      List<GraphModel> nextcol = new ArrayList<>();

      for(int i = 0; i < col.size(); i++) {
         GraphModel gelem = col.get(i);
         List<GraphModel> targets = relmap.get(gelem);

         if(targets == null) {
            continue;
         }

         for(int j = 0; j < targets.size(); j++) {
            GraphModel target = targets.get(j);
            GraphModel elem2 = extractElement(target, oelements);

            if(elem2 != null) {
               added = true;
               nextcol.add(elem2);
               setLocation(elem2, new Point(-1, -1));
            }
         }
      }

      if(added) {
         nelements.add(nextcol);
      }

      return added;
   }

   /**
    * Modify the position of node
    */
   private void setLocation(GraphModel graph, Point location) {
      graph.getBounds().setLocation(location);

      String tableName = graph.getNode().getName();
      Rectangle bounds = getBounds(tableName);
      bounds.setLocation(location);
      // bounds do not using <tt>graph.getBounds()</tt>,
      // because height already has changed to portal height(PORTAL_GRAPH_NODE_HEIGHT).
      setBounds(tableName, bounds, true);

      if(graph.isAutoAlias() || graph.isAutoAliasByOutgoing()) {
         String realTableName = getRealTableName(graph);
         setBounds(realTableName, bounds.getBounds(), false);
      }
   }

   private String getRealTableName(GraphModel graph) {
      if(graph.isAutoAlias()) {
         return graph.getNode().getAliasSource();
      }
      else if(graph.isAutoAliasByOutgoing()) {
         if(graph.getOutgoingAutoAliasSource() != null) {
            return graph.getOutgoingAutoAliasSource();
         }

         return graph.getNode().getOutgoingAliasSource();
      }

      return graph.getNode().getName();
   }

   /**
    * Find the first set containing <tt>node</tt> from elements and remove
    * @param node Elements to be removed
    * @param elements Traversed collection
    * @return The removed <tt>GraphModel</tt>
    * <tt>null</tt> means <tt>elements</tt> did not find <tt>node</tt>
    */
   private GraphModel extractElement(GraphModel node, List<List<GraphModel>> elements) {
      for(int i = 0; i < elements.size(); i++) {
         List<GraphModel> col = elements.get(i);
         int index = col.indexOf(node);

         if(index >= 0) {
            return col.remove(index);
         }
      }

      return null;
   }

   public int getGraphWidth() {
      return 800;
   }

   public int getGraphHeight() {
      return 800;
   }

   public Rectangle getBounds(String name) {
      Rectangle rectangle = boundsMap.get(name);

      if(rectangle == null) {
         return new Rectangle(0, 0, GRAPH_DEFAULT_COLUMN_WIDTH, PORTAL_GRAPH_NODE_HEIGHT);
      }

      return rectangle;
   }

   public void setBounds(String name, Rectangle bounds, boolean runtime) {
      boundsMap.put(name, bounds);
   }

   private final Map<String, Rectangle> boundsMap = new HashMap<>();

   protected int baseViewMaxTop;
   protected GraphViewModel graphModel;
   protected boolean colPriority;

   protected static final int ROW_PRIORITY_NODE_X_GAP = 45;
   protected static final int ROW_PRIORITY_NODE_Y_GAP = 30;
   protected static final int ROW_PRIORITY_LEFT_GAP = 30;

   protected static final int COL_PRIORITY_TOP_ADJUST = 20;
   public static final double COL_PRIORITY_NODE_X_GAP = 30D;
   public static final double COL_PRIORITY_NODE_Y_GAP = 60D;

   public static final int GRAPH_DEFAULT_COLUMN_WIDTH = 220;
   public static final int PORTAL_GRAPH_NODE_HEIGHT = 26;
   public static final int PORTAL_ADD_NODE_TOP_GAP = 15;
   public static final int DEFAULT_GRAPH_LEFT_GAP = 10;
   public static final int DEFAULT_GRAPH_TOP_GAP = 10;

   public static final int DEFAULT_VIEWPORT_WIDTH = 800;
   public static final int DEFAULT_VIEWPORT_HEIGHT = 600;
}
