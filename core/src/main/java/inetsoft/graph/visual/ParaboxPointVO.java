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
package inetsoft.graph.visual;

import inetsoft.graph.GraphConstants;
import inetsoft.graph.VGraph;
import inetsoft.graph.aesthetic.GShape;
import inetsoft.graph.coord.Coordinate;
import inetsoft.graph.element.ParaboxElement;
import inetsoft.graph.geometry.*;
import inetsoft.graph.guide.VLabel;
import inetsoft.graph.internal.GDefaults;

import java.awt.*;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

/**
 * This visual object represents a point in parabox graph.
 *
 * @version 13.4
 * @author InetSoft Technology Corp
 */
public class ParaboxPointVO extends ElementVO {
   /**
    * Create a visual object at 0,0 location.
    * @param gobj geometry object.
    * @param coord the coordinate the visual object is plotted on.
    */
   public ParaboxPointVO(Geometry gobj, Coordinate coord) {
      super(gobj);

      // point should be on top of axis
      setZIndex(GDefaults.AXIS_Z_INDEX + 2);

      ParaboxPointGeometry obj = (ParaboxPointGeometry) gobj;
      ParaboxElement elem = (ParaboxElement) obj.getElement();

      loc = coord.getPosition(obj.getTuple());

      Object label = obj.getText(0);
      GShape shp = getShape();
      double size = obj.getSize(0);

      radius = (short) (size + shp.getMinSize() / 2);

      if(label != null) {
         vtext = new VOText(label, this, getMeasureName(), coord.getDataSet(), -1, obj);
         vtext.setCollisionModifier(VLabel.MOVE_NONE);
         vtext.setAlignmentX(GraphConstants.CENTER_ALIGNMENT);
         vtext.setAlignmentY(GraphConstants.MIDDLE_ALIGNMENT);
      }
   }

   @Override
   public void paint(Graphics2D g) {
      ElementGeometry gobj = (ElementGeometry) getGeometry();
      PointVO.paint(g, gobj, loc, radius, getShape(), getScreenTransform(), getAlphaHint(), null);
   }

   @Override
   public void layoutText(VGraph vgraph) {
      if(vtext == null) {
         return;
      }

      lvgraph = PointVO.layoutText(vgraph, lvgraph, (ElementGeometry) getGeometry(),
                           vtext, loc, radius, getShape(), getScreenTransform());
   }

   @Override
   public void transform(Coordinate coord) {
      loc = (Point2D) coord.transformShape(loc);
   }

   public Point2D getPosition() {
      return loc;
   }

   @Override
   public Rectangle2D getBounds() {
      Point2D pt = getScreenTransform().transform(loc, null);
      double r = radius + 1;
      return new Rectangle2D.Double(pt.getX() - r, pt.getY() - r, r * 2, r * 2);
   }

   @Override
   public double getUnscaledSize(int pos, Coordinate coord) {
      return radius;
   }

   @Override
   protected double getMinWidth0() {
      double textw = vtext == null ? 0 : vtext.getPreferredWidth();
      return Math.max(textw, radius * 2 + 2);
   }

   @Override
   protected double getPreferredWidth0() {
      return getMinWidth0();
   }

   @Override
   public VOText[] getVOTexts() {
      return new VOText[] {vtext};
   }

   public Shape[] getShapes() {
      Point2D pos = getScreenTransform().transform(loc, null);
      GShape shp = getShape();
      Shape point = shp.getShape(pos.getX() - radius, pos.getY() - radius,
                                 radius * 2, radius * 2);

      // NIL returns null for shape
      return (point != null) ? new Shape[] {point} : new Shape[0];
   }

   private GShape getShape() {
      ElementGeometry gobj = (ElementGeometry) getGeometry();
      GShape shp = gobj.getShape(0);

      if(shp == null) {
         shp = GShape.FILLED_CIRCLE;
      }

      return shp;
   }

   @Override
   public int[] getRowIndexes() {
      ParaboxPointGeometry gobj = (ParaboxPointGeometry) getGeometry();
      return gobj.getRowIndexes();
   }

   @Override
   public ParaboxPointVO clone() {
      ParaboxPointVO obj = (ParaboxPointVO) super.clone();
      obj.vtext = cloneVOText(vtext);
      return obj;
   }

   private Point2D loc;
   private VOText vtext;
   private short radius;
   private VGraph lvgraph;
}
