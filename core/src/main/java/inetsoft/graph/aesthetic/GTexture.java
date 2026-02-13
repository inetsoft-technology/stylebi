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
package inetsoft.graph.aesthetic;

import com.inetsoft.build.tern.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;

/**
 * The GTexture class is the base class for all texture aesthetics.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
@TernClass(url = "#cshid=GTexture")
public class GTexture implements Cloneable, Serializable {
   private static final int GAP0 = 3;
   private static final int GAP1 = 4;
   private static final int GAP2 = 5;
   private static final int WIDTH0 = 1;
   private static final int WIDTH1 = 2;
   private static final double ROTATION0 = Math.PI / 4;
   private static final double ROTATION1 = 3 * Math.PI / 4;
   private static final double ROTATION2 = Math.atan(2d);
   private static final double ROTATION3 = Math.PI - Math.atan(1d / 2d);

   /**
    * Pattern0.
    */
   @TernField
   public static final GTexture PATTERN_0 =
      new GTexture(GAP0, WIDTH0, ROTATION0, 1);

   /**
    * Pattern1.
    */
   @TernField
   public static final GTexture PATTERN_1 =
      new GTexture(GAP0, WIDTH0, ROTATION0, 2);

   /**
    * Pattern2.
    */
   @TernField
   public static final GTexture PATTERN_2 =
      new GTexture(GAP2, WIDTH0, ROTATION1, 1);

   /**
    * Pattern3.
    */
   @TernField
   public static final GTexture PATTERN_3 =
      new GTexture(GAP0, WIDTH1, ROTATION1, 1);

   /**
    * Pattern4.
    */
   @TernField
   public static final GTexture PATTERN_4 =
      new GTexture(GAP2, WIDTH0, ROTATION2, 1);

   /**
    * Pattern5.
    */
   @TernField
   public static final GTexture PATTERN_5 =
      new GTexture(GAP2, WIDTH1, ROTATION0, 2);

   /**
    * Pattern6.
    */
   @TernField
   public static final GTexture PATTERN_6 =
      new GTexture(GAP0, WIDTH0, ROTATION2, 1);

   /**
    * Pattern7.
    */
   @TernField
   public static final GTexture PATTERN_7 =
      new GTexture(GAP2, WIDTH1, ROTATION1, 1);

   /**
    * Pattern8.
    */
   @TernField
   public static final GTexture PATTERN_8 =
      new GTexture(GAP2, WIDTH0, ROTATION3, 1);

   /**
    * Pattern9.
    */
   @TernField
   public static final GTexture PATTERN_9 =
      new GTexture(GAP0, WIDTH1, ROTATION0, 1);

   /**
    * Pattern10.
    */
   @TernField
   public static final GTexture PATTERN_10 =
      new GTexture(GAP2, WIDTH0, ROTATION0, 1);

   /**
    * Pattern11.
    */
   @TernField
   public static final GTexture PATTERN_11 =
      new GTexture(GAP0, WIDTH1, ROTATION3, 1);

   /**
    * Pattern12.
    */
   @TernField
   public static final GTexture PATTERN_12 =
      new GTexture(GAP0, WIDTH0, ROTATION1, 1);

   /**
    * Pattern13.
    */
   @TernField
   public static final GTexture PATTERN_13 =
      new GTexture(GAP0, WIDTH1, ROTATION0, 2);

   /**
    * Pattern14.
    */
   @TernField
   public static final GTexture PATTERN_14 =
      new GTexture(GAP0, WIDTH1, ROTATION2, 1);

   /**
    * Pattern15.
    */
   @TernField
   public static final GTexture PATTERN_15 =
      new GTexture(GAP2, WIDTH1, ROTATION2, 1);

   /**
    * Pattern16.
    */
   @TernField
   public static final GTexture PATTERN_16 =
      new GTexture(GAP2, WIDTH0, ROTATION0, 2);

   /**
    * Pattern17.
    */
   @TernField
   public static final GTexture PATTERN_17 =
      new GTexture(GAP2, WIDTH1, ROTATION3, 1);

   /**
    * Pattern18.
    */
   @TernField
   public static final GTexture PATTERN_18 =
      new GTexture(GAP0, WIDTH0, ROTATION3, 1);

   /**
    * Pattern19.
    */
   @TernField
   public static final GTexture PATTERN_19 =
      new GTexture(GAP2, WIDTH1, ROTATION0, 1);

   /**
    * Create a solid fill.
    */
   public GTexture() {
   }

   /**
    * Create a texture with specified fill pattern.
    * @param gap the line gap.
    * @param width the line stroke.
    * @param rotation the angle to be rotated.
    * @param size the number of the line.
    */
   private GTexture(int gap, int width, double rotation, int size) {
      for(int i = 0; i < size; i++) {
         gaps.add(gap);
         widths.add(width);

         if(i == 1) {
            rotations.add(Math.PI - rotation);
         }
         else {
            rotations.add(rotation);
         }
      }
   }

   /**
    * Create a texture with specified fill pattern.
    * @param gap gap between lines.
    * @param rotation the angle to be rotated.
    * @param size the number of the line.
    */
   @TernConstructor
   public GTexture(int gap, double rotation, int size) {
      for(int i = 0; i < size; i++) {
         gaps.add(gap);
         widths.add(WIDTH0);

         if(i == 1) {
            rotations.add(Math.PI - rotation);
         }
         else {
            rotations.add(rotation);
         }
      }
   }

   /**
    * Get the line gap.
    */
   @TernMethod
   public int getLineGap() {
      int gap = gaps.isEmpty() ? 0 : gaps.getFirst();

      return Math.max(gap, 1);
   }

   /**
    * Get the line rotation.
    */
   private double getLineRotation(int idx) {
      double v = rotations.get(idx);

      while(v < 0) {
         v += Math.PI * 2;
      }

      return v;
   }

   /**
    * Get the line width.
    */
   @TernMethod
   public int getLineWidth() {
      return widths.isEmpty() ? 0 : widths.getFirst();
   }

   /**
    * Paint the texture.
    * @param clip the shape to paint.
    */
   public void paint(Graphics2D g2, Shape clip) {
      g2 = (Graphics2D) g2.create();
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                          RenderingHints.VALUE_ANTIALIAS_ON);
      g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
			  RenderingHints.VALUE_STROKE_PURE);

      double lineW = getLineWidth();
      double gap = getLineGap() + lineW;
      Color saveColor = g2.getColor();

      if(widths.isEmpty() && gaps.isEmpty() && rotations.isEmpty() || gap == 0) {
         g2.fill(clip);
         g2.dispose();
         return;
      }

      g2.setStroke(new BasicStroke(getLineWidth()));
      g2.clip(clip);

      g2.setColor(Color.white);
      g2.fill(clip);
      g2.setColor(saveColor);

      Rectangle2D rect = clip.getBounds2D();
      double x = rect.getX();
      double y = rect.getY();
      double w = rect.getWidth();
      double h = rect.getHeight();

      for(int i = 0; i < rotations.size(); i++) {
         double rotation =  getLineRotation(i);
         boolean ver = rotation != 0 && rotation % (Math.PI / 2) < 0.01;

         if(ver) {
            float[] dashes = { (float) lineW, (float) gap };
            BasicStroke stroke = new BasicStroke(
               (float) h, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, dashes, 0);
            g2.setStroke(stroke);
            g2.draw(new Line2D.Double(x + gap, y + (h / 2), x + w, y + (h / 2)));

            continue;
         }

         double normalRotation = rotation % (2 * Math.PI);

         if(normalRotation < 0) {
            normalRotation += 2 * Math.PI;
         }

         double lw;
         double x1;
         double y1;
         double x2;
         double y2;

         if(normalRotation >= 0 && normalRotation < Math.PI / 2 ||
            normalRotation >= Math.PI && normalRotation < 3 * Math.PI / 2)
         {
            double theta;

            if(normalRotation < Math.PI / 2) {
               theta = normalRotation;
            }
            else {
               theta = normalRotation - Math.PI;
            }

            x1 = x;
            y1 = y + h;

            if(w <= h) {
               double w2 = Math.abs(h / Math.tan(theta));
               lw = 2 * Math.abs(h / Math.sin(theta));
               x2 = x + w2;
               y2 = y;
            }
            else {
               double h2 = Math.abs(Math.tan(theta) * w);
               lw = 2 * Math.abs(w / Math.cos(theta));
               x2 = x + w;
               y2 = y + h - h2;
            }
         }
         else {
            double theta;

            if(normalRotation < Math.PI) {
               theta = normalRotation - Math.PI / 2;
            }
            else {
               theta = normalRotation - 3 * Math.PI / 2;
            }

            x1 = x;
            y1 = y;

            if(w <= h) {
               double w2 = Math.abs(h / Math.tan(theta));
               lw = 2 * Math.abs(h / Math.sin(theta));
               x2 = x + w2;
               y2 = y + h;
            }
            else {
               double h2 = Math.abs(Math.tan(theta) * w);
               lw = 2 * Math.abs(w / Math.cos(theta));
               x2 = x + w;
               y2 = y + h2;
            }
         }

         float[] dashes = { (float) lineW, (float) gap };
         BasicStroke stroke = new BasicStroke(
            (float) lw, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, dashes, 0);
         g2.setStroke(stroke);
         g2.draw(new Line2D.Double(x1, y1, x2, y2));
      }

      g2.dispose();
   }

   /**
    * Create a copy of this object.
    * @return a copy of this object.
    */
   @Override
   public Object clone() {
      try {
         return super.clone();
      }
      catch(Exception ex) {
         LOG.error("Failed to clone texture", ex);
      }

      return null;
   }

   @Override
   public boolean equals(Object obj) {
      if(!(obj instanceof GTexture texture)) {
         return false;
      }

      return gaps.equals(texture.gaps) && widths.equals(texture.widths) &&
         rotations.equals(texture.rotations);
   }

   @Override
   public int hashCode() {
      return gaps.hashCode() + widths.hashCode() + rotations.hashCode();
   }

   public String toString() {
      return "GTexture[" + gaps + ", " + widths + ", " + rotations + "]";
   }

   private final java.util.List<Integer> gaps = new ArrayList<>();
   private final java.util.List<Integer> widths = new ArrayList<>();
   private final java.util.List<Double> rotations = new ArrayList<>();

   @Serial
   private static final long serialVersionUID = 1L;
   private static final Logger LOG = LoggerFactory.getLogger(GTexture.class);
}
