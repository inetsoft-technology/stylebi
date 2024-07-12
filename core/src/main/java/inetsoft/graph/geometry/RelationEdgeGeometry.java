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
import inetsoft.graph.data.DataSet;
import inetsoft.graph.element.GraphElement;
import inetsoft.graph.internal.GTool;
import inetsoft.graph.mxgraph.model.mxCell;
import inetsoft.graph.mxgraph.util.mxPoint;
import inetsoft.graph.visual.*;

import java.awt.*;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

/**
 * This represents a relation edge.
 *
 * @version 13.6
 * @author InetSoft Technology Corp
 */
public class RelationEdgeGeometry extends ElementGeometry {
   public RelationEdgeGeometry(GraphElement elem, GGraph graph, String var, int tidx,
                               VisualModel vmodel)
   {
      super(elem, graph, var, tidx, vmodel);
   }

   /**
    * Create a visual object to visualize this element.
    * @param coord the coordinate the visual object is plotted on.
    * @return the new visual object.
    */
   @Override
   public VisualObject createVisual(Coordinate coord) {
      RelationEdgeVO vo = new RelationEdgeVO(this, coord);
      vo.setRowIndex(ridx);
      return vo;
   }

   public void setEdge(mxCell edge) {
      this.edge = edge;
   }

   public mxCell getEdge() {
      return edge;
   }

   public String getOverlayId(ElementVO vo, DataSet data) {
      return edge.getId();
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

   public List<Shape> getEdges() {
      List<Shape> edges = new ArrayList<>();
      mxCell e = getEdge();
      List<mxPoint> pts = e.getGeometry().getPoints();

      // in case lines are not created by layout, draw a straight line from the target to source.
      if(pts == null || pts.isEmpty()) {
         Line2D line = e.getSource().getGeometry().getConnector(e.getTarget().getGeometry());

         if(line != null) {
            edges.add(line);
         }
      }
      else {
         for(int i = 1; i < pts.size(); i++) {
            Point2D p1 = pts.get(i - 1).getPoint();
            Point2D p2 = pts.get(i).getPoint();
            // flip y since graph is java coordinate and g is graph coordinate (bottom up).
            edges.add(new Line2D.Double(p1.getX(), p2.getY(), p2.getX(), p1.getY()));
         }
      }

      return edges;
   }

   @Override
   public int compareTo(Object obj) {
      if(obj instanceof RelationEdgeGeometry) {
         Color c1 = getColor(0);
         Color c2 = ((RelationEdgeGeometry) obj).getColor(0);

         if(c1 != null && c2 != null) {
            double light1 = GTool.getLuminance(c1);
            double light2 = GTool.getLuminance(c2);
            // paint darker line on top so lines don't appear to be 'cut' by lighter lines.
            return Double.compare(light2, light1);
         }
      }

      return -1;
   }

   private int ridx; // row index
   private mxCell edge;
}
