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
package inetsoft.graph.aesthetic;

import com.inetsoft.build.tern.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
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
      int gap = gaps.size() == 0 ? 0 : gaps.get(0);

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
      return widths.size() == 0 ? 0 : widths.get(0);
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

      if(widths.size() == 0 && gaps.size() == 0 && rotations.size() == 0 ||
         gap == 0)
      {
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
            for(double x2 = x + gap; x2 < x + w; x2 += gap) {
               g2.draw(new Line2D.Double(x2, y, x2, y + h));
            }

            continue;
         }

         double yInc = w * Math.tan(rotation);
         // extra to make sure the entire area is covered
         double extra = Math.abs(yInc);
         double sin2 = Math.sin(Math.PI / 2 - rotation);
         double gap2 = (sin2 == 0) ? gap : Math.abs(gap / sin2);

         for(double y2 = y - extra; y2 < y + h + extra; y2 += gap2) {
            g2.draw(new Line2D.Double(x, y2, x + w, y2 + yInc));
         }
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
      if(!(obj instanceof GTexture)) {
         return false;
      }

      GTexture texture = (GTexture) obj;

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

   private ArrayList<Integer> gaps = new ArrayList<>();
   private ArrayList<Integer> widths = new ArrayList<>();
   private ArrayList<Double> rotations = new ArrayList<>();

   private static final long serialVersionUID = 1L;
   private static final Logger LOG = LoggerFactory.getLogger(GTexture.class);
}
