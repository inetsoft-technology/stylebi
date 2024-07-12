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
import inetsoft.graph.aesthetic.VisualModel;
import inetsoft.graph.coord.Coordinate;
import inetsoft.graph.element.GraphElement;
import inetsoft.graph.element.ParaboxElement;
import inetsoft.graph.internal.GDefaults;
import inetsoft.graph.visual.ParaboxLineVO;
import inetsoft.graph.visual.VisualObject;

import java.awt.*;

/**
 * A parabox geomtry captures represents a link (line) on a parabox diagram.
 *
 * @version 13.4
 * @author InetSoft Technology Corp
 */
public class ParaboxLineGeometry extends ParaboxPointGeometry {
   /**
    * Create a point in a 2D coordinate.
    * @param elem the source graph element.
    * @param var the name of the variable column.
    * @param tidx the index of the tuple or the base index of tuples.
    * @param vmodel the visual aesthetic attributes.
    * @param toVar the dimension at the end of the line.
    * @param toVal the value at the end of the line.
    */
   public ParaboxLineGeometry(GraphElement elem, GGraph graph, String var, int tidx,
                               VisualModel vmodel, double pval, String toVar, double toVal)
   {
      super(elem, graph, var, tidx, vmodel, pval);
      this.toVar = toVar;
      this.toVal = toVal;
   }

   /**
    * Get the tuples for start and end of line.
    */
   public double[][] getTuples() {
      ParaboxElement elem = (ParaboxElement) getElement();
      return new double[][] { ParaboxPointGeometry.getTuple(elem, getVar(), getParaboxValue()),
                              ParaboxPointGeometry.getTuple(elem, toVar, toVal) };
   }

   @Override
   public VisualObject createVisual(Coordinate coord) {
      ParaboxLineVO vo = new ParaboxLineVO(this, coord, getVar());
      return vo;
   }

   @Override
   public Color getColor(int idx) {
      ParaboxElement elem = (ParaboxElement) getElement();
      return elem.getLineColorFrame() != null
         ? elem.getLineColorFrame().getColor(getWeight()) : GDefaults.DEFAULT_LINE_COLOR;
   }

   @Override
   public double getSize(int idx) {
      ParaboxElement elem = (ParaboxElement) getElement();
      return elem.getLineSizeFrame() != null
         ? elem.getLineSizeFrame().getSize(getWeight()) : 3;
   }

   private double toVal;
   private String toVar;
}
