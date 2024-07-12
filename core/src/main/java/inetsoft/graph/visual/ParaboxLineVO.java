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

import inetsoft.graph.VGraph;
import inetsoft.graph.aesthetic.GLine;
import inetsoft.graph.coord.Coordinate;
import inetsoft.graph.geometry.*;
import inetsoft.graph.internal.GDefaults;

import java.awt.*;
import java.awt.geom.*;

/**
 * This visual object represents a line in a parabox graph.
 *
 * @version 13.4
 * @author InetSoft Technology Corp
 */
public class ParaboxLineVO extends ElementVO {
   /**
    * Create a visual object at 0,0 location.
    * @param coord the coordinate the visual object is plotted on.
    */
   public ParaboxLineVO(Geometry gobj, Coordinate coord, String mname) {
      super(gobj, mname);

      // line could be covered by axis line (polar inner axis), make sure it's
      // on top
      setZIndex(GDefaults.VO_Z_INDEX + 2);

      ParaboxLineGeometry obj = (ParaboxLineGeometry) gobj;
      double[][] tuples = obj.getTuples();
      points = new Point2D.Double[tuples.length];

      for(int i = 0; i < points.length; i++) {
         points[i] = coord.getPosition(tuples[i]);
      }
   }

   @Override
   public void paint(Graphics2D g) {
      double alpha = getAlphaHint();
      LineVO.paintLine(g, alpha, lineInfo);
   }

   @Override
   public void transform(Coordinate coord) {
      for(int i = 0; i < points.length; i++) {
         points[i] = (Point2D) coord.transformShape(points[i]);
      }
   }

   @Override
   public Rectangle2D getBounds() {
      return lineInfo.getBounds();
   }

   /**
    * Get the start and end points on the line.
    */
   protected Point2D[] getPoints() {
      return points;
   }

   @Override
   protected double getMinWidth0() {
      return MIN_WIDTH;
   }

   @Override
   public Shape[] getShapes() {
      return lineInfo.getShapes();
   }

   public Point2D[] getTransformedPoints() {
      return lineInfo.getTransformedPoints();
   }

   @Override
   public void layoutText(VGraph vgraph) {
   }

   @Override
   public VOText[] getVOTexts() {
      return new VOText[0];
   }

   @Override
   protected double getPreferredWidth0() {
      return 1;
   }

   @Override
   public int[] getRowIndexes() {
      ParaboxLineGeometry gobj = (ParaboxLineGeometry) getGeometry();
      return gobj.getRowIndexes();
   }

   public Point2D getEndPoint() {
      return getScreenTransform().transform(points[1], null);
   }

   @Override
   public Object clone() {
      ParaboxLineVO vo = (ParaboxLineVO) super.clone();
      vo.lineInfo = vo. new LineInfo2();
      return vo;
   }

   private class LineInfo2 implements LineVO.LineInfo {
      @Override
      public Point2D[] getPoints() {
         return points;
      }

      @Override
      public Color getColor(int idx) {
         return gobj().getColor(idx);
      }

      @Override
      public double getSize(int idx) {
         return gobj().getSize(idx);
      }

      @Override
      public GLine getLine(int idx) {
         return gobj().getLine(idx);
      }

      @Override
      public AffineTransform getScreenTransform() {
         return ParaboxLineVO.this.getScreenTransform();
      }

      @Override
      public Shape[] getShapes() {
         Point2D[] points = getTransformedPoints();
         return new Shape[] { new Line2D.Double(points[0], points[1]) };
      }

      private ElementGeometry gobj() {
         return (ElementGeometry) getGeometry();
      }
   };

   private static final double MIN_WIDTH = 10;
   private Point2D[] points; // the line points
   private LineVO.LineInfo lineInfo = new LineInfo2();
}
