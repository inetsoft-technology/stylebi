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
package inetsoft.graph.schema;

import inetsoft.graph.aesthetic.SizeFrame;
import inetsoft.graph.aesthetic.VisualFrame;
import inetsoft.graph.coord.Coordinate;
import inetsoft.graph.element.GraphElement;
import inetsoft.graph.geometry.SchemaGeometry;
import inetsoft.graph.internal.GTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.io.Serializable;
import java.util.Map;

/**
 * SchemaPainter defines the common functions of a schema painter. It is used
 * to render multiple measures.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public abstract class SchemaPainter implements Cloneable, Serializable {
   /**
    * Initialize the schema painter with geometry and coord.
    * @param geometry the specified schema geometry.
    * @param coord the specified coord.
    */
   public abstract void init(SchemaGeometry geometry, Coordinate coord);

   /**
    * Transform the painter with screen affine transform.
    */
   public abstract void transformScreen(AffineTransform trans);

   /**
    * Paint the schema painter.
    */
   public abstract void paint(Graphics2D g);

   /**
    * Get the shape count of the schema painter.
    */
   public abstract int getShapeCount();

   /**
    * Get the shape at the specified index of the schema painter.
    */
   public abstract Shape getShape(int idx);

   /**
    * Replace the specified shape.
    */
   public abstract void setShape(int idx, Shape shape);

   /**
    * Check if supports frame.
    */
   public abstract boolean supportsFrame(VisualFrame frame);

   /**
    * Get the tuple to be used to get position in the subgraph coordinate.
    * @param idx the index of last variable in vars.
    * @param tuple the complete tuple.
    * @param vars the tuple containing only var values.
    * @param hasX true if has x scale.
    */
   protected double[] getSubTuple(int idx, double[] tuple, double[] vars, boolean hasX) {
      int dcnt = geometry.getElement().getDimCount();
      double[] arr = new double[dcnt];
      double[] tp = new double[arr.length + 1];

      if(hasX) {
         System.arraycopy(tuple, 0, arr, 0, arr.length);
      }
      // in RectCoord.getPosition, if xscale is null, it takes tuple[tuple.length - 2] as
      // the x value. if we copy entire tuple to arr, that would be the y value. here we
      // leave it as 0. (52221)
      else if(arr.length > 0) {
         System.arraycopy(tuple, 0, arr, 0, arr.length - 1);
      }

      System.arraycopy(arr, 0, tp, 0, arr.length);

      if(vars.length > idx) {
         System.arraycopy(vars, idx, tp, arr.length, 1);
      }

      return tp;
   }

   /**
    * Calculate the horizontal shape size.
    */
   protected double getSize(Coordinate coord, double size, SizeFrame frame) {
      double maxw = coord.getMaxWidth();

      if(frame == null) {
         return Math.min(PREFERRED_WIDTH, maxw);
      }

      double max = frame.getLargest();
      double min = frame.getSmallest();

      if(min == max) {
         return Math.max(1, maxw / 2);
      }

      // base width
      double minwidth = getBaseWidth(maxw);
      double w = Math.max(0, maxw - minwidth);
      // apply size frame
      double bw = minwidth + w * size / frame.getMax();

      return bw;
   }

   /**
    * Get the width of the bar for the min size.
    */
   protected double getBaseWidth(double maxw) {
      return MIN_WIDTH;
   }

   /**
    * Transform the painter with coordinate.
    * @param coord the specified coordinate.
    * @param hints the element hints.
    */
   public void transformShape(Coordinate coord, Map<String, Object> hints) {
      for(int i = 0; i < getShapeCount(); i++) {
         setShape(i, (Shape) coord.transformShape(getShape(i)));
      }
   }

   /**
    * Get color and apply alpha hint.
    */
   protected Color applyAlpha(Color color) {
      Object hint = geometry.getElement().getHint(GraphElement.HINT_ALPHA);

      if(hint != null && color != null) {
         try {
            double alpha = Double.parseDouble(hint.toString());

            if(alpha != 1) {
               color = GTool.getColor(color, alpha);
            }
         }
         catch(Exception ex) {
         }
      }

      return color;
   }

   /**
    * Clone the shema painter.
    */
   @Override
   public Object clone() {
      try {
         return super.clone();
      }
      catch(Exception ex) {
         LOG.error("Failed to clone schema painter", ex);
      }

      return null;
   }

   protected SchemaGeometry geometry;
   private static final int MIN_WIDTH = 5;
   private static final int PREFERRED_WIDTH = 30;

   private static final Logger LOG =
      LoggerFactory.getLogger(SchemaPainter.class);
}
