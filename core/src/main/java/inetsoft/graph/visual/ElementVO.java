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
package inetsoft.graph.visual;

import inetsoft.graph.*;
import inetsoft.graph.coord.Coordinate;
import inetsoft.graph.coord.RectCoord;
import inetsoft.graph.element.GraphElement;
import inetsoft.graph.geometry.ElementGeometry;
import inetsoft.graph.geometry.Geometry;
import inetsoft.graph.internal.GDefaults;
import inetsoft.graph.internal.GTool;

import java.awt.*;
import java.util.List;
import java.util.*;

/**
 * A visual object is an object that has a visual representation on a graphic
 * output.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public abstract class ElementVO extends VisualObject {
   /**
    * Create a visual object at 0,0 location.
    * @param gobj geometry object.
    */
   public ElementVO(Geometry gobj) {
      this(gobj, gobj instanceof ElementGeometry ? ((ElementGeometry) gobj).getVar() : null);
   }

   /**
    * Create a visual object at 0,0 location.
    * @param gobj geometry object.
    */
   public ElementVO(Geometry gobj, String mname) {
      this.gobj = gobj;
      this.mname = mname;
      setZIndex(GDefaults.VO_Z_INDEX);
   }

   /**
    * Get the corresponding geometry object.
    * @return the corresponding geometry object.
    */
   public Geometry getGeometry() {
      return gobj;
   }

   /**
    * Move the visual objects to avoid overlapping.
    * @param comap collision map, from the first visual object to a list (List)
    * of overlapping visual objects.
    * @param coord the coordinate the visual object is plotted on.
    */
   public void dodge(Map<ElementVO, List<ElementVO>> comap, Coordinate coord) {
      // do nothing
   }

   /**
    * Make the visual object overlay another visual object.
    * @param vo the specified visual object to be overlaid.
    */
   public void overlay(VisualObject vo) {
      // do nothing
   }

   /**
    * Get the visual object's texts.
    * @return the VLabels of this visual object.
    */
   public abstract VOText[] getVOTexts();

   /**
    * Get the visual object's text.
    * @return the VLabel of this visual object.
    */
   public VOText getVOText() {
      VOText[] vtexts = getVOTexts();

      if(vtexts == null || vtexts.length == 0) {
         return null;
      }

      return (vtexts[0] != null) ? vtexts[0] : vtexts[vtexts.length - 1];
   }

   /**
    * Get linkable shapes.
    */
   public abstract Shape[] getShapes();

   /**
    * Get the rendering hints.
    */
   public Map<String, Object> getHints() {
      if(gobj instanceof ElementGeometry) {
         ElementGeometry elemg = (ElementGeometry) gobj;
         return elemg.getElement().getHints();
      }

      return new HashMap();
   }

   /**
    * Get the rendering hint.
    */
   public Object getHint(String hint) {
      if(gobj instanceof ElementGeometry) {
         ElementGeometry elemg = (ElementGeometry) gobj;
         return elemg.getElement().getHint(hint);
      }

      return null;
   }

   /**
    * Get measure name.
    * @return measure name.
    */
   public String getMeasureName() {
      return mname;
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
      this.cidx = cidx;

      if(getVOText() != null) {
         getVOText().setColIndex(cidx);
      }
   }

   /**
    * Get row index.
    */
   public int getRowIndex() {
      return ridxs == null || ridxs.length < 1 ? 0 : ridxs[0];
   }

   /**
    * Set row index.
    */
   public void setRowIndex(int ridx) {
      ridxs = new int[] {ridx};
   }

   /**
    * Set row indexs.
    */
   public void setRowIndexes(int[] ridxs) {
      this.ridxs = ridxs;
   }

   /**
    * Get row indexs.
    */
   public int[] getRowIndexes() {
      return ridxs;
   }

   /**
    * Get sub row index.
    * @return sub row index.
    */
   public int getSubRowIndex() {
      return subridxs == null || subridxs.length < 1 ? 0 : subridxs[0];
   }

   /**
    * Set sub row index.
    * @param ridx new sub row index.
    */
   public void setSubRowIndex(int ridx) {
      subridxs = new int[] {ridx};
   }

   /**
    * Set sub row indexs.
    */
   public void setSubRowIndexes(int[] subridxs) {
      this.subridxs = subridxs;
   }

   /**
    * Get sub row indexs.
    */
   public int[] getSubRowIndexes() {
      return subridxs;
   }

   /**
    * Get the size factor. The size factor is element defined. Elements with
    * same size factor have the same 'size' for the same size value (from size
    * frame or default size). Size factor are proportional to the actual size
    * on the output. The larger the factor, the larger the size.
    */
   public double getSizeFactor() {
      return Double.MAX_VALUE;
   }

   /**
    * Scale the shape to the specified size factor.
    */
   public void scaleToSizeFactor(double factor) {
      // ignore
   }

   /**
    * Layout the text labels.
    * @param vgraph the vgraph to hold the text labels.
    */
   public abstract void layoutText(VGraph vgraph);

   /**
    * Called after layoutText() has been called on all ElementVO.
    */
   public void postLayoutText() {
   }

   /**
    * Get the min height of this visualizable.
    * @return the min height of this visualizable.
    */
   @Override
   protected double getMinHeight0() {
      return 0;
   }

   /**
    * Get the preferred height of this visualizable.
    * @return the preferred height of this visualizable.
    */
   @Override
   protected double getPreferredHeight0() {
      return 0;
   }

   /**
    * Compare with another visualizable according to the drawing order.
    */
   @Override
   public int compareTo(Object obj) {
      int rc = super.compareTo(obj);

      if(rc != 0 || !(this.gobj instanceof ElementGeometry)) {
         return rc;
      }

      ElementGeometry gobj = (ElementGeometry) this.gobj;

      if(gobj.getElement().getComparator() != null) {
         return gobj.getElement().getComparator().compare(this, obj);
      }

      return 0;
   }

   /**
    * Check if this element needs to dodge from overlapping.
    */
   public boolean requiresDodge() {
      ElementGeometry gobj = (ElementGeometry) this.gobj;
      GraphElement elem = gobj.getElement();

      return (elem.getCollisionModifier() & GraphElement.MOVE_DODGE) != 0;
   }

   /**
    * Get the graph element.
    */
   @Override
   public Graphable getGraphable() {
      return gobj instanceof ElementGeometry ?
         ((ElementGeometry) gobj).getElement() : null;
   }

   /**
    * Check if overlapping with the other object and dodging is required.
    */
   public boolean requiresDodge(ElementVO vo) {
      return false;
   }

   /**
    * This method is called after all visual objects have been laid out in a
    * graph, before the final layoutText() is called.
    * @param coord the coordinate this object is contained in.
    */
   public void layoutCompleted(Coordinate coord) {
   }

   /**
    * Apply alpha hint to color.
    */
   protected Color applyAlpha(Color color) {
      double alpha = getAlphaHint();

      if(alpha != 1) {
         color = GTool.getColor(color, alpha);
      }

      return color;
   }

   /**
    * Get the alpha set in hints.
    */
   protected double getAlphaHint() {
      return getAlphaHint(getHint(GraphElement.HINT_ALPHA));
   }

   /**
    * Get the alpha set in hints.
    */
   private static double getAlphaHint(Object alpha) {
      if(alpha instanceof Number) {
         return ((Number) alpha).doubleValue();
      }

      if(alpha instanceof String) {
         return Double.parseDouble((String) alpha);
      }

      return 1;
   }

   /**
    * Check if this object should be kept inside plot area.
    */
   @Override
   public boolean isInPlot() {
      if(gobj instanceof ElementGeometry) {
         return ((ElementGeometry) gobj).getElement().isInPlot();
      }

      return super.isInPlot();
   }

   /**
    * Check if the coordinate is a single scale rectangle coordinate.
    */
   static boolean isRect1(Coordinate coord) {
      if(coord instanceof RectCoord) {
         RectCoord rect = (RectCoord) coord;

         return rect.getXScale() == null || rect.getYScale() == null;
      }

      return false;
   }

   /**
    * Get the size of the text label. If alignment is not center, all
    * votext in a graph is set to the max width of the labels belonging
    * to the same element so the alignment would work.
    */
   protected static double getVOTextWidth(VOText vtext, VGraph vgraph, ElementGeometry gobj) {
      GraphElement elem = gobj.getElement();
      int align = vtext.getTextSpec().getAlignment();
      double prefw = vtext.getPreferredWidth();

      // support left/right alignment for sparkline type dashboard
      // when left or right align the labels, we should lineup the labels
      // at the left or right edge, so the labels should be set to the
      // same size otherwise the alignment doesn't take effect
      if((align & GraphConstants.LEFT_ALIGNMENT) != 0 ||
         (align & GraphConstants.RIGHT_ALIGNMENT) != 0)
      {
         Object pw = elem.getHint("vo_width");

         if(pw == null) {
            Coordinate top = GTool.getTopCoordinate(vgraph.getCoordinate());
            VGraph topgraph = top.getVGraph();

            for(Object vo : GTool.getVOs(topgraph)) {
               if(vo instanceof ElementVO) {
                  ElementVO point = (ElementVO) vo;
                  VOText text = point.getVOText();

                  if(point.getGraphable() == elem && text != null) {
                     prefw = Math.max(prefw, text.getPreferredWidth());
                  }
               }
            }

            elem.setHint("vo_width", prefw);
            pw = prefw;
         }

         prefw = ((Number) pw).doubleValue();
      }

      return prefw;
   }

   // strip off ALL_PREFIX
   public static String getBaseName(String var) {
      if(var != null && var.startsWith(ALL_PREFIX)) {
         var = var.substring(ALL_PREFIX.length());
      }

      return var;
   }

   // flip the label to the other side, if supported by this element.
   // @param by the percent (0-1) of the vo height to flip. 1 to flip to the other side.
   // 0.5 to move to the middle.
   public void flip(VOText votext, double by) {
      // default do nothing.
   }

   // need to clone VOText when ElementVO is cloned. since a cloned VGraph is used to calculate
   // bounds, and shared voText causes original graph to be changed. (58092)

   VOText cloneVOText(VOText voText) {
      return voText != null ? (VOText) voText.clone() : null;
   }

   VOText[] cloneVOTexts(VOText[] voTexts) {
      if(voTexts == null) {
         return null;
      }

      return Arrays.stream(Arrays.copyOf(voTexts, voTexts.length, VOText[].class))
         .map(a -> a != null ? a.clone() : null).toArray(VOText[]::new);
   }

   /**
    * All data column header in brush data set.
    */
   public static final String ALL_PREFIX = "__all__";

   private Geometry gobj; // corresponding geometry definition
   private int[] ridxs = {};
   private int[] subridxs;
   private String mname;
   private int cidx;
}
