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
package inetsoft.graph.guide.legend;

import inetsoft.graph.BoundedVisualizable;
import inetsoft.graph.aesthetic.LinearColorFrame;
import inetsoft.graph.internal.DimensionD;
import inetsoft.graph.internal.GTool;

import java.awt.*;
import java.awt.geom.*;

/**
 * Color Legend band.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public class ColorLegendBand extends BoundedVisualizable {
   /**
    * Contructor.
    * @param frame is the VisualFrame content legend informations.
    */
   public ColorLegendBand(LinearColorFrame frame) {
      this.colorFrame = frame;
   }

   /**
    * Paint the visual object on the graphics.
    * @param g the graphic.
    */
   @Override
   public void paint(Graphics2D g) {
      Graphics2D g2 = (Graphics2D) g.create();
      Point2D pos = getPosition();
      double width = getSize().getWidth();
      double height = getSize().getHeight();
      Object[] values = colorFrame.getValues();
      boolean reversed = values.length != 0 && values[0] instanceof Comparable
         && ((Comparable) values[0]).compareTo(values[values.length - 1]) > 0;

      for(int i = 0; i < width; i++) {
         double ratio = (double) i / width;
         Color color = colorFrame.getColor(reversed ? (1 - ratio) : ratio);
         color = GTool.getColor(color, alpha);
         g2.setColor(color);
         g2.fill(new Rectangle2D.Double(
            pos.getX() + i, pos.getY() - TOP_PADDING, 1, height));
      }

      g2.dispose();
   }

   /**
    * Get preferred width.
    * @return preferred width.
    */
   @Override
   protected double getPreferredWidth0() {
      return BAND_PREF_SIZE.getWidth();
   }

   /**
    * Get preferred height.
    * @return preferred height.
    */
   @Override
   protected double getPreferredHeight0() {
      return BAND_PREF_SIZE.getHeight();
   }

   /**
    * Get the min width.
    * @return min width.
    */
   @Override
   protected double getMinWidth0() {
      return BAND_MIN_SIZE.getWidth();
   }

   /**
    * Get the min height.
    * @return min height.
    */
   @Override
   protected double getMinHeight0() {
      return BAND_MIN_SIZE.getHeight();
   }

   /**
    * Get the max width of band.
    * @return max width.
    */
   public double getMaxWidth() {
      return BAND_MAX_SIZE.getWidth();
   }

   /**
    * Get the max height of band.
    * @return max height.
    */
   public double getMaxHeight() {
      return BAND_MAX_SIZE.getHeight();
   }

   /**
    * Set the color alpha of band.
    * @param alpha the color alpha.
    */
   void setAlpha(double alpha) {
      this.alpha = alpha;
   }

   private static final int TOP_PADDING = 5;
   private static final Dimension2D BAND_MIN_SIZE = new DimensionD(50, 18);
   private static final Dimension2D BAND_PREF_SIZE = new DimensionD(80, 18);
   private static final Dimension2D BAND_MAX_SIZE = new DimensionD(200, 18);
   private LinearColorFrame colorFrame;
   private double alpha = 1;
}
