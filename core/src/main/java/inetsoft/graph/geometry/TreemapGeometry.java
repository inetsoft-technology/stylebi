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
package inetsoft.graph.geometry;

import inetsoft.graph.GGraph;
import inetsoft.graph.aesthetic.*;
import inetsoft.graph.coord.Coordinate;
import inetsoft.graph.data.DataSet;
import inetsoft.graph.element.GraphElement;
import inetsoft.graph.element.TreemapElement;
import inetsoft.graph.internal.TreemapVisualModel;
import inetsoft.graph.treemap.MapItem;
import inetsoft.graph.visual.*;

import java.awt.*;

/**
 * This represents a treemap area.
 *
 * @version 12.3
 * @author InetSoft Technology Corp
 */
public class TreemapGeometry extends ElementGeometry {
   public TreemapGeometry(GraphElement elem, GGraph graph, String var, int tidx,
                          VisualModel vmodel, boolean leaf, int level)
   {
      super(elem, graph, var, tidx, vmodel);
      this.leaf = leaf;
      this.level = level;

      if(leaf) {
         mapItem.setSize(getSize(0));
      }
   }

   /**
    * Create a visual object to visualize this element.
    * @param coord the coordinate the visual object is plotted on.
    * @return the new visual object.
    */
   @Override
   public VisualObject createVisual(Coordinate coord) {
      TreemapVO vo = new TreemapVO(this, coord);
      vo.setColIndex(cidx);
      vo.setSubRowIndex(subridx);
      vo.setRowIndex(ridx);
      return vo;
   }

   /**
    * Check if this is a leaf node.
    */
   public boolean isLeaf() {
      return leaf;
   }

   /**
    * Get the node level above leaf.
    */
   public int getLevel() {
      return level;
   }

   /**
    * Set row index.
    */
   public void setRowIndex(int ridx) {
      this.ridx = ridx;
   }

   /**
    * Get row index.
    */
   public int getRowIndex() {
      return ridx;
   }

   /**
    * Get the col index.
    */
   public int getColIndex() {
      return cidx;
   }

   /**
    * Set the col index.
    */
   public void setColIndex(int cidx) {
      this.cidx = (short) cidx;
   }

   /**
    * Set sub row index.
    */
   public void setSubRowIndex(int subridx) {
      this.subridx = subridx;
   }

   /**
    * Get sub row index.
    */
   public int getSubRowIndex() {
      return subridx;
   }

   /**
    * Item representing this area in TreeModel.
    */
   public MapItem getMapItem() {
      return mapItem;
   }

   /**
    * Get the label for the tree dim.
    * @param idx tree dim index.
    */
   public Object getTreeDimText(int idx) {
      TreemapElement treemap = (TreemapElement) getElement();
      TextFrame text = treemap.getTextFrame();

      if(text instanceof DefaultTextFrame) {
         if(text.getField() != null && text.getField().equals(treemap.getTreeDim(idx))) {
            return getText(0);
         }
      }

      // hide container text since the value would only be the value of the first child,
      // so it's incorrect. better to not show than show a wrong value. will consider
      // way to calculate the container value in a special textframe.
      if(!leaf && !(text instanceof CurrentTextFrame)) {
         switch(treemap.getMapType()) {
         case ICICLE:
         case SUNBURST:
         case CIRCLE:
            return null;
         }
      }

      return getText(0);
   }

   @Override
   public String getOverlayId(ElementVO vo, DataSet data) {
      return super.getOverlayId(vo, data) + ":" + level;
   }

   /**
    * Set the row indexes of a child nodes. The index is the index of SubDataSet (for facet)
    * or the base (of SortedDataSet created in createGeometry).
    */
   public void setChildRows(int[] rows) {
      children = rows;
   }

   /**
    * Get the row indexes of child nodes.
    */
   public int[] getChildRows() {
      return children;
   }

   public Color getColor(int idx) {
      if(getTupleIndex() < 0) {
         return null;
      }

      TreemapVisualModel vmodel = (TreemapVisualModel) getVisualModel();
      return vmodel.getColor(this, idx + getTupleIndex());
   }

   public GTexture getTexture(int idx) {
      if(getTupleIndex() < 0) {
         return null;
      }

      TreemapVisualModel vmodel = (TreemapVisualModel) getVisualModel();
      return vmodel.getTexture(this, idx + getTupleIndex());
   }

   public GLine getLine(int idx) {
      if(getTupleIndex() < 0 && !(getElement().getLineFrame() instanceof StaticLineFrame)) {
         return null;
      }

      TreemapVisualModel vmodel = (TreemapVisualModel) getVisualModel();
      return vmodel.getLine(this, idx + getTupleIndex());
   }

   private int ridx; // row index
   private int subridx; // sub row index
   private short cidx; // column index
   private MapItem mapItem = new MapItem();
   private boolean leaf = true;
   // the number of level below this node
   // for a tree with 3 levels, the root has level 2, and leaf always has 0
   private int level;
   private int[] children = {};
}
