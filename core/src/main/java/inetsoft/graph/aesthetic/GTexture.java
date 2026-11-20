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
import java.awt.geom.PathIterator;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RectangularShape;
import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Objects;

/**
 * The GTexture class is the base class for all texture aesthetics.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
@TernClass(url = "#cshid=GTexture")
public class GTexture implements Cloneable, Serializable {
   private static final int WIDTH0 = 1;
   // the white margin, in pixels, between a built-in pattern and the edge of the shape
   private static final double PATTERN_MARGIN = 2;
   // a path of one piece with no more segments than this is a simple shape, such as a bar or slice
   private static final int MAX_SIMPLE_SEGMENTS = 8;
   /**
    * Pattern0.
    */
   @TernField
   public static final GTexture PATTERN_0 =
      new GTexture(TexturePattern.values()[0]);

   /**
    * Pattern1.
    */
   @TernField
   public static final GTexture PATTERN_1 =
      new GTexture(TexturePattern.values()[1]);

   /**
    * Pattern2.
    */
   @TernField
   public static final GTexture PATTERN_2 =
      new GTexture(TexturePattern.values()[2]);

   /**
    * Pattern3.
    */
   @TernField
   public static final GTexture PATTERN_3 =
      new GTexture(TexturePattern.values()[3]);

   /**
    * Pattern4.
    */
   @TernField
   public static final GTexture PATTERN_4 =
      new GTexture(TexturePattern.values()[4]);

   /**
    * Pattern5.
    */
   @TernField
   public static final GTexture PATTERN_5 =
      new GTexture(TexturePattern.values()[5]);

   /**
    * Pattern6.
    */
   @TernField
   public static final GTexture PATTERN_6 =
      new GTexture(TexturePattern.values()[6]);

   /**
    * Pattern7.
    */
   @TernField
   public static final GTexture PATTERN_7 =
      new GTexture(TexturePattern.values()[7]);

   /**
    * Pattern8.
    */
   @TernField
   public static final GTexture PATTERN_8 =
      new GTexture(TexturePattern.values()[8]);

   /**
    * Pattern9.
    */
   @TernField
   public static final GTexture PATTERN_9 =
      new GTexture(TexturePattern.values()[9]);

   /**
    * Pattern10.
    */
   @TernField
   public static final GTexture PATTERN_10 =
      new GTexture(TexturePattern.values()[10]);

   /**
    * Pattern11.
    */
   @TernField
   public static final GTexture PATTERN_11 =
      new GTexture(TexturePattern.values()[11]);

   /**
    * Pattern12.
    */
   @TernField
   public static final GTexture PATTERN_12 =
      new GTexture(TexturePattern.values()[12]);

   /**
    * Pattern13.
    */
   @TernField
   public static final GTexture PATTERN_13 =
      new GTexture(TexturePattern.values()[13]);

   /**
    * Pattern14.
    */
   @TernField
   public static final GTexture PATTERN_14 =
      new GTexture(TexturePattern.values()[14]);

   /**
    * Pattern15.
    */
   @TernField
   public static final GTexture PATTERN_15 =
      new GTexture(TexturePattern.values()[15]);

   /**
    * Pattern16.
    */
   @TernField
   public static final GTexture PATTERN_16 =
      new GTexture(TexturePattern.values()[16]);

   /**
    * Pattern17.
    */
   @TernField
   public static final GTexture PATTERN_17 =
      new GTexture(TexturePattern.values()[17]);

   /**
    * Pattern18.
    */
   @TernField
   public static final GTexture PATTERN_18 =
      new GTexture(TexturePattern.values()[18]);

   /**
    * Pattern19.
    */
   @TernField
   public static final GTexture PATTERN_19 =
      new GTexture(TexturePattern.values()[19]);

   /**
    * Create a solid fill.
    */
   public GTexture() {
   }

   /**
    * Create one of the built-in patterns.
    */
   private GTexture(TexturePattern pattern) {
      this.pattern = pattern;
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

      if(pattern != null) {
         paintPattern(g2, clip);
         g2.dispose();
         return;
      }

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
    * Fill the shape with the tiles of the built-in pattern, starting from the bottom-left corner
    * of the shape, less a thin white margin. The margin keeps a bold line or a block of the
    * pattern from running into the border drawn around the shape. It is drawn as a white stroke
    * along the outline, so it works the same for a rectangle, a slice or an area, and costs no
    * more than outlining the shape.
    */
   private void paintPattern(Graphics2D g2, Shape clip) {
      Color color = g2.getColor();
      g2.clip(clip);
      g2.setColor(Color.white);
      g2.fill(clip);
      g2.setColor(color);
      g2.setStroke(TexturePattern.LINE);

      Rectangle2D rect = clip.getBounds2D();
      Rectangle visible = g2.getClipBounds();

      if(visible == null) {
         return;
      }

      // a small shape, such as a legend swatch, gets a smaller margin so it still shows the
      // pattern
      double margin = Math.min(PATTERN_MARGIN, 0.12 * Math.min(rect.getWidth(), rect.getHeight()));
      margin = margin < 0.25 || !hasSimpleOutline(clip) ? 0 : margin;

      double tw = pattern.getTileWidth();
      double th = pattern.getTileHeight();
      double left = rect.getX() + margin;
      double bottom = rect.getMaxY() - margin;
      // only the tiles that reach the visible part of the shape
      int c0 = (int) Math.max(0, Math.floor((visible.getMinX() - left) / tw));
      int c1 = (int) Math.ceil((visible.getMaxX() - left) / tw);
      int r0 = (int) Math.max(0, Math.floor((bottom - visible.getMaxY()) / th));
      int r1 = (int) Math.ceil((bottom - visible.getMinY()) / th);

      for(int r = r0; r < r1; r++) {
         double y = bottom - (r + 1) * th;

         for(int c = c0; c < c1; c++) {
            pattern.paintTile(g2, left + c * tw, y);
         }
      }

      if(margin > 0) {
         // the stroke is centered on the outline; the half outside the shape is clipped away
         g2.setColor(Color.white);
         g2.setStroke(new BasicStroke((float) (2 * margin)));
         g2.draw(clip);
      }
   }

   /**
    * Check if stroking the shape draws only its outline. A rectangle, an ellipse, an arc or a
    * small polygon does. A large shape built from many pieces, such as an area chart's fill (one
    * path of appended segments, filled with the non-zero rule), also has the edges between its
    * pieces, which a stroke would draw across the shape.
    */
   private static boolean hasSimpleOutline(Shape shape) {
      if(shape instanceof RectangularShape) {
         return true;
      }

      int moves = 0;
      int segments = 0;

      for(PathIterator it = shape.getPathIterator(null); !it.isDone(); it.next()) {
         double[] coords = new double[6];
         int type = it.currentSegment(coords);

         if(type == PathIterator.SEG_MOVETO) {
            moves++;
         }
         else if(type != PathIterator.SEG_CLOSE) {
            segments++;
         }

         if(moves > 1 || segments > MAX_SIMPLE_SEGMENTS) {
            return false;
         }
      }

      return true;
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

      return pattern == texture.pattern && gaps.equals(texture.gaps) &&
         widths.equals(texture.widths) && rotations.equals(texture.rotations);
   }

   @Override
   public int hashCode() {
      return gaps.hashCode() + widths.hashCode() + rotations.hashCode() +
         Objects.hashCode(pattern);
   }

   public String toString() {
      return pattern != null ? "GTexture[" + pattern + "]" :
         "GTexture[" + gaps + ", " + widths + ", " + rotations + "]";
   }

   private final java.util.List<Integer> gaps = new ArrayList<>();
   private final java.util.List<Integer> widths = new ArrayList<>();
   private final java.util.List<Double> rotations = new ArrayList<>();
   // the built-in pattern, or null for a solid fill or line texture
   private TexturePattern pattern;

   @Serial
   private static final long serialVersionUID = 1L;
   private static final Logger LOG = LoggerFactory.getLogger(GTexture.class);
}
