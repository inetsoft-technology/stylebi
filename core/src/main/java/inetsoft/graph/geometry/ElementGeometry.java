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
package inetsoft.graph.geometry;

import inetsoft.graph.GGraph;
import inetsoft.graph.aesthetic.*;
import inetsoft.graph.coord.RectCoord;
import inetsoft.graph.data.DataSet;
import inetsoft.graph.element.GraphElement;
import inetsoft.graph.scale.Scale;
import inetsoft.graph.visual.ElementVO;
import inetsoft.graph.visual.Pie3DVO;
import inetsoft.util.CoreTool;

import java.awt.*;

/**
 * A geometry defines the geometric object to be plotted on a graph.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public abstract class ElementGeometry extends Geometry {
   /**
    * Create a geometry from a graph element.
    * @param elem the graph element the created this geometry.
    * @param var the name of the variable column.
    * @param tidx the index of the tuple or the base index of tuples.
    * @param vmodel the visual aesthetic attributes.
    */
   protected ElementGeometry(GraphElement elem, GGraph graph, String var, int tidx,
                             VisualModel vmodel)
   {
      this(elem, graph, new String[] {var}, tidx, vmodel);
   }

   /**
    * Create a geometry from a graph element.
    * @param elem the graph element the created this geometry.
    * @param vars the variable columns.
    * @param tidx the index of the tuple or the base index of tuples.
    * @param vmodel the visual aesthetic attributes.
    */
   protected ElementGeometry(GraphElement elem, GGraph graph, String[] vars,
                             int tidx, VisualModel vmodel)
   {
      this.elem = elem;
      this.vars = vars;
      this.tidx = tidx;
      this.vmodel = vmodel;

      if(graph.getCoordinate() instanceof RectCoord) {
         Scale yscale = ((RectCoord) graph.getCoordinate()).getYScale();

         if(yscale != null) {
            measure = yscale.getMeasure();
         }
      }

      if(measure == null) {
         measure = vars.length == 0 ? null : vars[0];
      }
   }

   /**
    * Get the source graph element.
    * @return the source graph element.
    */
   public GraphElement getElement() {
      return elem;
   }

   /**
    * Get the name of the variable column.
    * @return the name of the variable column.
    */
   public String getVar() {
      return vars[0];
   }

   /**
    * Get all the variable columns.
    * @return all the variable columns.
    */
   public String[] getVars() {
      return vars;
   }

   /**
    * Get the index of the tuple in dataset.
    * @return the index of the tuple in dataset.
    */
   public int getTupleIndex() {
      return tidx;
   }

   /**
    * Get the color of this object.
    * @param idx the index of the tuple that is rendered.
    * @return the color of this object.
    */
   public Color getColor(int idx) {
      return tidx < 0 ? null : vmodel.getColor(measure, idx + tidx);
   }

   /**
    * Get the size of this object.
    * @param idx the index of the tuple that is rendered.
    * @return the size of this object.
    */
   public double getSize(int idx) {
      return tidx < 0 ? 0 : vmodel.getSize(measure, idx + tidx);
   }

   /**
    * Get the shape to use to draw/fill this object.
    * @param idx the index of the tuple that is rendered.
    * @return the shape to use to draw/fill this object.
    */
   public GShape getShape(int idx) {
      return tidx < 0 ? null : vmodel.getShape(measure, idx + tidx);
   }

   /**
    * Get the texture to use to draw/fill this object.
    * @param idx the index of the tuple that is rendered.
    */
   public GTexture getTexture(int idx) {
      return tidx < 0 ? null : vmodel.getTexture(measure, idx + tidx);
   }

   /**
    * Get the label to be drawn for this object.
    * @param idx the index of the tuple that is rendered.
    */
   public Object getText(int idx) {
      return tidx < 0 ? null : vmodel.getText(measure, idx + tidx);
   }

   /**
    * Get the line style to be drawn for this object.
    * @param idx the index of the tuple that is rendered.
    */
   public GLine getLine(int idx) {
      return tidx < 0 ? null : vmodel.getLine(measure, idx + tidx);
   }

   /**
    * Get the visual model.
    */
   public VisualModel getVisualModel() {
      return vmodel;
   }

   public String getOverlayId(ElementVO vo, DataSet data) {
      int ridx = vo.getSubRowIndex();
      ElementGeometry g = (ElementGeometry) vo.getGeometry();
      GraphElement elem = g.getElement();
      StringBuilder sb = new StringBuilder();
      boolean pie3d = vo instanceof Pie3DVO;

      if(pie3d) {
         int[] rarr = vo.getSubRowIndexes();

         if(rarr != null && rarr.length > 0) {
            ridx = rarr[0];
         }
         // no row index? ignore the vo
         else {
            return sb.toString();
         }
      }

      for(int i = 0; i < data.getColCount(); i++) {
         String dim = data.getHeader(i);

         if(!data.isMeasure(dim) && ridx >= 0 && ridx < data.getRowCount()) {
            if(pie3d && indexOfDim(elem, dim) < 0) {
               continue;
            }

            if(sb.length() > 0) {
               sb.append(',');
            }

            Object obj = data.getData(dim, ridx);
            sb.append(CoreTool.toString(obj));
         }
      }

      // maybe elements for same dims but different vars
      if(vo.getColIndex() >= 0) {
         String var = data.getHeader(vo.getColIndex());
         var = ElementVO.getBaseName(var);
         sb.append("," + var);
      }
      else {
         for(int i = 0; i < elem.getVarCount(); i++) {
            sb.append("," + ElementVO.getBaseName(elem.getVar(i)));
         }
      }

      return sb.toString();
   }

   /**
    * Get the index of the specified dimension.
    */
   private int indexOfDim(GraphElement elem, String dim) {
      for(int i = 0; i < elem.getDimCount(); i++) {
         if(dim.equals(elem.getDim(i))) {
            return i;
         }
      }

      return -1;
   }

   /**
    * Get label placement.
    */
   public int getLabelPlacement() {
      return elem.getLabelPlacement();
   }

   /**
    * Called if tuple is no longer needed.
    */
   public void clearTuple() {
   }

   private GraphElement elem; // source graph element
   private VisualModel vmodel; // aesthetic attributes
   private int tidx; // the index (or base index) of the tuple in the database
   private String[] vars; // the variable columns
   private String measure;
}
