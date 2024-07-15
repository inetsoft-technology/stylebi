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
package inetsoft.graph.visual;

import inetsoft.graph.VGraph;
import inetsoft.graph.aesthetic.GLine;
import inetsoft.graph.coord.Coordinate;
import inetsoft.graph.element.RelationElement;
import inetsoft.graph.geometry.Geometry;
import inetsoft.graph.geometry.RelationEdgeGeometry;
import inetsoft.graph.internal.GDefaults;
import inetsoft.graph.internal.GTool;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

/**
 * Visualizable object for a relation edge.
 *
 * @version 13.6
 * @author InetSoft Technology Corp
 */
public class RelationEdgeVO extends ElementVO {
   public RelationEdgeVO(Geometry gobj, Coordinate coord) {
      super(gobj);

      setZIndex(GDefaults.VO_Z_INDEX - 1); // edges below nodes
      RelationEdgeGeometry obj = (RelationEdgeGeometry) gobj;
      this.edges = obj.getEdges();
   }

   /**
    * Paint the visual object on the graphics.
    */
   @Override
   public void paint(Graphics2D g) {
      RelationEdgeGeometry gobj = (RelationEdgeGeometry) getGeometry();
      RelationElement elem = (RelationElement) gobj.getElement();

      Graphics2D g2 = (Graphics2D) g.create();
      Color color = gobj.getColor(0);
      GLine line = gobj.getLine(0);
      double size = gobj.getSize(0);

      color = applyAlpha(color);
      g2.setColor(color);

      if(line != null) {
         g2.setStroke(line.getStroke(size));
      }
      else {
         g2.setStroke(new BasicStroke((float) size));
      }

      if(elem.getAlgorithm() == RelationElement.Algorithm.COMPACT_TREE) {
         GTool.setRenderingHint(g2, false);
      }
      else {
         GTool.setRenderingHint(g2, true);
      }

      for(Shape edge : edges) {
         edge = getScreenTransform().createTransformedShape(edge);
         g2.draw(edge);
      }

      g2.dispose();
   }

   @Override
   public void transform(Coordinate coord) {
   }

   @Override
   public Rectangle2D getBounds() {
      Rectangle2D bounds = null;

      for(Shape edge : edges) {
         edge = getScreenTransform().createTransformedShape(edge).getBounds2D();
         bounds = bounds == null ? edge.getBounds2D() : bounds.createUnion(edge.getBounds2D());
      }

      return bounds;
   }

   @Override
   protected double getMinWidth0() {
      return 1;
   }

   @Override
   protected double getPreferredWidth0() {
      return getMinWidth0();
   }

   @Override
   public VOText[] getVOTexts() {
      return new VOText[0];
   }

   @Override
   public Shape[] getShapes() {
      return new Shape[0];
   }

   @Override
   public void layoutText(VGraph vgraph) {
   }

   private List<Shape> edges = new ArrayList<>();
   private static final Color DEFAULT_LINE_COLOR = new Color(0xafafad);

}
