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
package inetsoft.graph.element;

import inetsoft.graph.GGraph;
import inetsoft.graph.GraphConstants;
import inetsoft.graph.aesthetic.*;
import inetsoft.graph.data.*;
import inetsoft.graph.geometry.*;
import inetsoft.graph.internal.GTool;
import inetsoft.graph.mxgraph.layout.hierarchical.mxHierarchicalLayout;
import inetsoft.graph.mxgraph.layout.*;
import inetsoft.graph.mxgraph.model.mxCell;
import inetsoft.graph.mxgraph.view.mxGraph;
import inetsoft.graph.visual.ElementVO;
import inetsoft.graph.visual.VOText;
import inetsoft.sree.SreeEnv;
import inetsoft.util.CoreTool;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.text.Format;
import java.util.List;
import java.util.*;

/**
 * This defines a relation chart element.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public class RelationElement extends GraphElement {
   /**
    * Relation layout algorithms.
    */
   public enum Algorithm {
      HIERARCHICAL, CIRCLE, COMPACT_TREE, EDGE_LABEL,
      FAST_ORGANIC, ORGANIC, PARALLEL_EDGE, PARTITION, STACK
   }

   /**
    * Define an empty relation. Source and target dimensions must be set.
    */
   public RelationElement() {
   }

   /**
    * Create a relation with specified tree dimensions.
    */
   public RelationElement(String fromDim, String toDim) {
      this.fromDim = fromDim;
      this.toDim = toDim;
   }

   /**
    * Get the relation layout algorithm. Defaults to CIRCULAR.
    */
   public Algorithm getAlgorithm() {
      return algorithm;
   }

   /**
    * Set the relation layout algorithm.
    */
   public void setAlgorithm(Algorithm algorithm) {
      this.algorithm = algorithm;
   }

   /**
    * Get the dimension containing the from vertex of connections.
    */
   public String getSourceDim() {
      return fromDim;
   }

   /**
    * Set the dimension containing the from vertex of connections.
    */
   public void setSourceDim(String dim) {
      this.fromDim = dim;
   }

   /**
    * Get the dimension containing the to vertex of connections.
    */
   public String getTargetDim() {
      return toDim;
   }

   /**
    * Set the dimension containing the to vertex of connections.
    */
   public void setTargetDim(String dim) {
      this.toDim = dim;
   }

   @Override
   public void createGeometry(DataSet data0, GGraph graph) {
      VisualModel vmodel = createVisualModel(data0);
      SortedDataSet sdata = sortData(data0, graph);

      if(sdata != null) {
         data0 = getSortedDataSetInRange(data0, sdata);
         // values must be sorted for the treemap chart to render properly
         sdata.setForceSort(true);
      }

      if(fromDim == null || toDim == null) {
         return;
      }

      DataSet data = data0;
      int max = getEndRow(data);
      String sizeField = vmodel != null ? vmodel.getSizeFrame().getField() : null;
      Map<Object, RelationGeometry> nodes = new HashMap<>();
      List<RelationEdgeGeometry> edges = new ArrayList<>();
      boolean overlay = getHint("overlay") != null;
      mxGraph mxgraph = new mxGraph();
      final Object mxroot = mxgraph.getDefaultParent();
      final int maxNodes = Integer.parseInt(SreeEnv.getProperty("graph.max.nodes", "1000"));

      // add To nodes
      for(int i = getStartRow(data); i < max; i++) {
         if(!isAccepted(data, i)) {
            continue;
         }

         Object to = data.getData(toDim, i);
         final String id = getId(to, i, data, toDim);
         final int sidx = sdata == null ? i : sdata.getBaseRow(i);
         RelationGeometry toNode = nodes.get(id);

         if(nodes.containsKey(id)) {
            toNode.setMultiParent(true);
            continue;
         }

         if(sizeField != null && (overlay || sizeField.startsWith(ElementVO.ALL_PREFIX)) &&
            data.getData(sizeField, i) == null)
         {
            continue;
         }

         toNode = createGeometry(id, mxgraph, graph, vmodel, to, i, sidx, data, toDim);
         nodes.put(id, toNode);
         graph.addGeometry(toNode);

         setNodeSize(toNode, toNode.getSize());

         if(nodes.size() > maxNodes) {
            showMaxWarning(maxNodes);
            break;
         }
      }

      // layout the areas
      // overlay area gets size/position from underlying element so don't need to laid out
      if(!overlay) {
         List<RelationGeometry> roots = new ArrayList<>();
         boolean staticSize = getSizeFrame() instanceof StaticSizeFrame;
         Set<String> edgeIds = new HashSet<>();

         // add from nodes and edges
         for(int i = getStartRow(data); i < max; i++) {
            if(!isAccepted(data, i)) {
               continue;
            }

            Object from = data.getData(fromDim, i);
            Object to = data.getData(toDim, i);
            final String fromId = getId(from, i, data, fromDim);
            final String toId = getId(to, i, data, toDim);
            final int sidx = sdata == null ? i : sdata.getBaseRow(i);
            String edgeId = fromId + "-" + toId;

            RelationGeometry fromNode = nodes.get(fromId);

            if(fromNode == null) {
               if(nodes.size() > maxNodes * 2) {
                  showMaxWarning(maxNodes);
                  continue;
               }

               fromNode = createGeometry(fromId, mxgraph, graph, vmodel, from, i, sidx,
                                         data, fromDim);
               roots.add(fromNode);
               nodes.put(fromId, fromNode);

               // dynamic size only applied to child
               if(staticSize) {
                  setNodeSize(fromNode, fromNode.getSize());
               }

               graph.addGeometry(fromNode);
            }

            // having bi-directional edges causes the graph to be messed up. (56755)
            if(edgeIds.contains(edgeId) || edgeIds.contains(toId + "-" + fromId)) {
               continue;
            }

            RelationGeometry toNode = nodes.get(toId);

            if(toNode != null) {
               RelationEdgeGeometry edgeGeometry = createEdgeGeometry(
                  graph, vmodel, data, mxgraph, mxroot, i, sidx, edgeId,
                  fromNode, toNode);
               graph.addGeometry(edgeGeometry);
               edges.add(edgeGeometry);
               edgeIds.add(edgeId);
            }
         }

         // if size is bound, the size of roots are not set since there is no value to calculate
         // a meaningful size. leaving it at default may look odd. set use the average size
         // of children in this case, which may be somewhat meaningful and aesthetically
         // more pleasing.
         if(!staticSize) {
            roots.stream().forEach(r -> r.getMxCell().setAutoSize());
         }

         prepareLayout(graph);

         // if a forest, create a fake root and add the root of individual trees to the fake root.
         // this is needed since the tree layouts don't work with forest.
         if(roots.size() > 1) {
            mxCell root0 = (mxCell) mxgraph.insertVertex(mxroot, "__graph_forest_root__", "root",
                                                         0, 0, nodeWidth, nodeHeight);

            for(RelationGeometry fromNode : roots) {
               mxgraph.insertEdge(mxroot, "__graph_forest_child__" + fromNode.getMxCell().getId(),
                                  null, root0, fromNode.getMxCell());
            }
         }

         mxLayout(nodes, edges, mxgraph, mxroot, graph);
      }
   }

   private void showMaxWarning(int maxNodes) {
      String msg = GTool.getString("viewer.viewsheet.chart.maxNodes", maxNodes);
      CoreTool.addUserMessage(msg);
   }

   private void mxLayout(Map<Object, RelationGeometry> nodes, List<RelationEdgeGeometry> edges,
                         mxGraph mxgraph, Object mxroot, GGraph ggraph)
   {
      mxGraphLayout layout0;
      boolean scalePositions = true;
      int layoutW = layoutSize.width, layoutH = layoutSize.height;

      // leave space for label since mxLayout is not aware of labels and the labels are
      // laid out later.
      if(getTextFrame() != null && !isLabelInside()) {
         layoutW -= 20;
         layoutH -= GTool.getFontMetrics(getTextSpec().getFont()).getHeight() * 2;
      }

      switch(algorithm) {
      case CIRCLE: {
         mxCircleLayout layout = new mxCircleLayout(mxgraph);
         double maxNodeSize = nodes.values().stream()
            .mapToDouble(n -> Math.max(n.getMxCell().getGeometry().getWidth(),
                                       n.getMxCell().getGeometry().getHeight()))
            .max().orElse(1);
         double minr = Math.min(layoutW * widthRatio, layoutH * heightRatio);
         layout.setRadius((minr - maxNodeSize) * 0.9 / 2);
         //double maxr = Math.max(nodeWidth, nodeHeight);
         //layout.setSizeFactor(Math.max(0.75, 15 * Math.max(widthRatio, heightRatio) / maxr));
         layout0 = layout;
         scalePositions = false;
         break;
      }
      case COMPACT_TREE: {
         mxCompactTreeLayout layout = new mxCompactTreeLayout(mxgraph, false);
         layout.setPrefVertEdgeOff(0);
         layout.setEdgeSpacing(0);
         layout.setNodeDistance(12);
         layout0 = layout;
         scalePositions = false;
         break;
      }
      case EDGE_LABEL: {
         mxEdgeLabelLayout layout = new mxEdgeLabelLayout(mxgraph);
         layout0 = layout;
         break;
      }
      case FAST_ORGANIC: {
         mxFastOrganicLayout layout = new mxFastOrganicLayout(mxgraph);
         layout0 = layout;
         break;
      }
      case ORGANIC: {
         // since the layout doesn't guarantee to place all nodes within bounds, we add
         // a small padding to try to fit all in default size to avoid scrolling.
         double xgap = Math.min(20, layoutW * 0.05);
         double ygap = Math.min(20, layoutH * 0.05);
         mxOrganicLayout layout = new mxOrganicLayout(
            mxgraph, new Rectangle2D.Double(0, 0, layoutW - xgap, layoutH - ygap));
         //layout.setFineTuning(true);
         layout.setTriesPerCell(16);
         //layout.setMaxIterations(2000);
         layout.setRadiusScaleFactor(0.95);
         //layout.setBorderLineCostFactor(10);
         layout.setEdgeLengthCostFactor(0.03);
         double crossingFactor = Math.min(20000, 6000 / (edges.size() / 30.0));
         layout.setOptimizeEdgeCrossing(crossingFactor > 1000);
         layout.setEdgeCrossingCostFactor(crossingFactor);
         layout.setAverageNodeArea(0); // don't apply 'dynamic' sizing
         layout.setOptimizeNodeDistribution(true);
         //layout.setNodeDistributionCostFactor(120000);
         layout0 = layout;
         break;
      }
      case PARALLEL_EDGE: {
         mxParallelEdgeLayout layout = new mxParallelEdgeLayout(mxgraph);
         layout0 = layout;
         break;
      }
      case PARTITION: {
         mxPartitionLayout layout = new mxPartitionLayout(mxgraph);
         layout0 = layout;
         break;
      }
      case STACK: {
         mxStackLayout layout = new mxStackLayout(mxgraph);
         layout0 = layout;
         break;
      }
      case HIERARCHICAL:
      default: {
         mxHierarchicalLayout layout = new mxHierarchicalLayout(mxgraph);
         layout.setInterRankCellSpacing(15);
         layout.setIntraCellSpacing(10);
         layout0 = layout;
         break;
      }
      }

      layout0.setUseBoundingBox(false);
      layout0.execute(mxroot);
      flipY(ggraph);

      if(scalePositions) {
         scaleGeometries(ggraph);
      }

      // some layout places nodes far from the origin. move them to relative to top-left corner.
      double minX = nodes.values().stream()
         .mapToDouble(v -> v.getMxCell().getGeometry().getX()).min().orElse(0);
      double minY = nodes.values().stream()
         .mapToDouble(v -> v.getMxCell().getGeometry().getY()).min().orElse(0);
      nodes.values().stream()
         .forEach(v -> v.getMxCell().getGeometry().translate(-minX, -minY));
      edges.stream()
         .forEach(v -> v.getEdge().getGeometry().translate(-minX, -minY));
   }

   // flip the Y so root is on top.
   // (also mxGraph use top-left as origin while we use bottom-left).
   private void flipY(GGraph graph) {
      if(layoutSize == null) {
         return;
      }

      AffineTransform trans = new AffineTransform();
      trans.scale(1, -1);
      trans.translate(0, -layoutSize.getHeight());

      for(int i = 0; i < graph.getGeometryCount(); i++) {
         Geometry gobj = graph.getGeometry(i);

         if(gobj instanceof RelationGeometry) {
            ((RelationGeometry) gobj).getMxCell().getGeometry().transformPosition(trans);
         }
         else if(gobj instanceof RelationEdgeGeometry) {
            ((RelationEdgeGeometry) gobj).getEdge().getGeometry().transformPosition(trans);
         }
      }
   }

   private RelationEdgeGeometry createEdgeGeometry(
      GGraph graph, VisualModel vmodel, DataSet data, mxGraph mxgraph, Object mxroot,
      int i, int sidx, String edgeId, RelationGeometry fromNode, RelationGeometry toNode)
   {
      RelationEdgeGeometry edgeGeometry = new RelationEdgeGeometry(this, graph, fromDim,
                                                                   sidx, vmodel);

      mxCell edge = (mxCell) mxgraph.insertEdge(mxroot, edgeId, null, fromNode.getMxCell(),
                                                toNode.getMxCell());
      edgeGeometry.setEdge(edge);
      edgeGeometry.setRowIndex(getRootRowIndex(data, i));
      return edgeGeometry;
   }

   private void prepareLayout(GGraph graph) {
      graph.sortGeometries();

      if(!isLabelInside()) {
         for(int i = 0; i < graph.getGeometryCount(); i++) {
            Geometry gobj = graph.getGeometry(i);

            if(gobj instanceof RelationGeometry) {
               setOuterSize((RelationGeometry) gobj);
            }
         }
      }
   }

   // set the outer bounds to consider label overlapping.
   private void setOuterSize(RelationGeometry gobj) {
      VOText vtext = new VOText(gobj.getText(), null, gobj.getVar(), null, -1, gobj);
      Rectangle cellbox = gobj.getMxCell().getGeometry().getRectangle();
      double prefw = vtext.getPreferredWidth();
      double prefh = vtext.getPreferredHeight();

      switch(getLabelPlacement()) {
      case GraphConstants.TOP:
      case GraphConstants.BOTTOM:
         gobj.getMxCell().getGeometry().setOuterWidth(Math.max(prefw, cellbox.width));
         gobj.getMxCell().getGeometry().setOuterHeight(prefh + cellbox.height);
         break;
      case GraphConstants.LEFT:
      case GraphConstants.RIGHT:
         gobj.getMxCell().getGeometry().setOuterWidth(prefw + cellbox.width);
         gobj.getMxCell().getGeometry().setOuterHeight(Math.max(prefh, cellbox.height));
      }
   }

   public boolean isLabelInside() {
      return getLabelPlacement() == GraphConstants.AUTO ||
         getLabelPlacement() == GraphConstants.CENTER;
   }

   private void setNodeSize(RelationGeometry fromNode, double size) {
      if(isLabelInside()) {
         fromNode.setCellSize(nodeWidth * widthRatio + size * 4,
                              nodeHeight * heightRatio + Math.max(0, size - 1) * 3);
      }
      else {
         fromNode.setCellSize(nodeWidth + size, nodeHeight + size);
      }
   }

   private String getId(Object obj, int row, DataSet data, String dim) {
      // differentiate between null and "null"
      if(obj == null) {
         return "null";
      }

      Format fmt = data instanceof AttributeDataSet
         ? ((AttributeDataSet) data).getFormat(dim, row) : null;

      try {
         return (fmt != null ? fmt.format(obj) : obj) + "_id";
      }
      catch(Exception ex) {
         return obj + "_id";
      }
   }

   private RelationGeometry createGeometry(String id, mxGraph mxgraph,
                                           GGraph graph, VisualModel vmodel, Object obj,
                                           int row, int sidx, DataSet data, String dim)
   {
      RelationGeometry gobj = null;

      Object root = mxgraph.getDefaultParent();
      double vertexWidth = isLabelInside() ? this.nodeWidth * widthRatio : this.nodeWidth;
      double vertexHeight = isLabelInside() ? this.nodeHeight * heightRatio : this.nodeHeight;
      mxCell node = (mxCell) mxgraph.insertVertex(root, id, obj, 0, 0, vertexWidth, vertexHeight);
      gobj = new RelationGeometry(this, graph, dim, sidx, vmodel, node);

      gobj.setSubRowIndex(sidx);
      gobj.setRowIndex(getRootRowIndex(data, row));
      gobj.setColIndex(getRootColIndex(data, dim));

      return gobj;
   }

   // sort data by tree dimensions
   @Override
   public SortedDataSet sortData(DataSet data, GGraph graph) {
      SortedDataSet sdata = super.sortData(data, graph);

      if(sdata == null) {
         sdata = createSortedDataSet(data, getSourceDim(), getTargetDim());
      }
      else {
         sdata.addSortColumn(getSourceDim(), false);
         sdata.addSortColumn(getTargetDim(), false);
      }

      return sdata;
   }

   @Override
   public boolean supportsOverlay() {
      return true;
   }

   /**
    * Get the vertex width (without size scale).
    */
   public int getNodeWidth() {
      return nodeWidth;
   }

   /**
    * Set the vertex width (without size scale).
    */
   public void setNodeWidth(int nodeWidth) {
      this.nodeWidth = nodeWidth;
   }

   /**
    * Get the vertex height.
    */
   public int getNodeHeight() {
      return nodeHeight;
   }

   /**
    * Set the vertex height.
    */
   public void setNodeHeight(int nodeHeight) {
      this.nodeHeight = nodeHeight;
   }

   public double getWidthRatio() {
      return widthRatio;
   }

   public void setWidthRatio(double widthRatio) {
      this.widthRatio = widthRatio;
   }

   public double getHeightRatio() {
      return heightRatio;
   }

   public void setHeightRatio(double heightRatio) {
      this.heightRatio = heightRatio;
   }

   /**
    * Set the color frame for getting the color aesthetic for nodes.
    */
   public void setNodeColorFrame(ColorFrame colors) {
      this.nodeColors = colors;
   }

   /**
    * Get the color frame for getting the color aesthetic for nodes.
    */
   public ColorFrame getNodeColorFrame() {
      return nodeColors;
   }

   /**
    * Set the size frame for getting the size aesthetic for nodes.
    */
   public void setNodeSizeFrame(SizeFrame sizes) {
      this.nodeSizes = sizes;
   }

   /**
    * Get the size frame for getting the size aesthetic for nodes.
    */
   public SizeFrame getNodeSizeFrame() {
      return nodeSizes;
   }

   @Override
   public VisualFrame[] getVisualFrames() {
      List<VisualFrame> list = new ArrayList<>();
      list.add(nodeColors);
      list.add(nodeSizes);
      list.addAll(Arrays.asList(super.getVisualFrames()));
      return list.toArray(new VisualFrame[0]);
   }

   /**
    * Set the size to layout graph. It is the 'default' size where the graph will tried to
    * be fitted into. The actual size may be larger or smaller.
    */
   public void setLayoutSize(Dimension size) {
      this.layoutSize = size != null ? size : new Dimension(200, 200);
   }

   /**
    * Get the size to layout graph.
    */
   public Dimension getLayoutSize() {
      return layoutSize;
   }

   /**
    * Get the node fill color.
    */
   public Color getFillColor() {
      return fillColor;
   }

   /**
    * Set the node fill color.
    */
   public void setFillColor(Color fillColor) {
      this.fillColor = fillColor;
   }

   private void scaleGeometries(GGraph graph) {
      AffineTransform trans = AffineTransform.getScaleInstance(widthRatio, heightRatio);

      for(int i = 0; i < graph.getGeometryCount(); i++) {
         Geometry gobj = graph.getGeometry(i);

         if(gobj instanceof RelationGeometry) {
            ((RelationGeometry) gobj).getMxCell().getGeometry().transformPosition(trans);
         }
         else if(gobj instanceof RelationEdgeGeometry) {
            ((RelationEdgeGeometry) gobj).getEdge().getGeometry().transformPosition(trans);
         }
      }
   }

   @Override
   public ColorFrame getSharedColorFrame(VisualFrame frame) {
      // node size should only use node color frame
      if(frame == nodeSizes) {
         ColorFrame edgeColors = getColorFrame();

         // if edge color is defined and is same as node color, don't share the color frame
         // so a color legend will be shown for the edge. (57573)
         if(nodeColors != null && edgeColors != null &&
            nodeColors.getShareId().equals(edgeColors.getShareId()) &&
            edgeColors.getLegendSpec().isVisible())
         {
            return null;
         }

         // don't share color if color is (only) defined for node, and (same) size is defined for
         // both edge and node. (57599)
         if(getSizeFrame() != null && nodeSizes != null &&
            nodeSizes.getField() != null &&
            nodeSizes.getShareId().equals(getSizeFrame().getShareId()) &&
            (getColorFrame() == null || getColorFrame() instanceof StaticColorFrame))
         {
            return null;
         }

         boolean edgeSizeSame = getSizeFrame() != null &&
            getSizeFrame().getShareId().equals(nodeSizes.getShareId());

         // if edge size bound to the same field as node size, we should check for edge
         // size shared color frame if node size frame don't have a shared color. (59817)
         if(!edgeSizeSame) {
            return nodeColors;
         }
      }

      // other (line/size) frames use edge color frame.
      ColorFrame shared = super.getSharedColorFrame(frame);

      // don't share brushing/highlight color frames. (57627)
      if(shared instanceof CompositeColorFrame) {
         return null;
      }

      // if node color is defined and is same as line color, don't share the color frame
      // so a color legend will be shown for the node. (57546)
      if(shared != null && nodeColors != null &&
         shared.getShareId().equals(nodeColors.getShareId()) &&
         nodeColors.getLegendSpec().isVisible())
      {
         return null;
      }

      // don't share color if color is (only) defined for edge, and (same) size is defined for
      // both edge and node. (57525, 57582)
      if(shared != null && getSizeFrame() != null && nodeSizes != null &&
         nodeSizes.getField() != null &&
         nodeSizes.getShareId().equals(getSizeFrame().getShareId()) &&
         (nodeColors == null || nodeColors instanceof StaticColorFrame))
      {
         return null;
      }

      return shared;
   }

   /**
    * Check if aesthetic binding should be applied to source node.
    */
   public boolean isApplyAestheticsToSource() {
      return applyAestheticsToSource;
   }

   /**
    * Set if aesthetic binding should be applied to source node.
    */
   public void setApplyAestheticsToSource(boolean applyAestheticsToSource) {
      this.applyAestheticsToSource = applyAestheticsToSource;
   }

   @Override
   public boolean equalsContent(Object obj) {
      if(!super.equalsContent(obj)) {
         return false;
      }

      if(obj instanceof RelationElement) {
         RelationElement elem = (RelationElement) obj;
         return Objects.equals(fromDim, elem.fromDim) && Objects.equals(toDim, elem.toDim) &&
            this.algorithm == elem.algorithm && widthRatio == elem.widthRatio &&
            heightRatio == elem.heightRatio &&
            applyAestheticsToSource == elem.applyAestheticsToSource &&
            Objects.equals(nodeColors, elem.nodeColors) &&
            Objects.equals(nodeSizes, elem.nodeSizes) &&
            Objects.equals(layoutSize, elem.layoutSize) &&
            Objects.equals(fillColor, elem.fillColor);
      }

      return false;
   }

   /**
    * Default vertex width.
    */
   public static final int VERTEX_WIDTH = 40;
   /**
    * Default vertex height.
    */
   public static final int VERTEX_HEIGHT = 30;

   private int nodeWidth = VERTEX_WIDTH;
   private int nodeHeight = VERTEX_HEIGHT;
   private String fromDim;
   private String toDim;
   private Algorithm algorithm = Algorithm.HIERARCHICAL;
   private double widthRatio = 1;
   private double heightRatio = 1;
   private ColorFrame nodeColors;
   private SizeFrame nodeSizes;
   private Dimension layoutSize = new Dimension(200, 200);
   private Color fillColor;
   private boolean applyAestheticsToSource = false;

   private static final long serialVersionUID = 1L;
}
