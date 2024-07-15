/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.graph.element;

import inetsoft.graph.GGraph;
import inetsoft.graph.GraphConstants;
import inetsoft.graph.aesthetic.VisualModel;
import inetsoft.graph.coord.Coordinate;
import inetsoft.graph.data.DataSet;
import inetsoft.graph.data.SortedDataSet;
import inetsoft.graph.geometry.TreemapGeometry;
import inetsoft.graph.internal.Donut;
import inetsoft.graph.internal.TreemapVisualModel;
import inetsoft.graph.treemap.*;
import inetsoft.graph.treeviz.ProgressTracker;
import inetsoft.graph.treeviz.tree.DefaultNodeInfo;
import inetsoft.graph.treeviz.tree.circlemap.*;
import inetsoft.graph.treeviz.tree.sunburst.*;
import inetsoft.report.composition.graph.ValueOrderComparer;
import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.ints.IntSet;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;

/**
 * This defines a treemap chart element.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public class TreemapElement extends GraphElement {
  /**
    * Treemap layout algorithms.
    */
   public enum Algorithm { SLICE, BINARY, SQUARIFIED }

   /**
    * Types of tree visualization.
    * TREEMAP - default rectangular treemap.
    * CICLEMAP - circle packing tree presentation.
    * SUNBURST - hierarchical pie structure.
    * ICICLE - horizontal bar like tree.
    */
   public enum Type {
      TREEMAP(GraphConstants.TOP_ALIGNMENT | GraphConstants.LEFT_ALIGNMENT, false, true),
      CIRCLE(GraphConstants.MIDDLE_ALIGNMENT | GraphConstants.CENTER_ALIGNMENT, true, true),
      SUNBURST(GraphConstants.MIDDLE_ALIGNMENT | GraphConstants.CENTER_ALIGNMENT, false, false),
      ICICLE(GraphConstants.MIDDLE_ALIGNMENT | GraphConstants.LEFT_ALIGNMENT, false, false);

      Type(int labelAlign, boolean showRoot, boolean containment) {
         this.labelAlign = labelAlign;
         this.showRoot = showRoot;
         this.containment = containment;
      }

      public int getLabelAlign() {
         return labelAlign;
      }

      public boolean isShowRoot() {
         return showRoot;
      }

      public boolean isContainment() {
         return containment;
      }

      private int labelAlign;
      private boolean showRoot;
      private boolean containment;
   }

   /**
    * Define an empty treemap. Tree dimensions must be added using addTreeDim().
    */
   public TreemapElement() {
   }

   /**
    * Create a treemap with specified tree dimensions.
    */
   public TreemapElement(String ...dims) {
      for(String dim : dims) {
         addTreeDim(dim);
      }
   }

   public Type getMapType() {
      return mapType;
   }

   public void setMapType(Type type) {
      this.mapType = type;
   }

   /**
    * Get the treemap layout algorithm. Defaults to SQUARIFIED.
    */
   public Algorithm getAlgorithm() {
      return algorithm;
   }

   /**
    * Set the treemap layout algorithm.
    */
   public void setAlgorithm(Algorithm algorithm) {
      this.algorithm = algorithm;
   }

   /**
    * Add a dimension to the treemap. it's used as the nested dimensions of treemaps.
    */
   public void addTreeDim(String col) {
      if(col != null) {
         treeDims.add(col);
      }
   }

   /**
    * Get the tree dimension at the specified index.
    * @param idx the dim index.
    */
   public String getTreeDim(int idx) {
      return treeDims.get(idx);
   }

   /**
    * Get the number of tree dimensions specified for this element.
    */
   public int getTreeDimCount() {
      return treeDims.size();
   }

   /**
    * Remove the tree dimension at the specified index.
    * @param idx the dim index.
    */
   public void removeTreeDim(int idx) {
      treeDims.remove(idx);
   }

   /**
    * Get the tree dimensions.
    */
   public List<String> getTreeDims() {
      return treeDims;
   }

   /**
    * Set whether the label should be drawn from center to the edge, or as circular text around
    * the center.
    * @param level treemap level.
    */
   public void setRadialLabel(int level, boolean radial) {
      if(radial) {
         radialLevels.add(level);
      }
      else {
         radialLevels.remove(level);
      }
   }

   /**
    * Check if label should be drawn from center to outer edge.
    */
   public boolean isRadialLabel(int level) {
      return radialLevels.contains(level);
   }

   /**
    * Get the border color for the circle level.
    */
   public Color getBorderColor(int level) {
      return borderColors.get(level);
   }

   /**
    * Set the border color for the circles.
    * @param level circle nesting level, top level is 0.
    */
   public void setBorderColor(int level, Color color) {
      borderColors.put(level, color);
   }

   /**
    * Get the background color for the circle level.
    */
   public Color getBackground(int level) {
      return backgrounds.get(level);
   }

   /**
    * Set the background color for the circle level.
    * @param level circle nesting level, top level is 0.
    */
   public void setBackground(int level, Color background) {
      backgrounds.put(level, background);
   }

   protected VisualModel createVisualModel(DataSet data) {
      return new TreemapVisualModel(data, getColorFrame(), getSizeFrame(), getShapeFrame(),
                                    getTextureFrame(), getLineFrame(), getTextFrame());
   }

   @Override
   public void createGeometry(DataSet data, GGraph graph) {
      SortedDataSet sdata = sortData(data, graph);

      if(sdata != null) {
         data = getSortedDataSetInRange(data, sdata);
         // values must be sorted for the treemap chart to render properly
         sdata.setForceSort(true);
      }

      VisualModel vmodel = createVisualModel(data);
      TreeModel root;
      Stack<TreeModel> currRoot = new Stack<>();
      List currVals = new ArrayList();

      int max = getEndRow(data);

      if(getTreeDimCount() == 0) {
         return;
      }

      if(mapType.isShowRoot()) {
         TreemapGeometry gobj = new TreemapGeometry(this, graph, null, -1, vmodel, false,
                                                    getTreeDimCount());
         gobj.setSubRowIndex(-1);
         gobj.setRowIndex(-1);
         gobj.setColIndex(-1);
         graph.addGeometry(gobj);
         root = new TreeModel(gobj);
      }
      else {
         root = new TreeModel();
      }

      String sizeField = vmodel != null ? vmodel.getSizeFrame().getField() : null;
      currRoot.push(root);

      for(int i = getStartRow(data); i < max; i++) {
         if(!isAccepted(data, i)) {
            continue;
         }

         List dimVals = getRowValues(data, i);
         // common prefix is used to organized children into parent circles. it should not
         // be applied to the leaves. when dimension is bound to color, there may be
         // duplicate children with same prefix at the leaf level. shouldn't ignore them.
         int common = Math.min(commonPrefix(dimVals, currVals), getTreeDimCount() - 1);

         while(currRoot.size() - 1 > common) {
            currRoot.pop();
         }

         if(sizeField != null) {
            Object size = data.getData(sizeField, i);

            if(size == null) {
               continue;
            }

            // treemap represents value as an area, so if it's 0, it shouldn't be drawn.
            if(getSizeFrame().getSize(data, sizeField, i) == 0) {
               continue;
            }
         }

         for(int k = common; k < treeDims.size(); k++) {
            TreeModel curr = currRoot.peek();
            String vname = treeDims.get(k);
            boolean leaf = k == treeDims.size() - 1;
            TreemapGeometry gobj = new TreemapGeometry(this, graph, vname, i, vmodel,
                                                       leaf, treeDims.size() - k - 1);
            int sidx = sdata == null ? i : sdata.getBaseRow(i);

            gobj.setSubRowIndex(sidx);
            gobj.setRowIndex(getRootRowIndex(data, i));
            gobj.setColIndex(getRootColIndex(data, vname));
            graph.addGeometry(gobj);

            TreeModel node = new TreeModel(gobj);

            node.setLeaf(leaf);
            curr.addChild(node);
            currVals = dimVals;
            currRoot.push(node);
         }
      }

      root.updateChildRows();
      // parent sizes are calculated from children (leaves)
      calcSize(root);

      // layout the areas
      // overlay area gets size/position from underlying element so don't need to laid out
      if(getHint("overlay") == null) {
         switch(mapType) {
         case TREEMAP:
            root.layout(createMapLayout(), new Rect(0, 0, Coordinate.GWIDTH, Coordinate.GHEIGHT));
            break;
         case CIRCLE:
            layoutCirclemap(root);
            break;
         case SUNBURST:
            layoutSunburst(root);
            break;
         case ICICLE:
            layoutIcicle(root);
            break;
         }
      }
   }

   // copy circlemap node info to coordinate logic space
   private void layoutCirclemap(TreeModel root) {
      CirclemapModel model = new CirclemapModel(root, new DefaultNodeInfo(), new ProgressTracker());
      CirclemapTree tree = model.getView();
      CirclemapNode vroot = tree.getRoot();
      double radius = vroot.radius; //vroot.getWeightRadius(model.getInfo());
      double scale = Coordinate.GHEIGHT / 2 / radius;

      layoutCirclemap0(vroot, scale, 0, 0);
   }

   // center x/y of vroot is relative to parent node
   // pcx and pcy are parent center x/y
   private void layoutCirclemap0(CirclemapNode vroot, double scale, double pcx, double pcy) {
      TreeModel datanode = (TreeModel) vroot.getDataNode();
      double cx = pcx + vroot.cx;
      double cy = pcy + vroot.cy;
      ((MapItem) datanode.getMapItem()).setShape(new Circle(cx * scale, cy * scale,
                                                            vroot.radius * scale));

      for(CirclemapNode node : vroot.children()) {
         layoutCirclemap0(node, scale, cx, cy);
      }
   }

   // copy sunburst node info to coordinate logic space
   private void layoutIcicle(TreeModel root) {
      IcicleModel model = new IcicleModel(root, new DefaultNodeInfo());
      SunburstTree tree = model.getView();
      SunburstNode vroot = tree.getRoot();

      layoutIcicle0(vroot, 0, 0, Coordinate.GWIDTH, Coordinate.GHEIGHT, true);
   }

   private void layoutIcicle0(SunburstNode vroot, double x, double y, double w, double h,
                              boolean root)
   {
      double x2 = x;
      double w2 = w;
      double y2 = y;
      double extent = vroot.getExtent();
      double depth = vroot.getDepth();
      double maxdepth = vroot.getMaxDepth();

      // ignore root
      if(!root) {
         TreeModel datanode = (TreeModel) vroot.getNode();
         double w0 = w / maxdepth;
         ((MapItem) datanode.getMapItem()).setShape(new Rectangle2D.Double(x, y, w0, h));

         x2 = x + w0;
         w2 = w - w0;
      }

      for(SunburstNode node : vroot.children()) {
         double extent2 = node.getExtent();
         double h2 = extent2 * h / extent;
         layoutIcicle0(node, x2, y2, w2, h2, false);
         y2 += h2;
      }
   }

   // copy sunburst node info to coordinate logic space
   private void layoutSunburst(TreeModel root) {
      SunburstModel model = new SunburstModel(root, new DefaultNodeInfo());
      SunburstTree tree = model.getView();
      SunburstNode vroot = tree.getRoot();

      layoutSunburst0(vroot, 0, 0, 360, vroot.getMaxDepth());
   }

   private void layoutSunburst0(SunburstNode vroot, double consumedR, double angleStart,
                                double angleExtent, double maxDepth)
   {
      double extent = vroot.getExtent();

      TreeModel datanode = (TreeModel) vroot.getNode();
      double r0 = consumedR; // inner
      double r1 = r0 + Coordinate.GWIDTH / 2 / maxDepth; // outer
      final double cx = Coordinate.GWIDTH / 2;
      final double cy = Coordinate.GHEIGHT / 2;

      // small rounding error (e.g. 360.00000006) may cause the slice borders to be
      // slightly misaligned between rings. (56361)
      if(angleExtent + angleStart > 360) {
         angleExtent = 360 - angleStart;
      }

      ((MapItem) datanode.getMapItem()).setShape(new Donut(cx - r1, cy - r1, r1 * 2, r1 * 2,
                                                           r0 * 2, r0 * 2, -angleStart,
                                                           -angleExtent));

      for(SunburstNode node : vroot.children()) {
         double extent2 = node.getExtent();
         double angleExtent2 = extent2 * angleExtent / extent;
         layoutSunburst0(node, r1, angleStart, angleExtent2, maxDepth);
         angleStart += angleExtent2;
      }
   }

   // calculate the parent size from (leaf) children
   private void calcSize(TreeModel root) {
      if(root.hasChildren()) {
         for(int i = 0; i < root.childCount(); i++) {
            calcSize(root.getChild(i));
         }

         double size = 0;

         for(int i = 0; i < root.childCount(); i++) {
            size += root.getChild(i).getMapItem().getSize();
         }

         root.getMapItem().setSize(size);
      }
   }

   // get values for the tree dims
   private List getRowValues(DataSet data, int row) {
      return treeDims.stream()
         .map(dim -> data.getData(dim, row))
         .collect(Collectors.toList());
   }

   // find number of common prefix
   private int commonPrefix(List list1, List list2) {
      int max = Math.min(list1.size(), list2.size());

      for(int i = 0; i < max; i++) {
         if(!Objects.equals(list1.get(i), list2.get(i))) {
            return i;
         }
      }

      return max;
   }

   // sort data by tree dimensions
   @Override
   public SortedDataSet sortData(DataSet data, GGraph graph) {
      SortedDataSet sdata = super.sortData(data, graph);

      if(sdata == null) {
         sdata = new SortedDataSet(data);
      }

      for(int i = 0; i < treeDims.size(); i++) {
         String dim = treeDims.get(i);
         Comparator comp = data.getComparator(dim);

         // sort value within each tree group. (50045, 50922)
         if(comp instanceof ValueOrderComparer) {
            sdata.addSortColumn(dim, true);
            ((ValueOrderComparer) comp).setGroups(treeDims.subList(0, i + 1)
                                                     .toArray(new String[0]));
            sdata.setComparator(dim, comp);
         }
         // ranking should not sort by value again. (50959)
         else {
            sdata.addSortColumn(dim, false);
         }
      }

      return sdata;
   }

   private MapLayout createMapLayout() {
      switch(algorithm) {
      case SLICE: return new SliceLayout();
      case SQUARIFIED: return new SquarifiedLayout();
      case BINARY: return new BinaryTreeLayout();
      }

      return new SliceLayout();
   }

   @Override
   public boolean supportsOverlay() {
      return true;
   }

   @Override
   public boolean equalsContent(Object obj) {
      if(!super.equalsContent(obj)) {
         return false;
      }

      TreemapElement elem = (TreemapElement) obj;

      return treeDims.equals(elem.treeDims) && algorithm.equals(elem.algorithm) &&
         mapType.equals(elem.mapType) && backgrounds.equals(elem.backgrounds);
   }

   @Override
   public GraphElement clone() {
      TreemapElement elem = (TreemapElement) super.clone();
      elem.treeDims = new ArrayList<>(treeDims);
      elem.radialLevels = new IntArraySet(radialLevels);
      elem.backgrounds = new HashMap<>(backgrounds);
      elem.borderColors = new HashMap<>(borderColors);

      return elem;
   }

   private List<String> treeDims = new ArrayList<>();
   private Algorithm algorithm = Algorithm.SQUARIFIED;
   private Type mapType = Type.TREEMAP;
   private IntSet radialLevels = new IntArraySet();
   private Map<Integer, Color> backgrounds = new HashMap<>();
   private Map<Integer, Color> borderColors = new HashMap<>();

   private static final long serialVersionUID = 1L;
}
