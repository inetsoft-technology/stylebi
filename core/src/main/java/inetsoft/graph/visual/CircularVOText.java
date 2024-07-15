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

import inetsoft.graph.data.DataSet;
import inetsoft.graph.geometry.Geometry;
import inetsoft.graph.guide.VLabel;
import inetsoft.graph.internal.*;
import inetsoft.report.internal.Common;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;

import java.awt.*;
import java.awt.geom.*;
import java.util.Arrays;

/**
 * Text drawn on a circle.
 *
 * @version 12.3
 * @author InetSoft Technology
 */
public class CircularVOText extends VOText {
   /**
    * Constructor.
    * @param label the output string.
    * @param vo the graph element this text is a label of.
    * @param mname measure name.
    */
   public CircularVOText(Object label, ElementVO vo, String mname,
                         DataSet dataset, int row, Geometry gobj)
   {
      super(label, vo, mname, dataset, row, gobj);
      setCollisionModifier(VLabel.MOVE_NONE);
   }

   /**
    * Set the arc this label is attached to.
    * @param cx center x.
    * @param cy center y.
    * @param outerR outer circle radius.
    * @param innerR inner circle radius.
    * @param angle starting angle in radian.
    * @param extent extent in radian.
    */
   public void setCircle(double cx, double cy, double outerR, double innerR, double angle,
                         double extent)
   {
      this.cx = cx;
      this.cy = cy;
      this.outerR = outerR;
      this.innerR = innerR;
      this.angle = angle;
      this.extent = extent;

      // normalize for easier calculation
      if(this.extent < 0) {
         this.angle += this.extent;
         this.extent = -this.extent;
      }

      while(this.angle < 0) {
         this.angle += Math.PI * 2;
      }
   }

   /**
    * Get the angle extent that is needed to fit text.
    * @return angle extent in degrees.
    */
   public double getPreferredAngle() {
      double r = (outerR + innerR) / 2;
      return Math.toDegrees(getPreferredWidth() / r);
   }

   /**
    * Get the angle from the center.
    */
   public double getAngle() {
      return angle;
   }

   /**
    * Check if label should be drawn from center to outer edge.
    */
   public boolean isRadial() {
      return radial;
   }

   /**
    * Set whether the label should be drawn from center to the edge, or as circular text around
    * the center.
    */
   public void setRadial(boolean radial) {
      this.radial = radial;
   }

   /**
    * Paint the visual object on the graphics.
    */
   @Override
   public void paint(Graphics2D g) {
      if(radial) {
         paintRadial(g);
      }
      else {
         paintArc(g);
      }
   }

   // paint text circular around the center.
   private void paintArc(Graphics2D g) {
      // don't use g.getFontMetrics. openjdk returns negative values when g is transformed.
      FontMetrics fm = GTool.getFontMetrics(g.getFont());
      double fontH = fm.getHeight();

      // don't paint out of bounds
      if(outerR - innerR < fontH * 0.8) {
         return;
      }

      Shape oclip = g.getClip();
      double radius = (outerR + innerR) / 2;
      Color bg = getTextSpec().getBackground();
      double innerR = Math.round(radius - fontH / 2 - 1);
      double outerR = Math.round(radius + fontH / 2 + 1);
      double mid = angle + extent / 2;
      double maxExtent = Math.asin(fm.stringWidth(getText()) * 1.5 / radius);
      double extent2 = Double.isNaN(maxExtent) ? extent : Math.min(extent, maxExtent);
      Donut donut = new Donut(cx - outerR, cy - outerR, outerR * 2, outerR * 2,
                              innerR * 2, innerR * 2, Math.toDegrees(mid - extent2 / 2),
                              Math.toDegrees(extent2));

      g.clip(donut);

      if(bg != null) {
         g.setColor(bg);
         g.fill(donut);
      }

      g.setColor(getColor());
      g.setFont(getFont());
      PolarUtil.drawString(g, new Point2D.Double(cx, cy), angle + extent / 2, extent,
                           radius, getText());
      g.setClip(oclip);
   }

   // paint text from the center to edge.
   private void paintRadial(Graphics2D g) {
      g = (Graphics2D) g.create();
      g.setFont(getFont());
      g.setColor(getColor());
      FontMetrics fm = GTool.getFontMetrics(g.getFont());
      boolean leftSide = 100 * Math.cos(angle + extent / 2) <= 0;
      double strWidth = GTool.stringWidth(new String[] { getText() }, g.getFont());
      double outerR = this.outerR - 2;
      double innerR = this.innerR + 2;
      // mid point of the middle of the string
      double midR = innerR + (outerR - innerR) / 2;
      // the height of the space available at the middle of the string
      double midH = midR * Math.sin(extent / 2) * 2;
      double fontH = fm.getHeight();

      // ignore label if the space is too small.
      if(midH < fontH * 0.95) {
         g.dispose();
         return;
      }

      g.clip(new Donut(cx - outerR, cy - outerR, outerR * 2, outerR * 2, innerR * 2, innerR * 2));
      g.translate(cx, cy);
      g.rotate(-angle - extent / 2);
      double tx = innerR + Math.max(0, (outerR - innerR - strWidth) / 2);
      // y is the mid point of the slice, move it down to the baseline so text is centered.
      g.translate(tx, -(fm.getAscent() - fontH / 2));

      // if on left side of the half, draw from left to right (edge to center).
      if(leftSide) {
         // this will be flipped, so the mid point needs to be adjusted.
         g.translate(Math.min(strWidth, outerR - innerR), fm.getAscent() - fm.getDescent());
         g.rotate(Math.PI);
      }

      GTool.drawString(g, getText(), 0, 0);
      g.dispose();
   }

   // get the bounding polygon
   public Polygon getPolyBounds() {
      return radial ? getRadialPolyBounds() : getArcPolyBounds();
   }

   // get the bounds for radial text.
   private Polygon getRadialPolyBounds() {
      FontMetrics fm = GTool.getFontMetrics(getFont());
      double strWidth = GTool.stringWidth(new String[] { getText() }, getFont());
      int fontH = fm.getHeight();
      int maxDescent = fm.getMaxDescent();
      double tx = innerR + Math.max(0, (outerR - innerR - strWidth) / 2);
      Rectangle2D box = new Rectangle2D.Double(0, -maxDescent,
                                               Math.min(strWidth, outerR - innerR), fontH);
      AffineTransform trans = new AffineTransform();
      trans.translate(cx, cy);
      trans.rotate(-angle - extent / 2);
      trans.translate(tx, -maxDescent);
      Point2D[] corners = {
         new Point2D.Double(box.getMinX(), box.getMinY()),
         new Point2D.Double(box.getMinX(), box.getMaxY()),
         new Point2D.Double(box.getMaxX(), box.getMaxY()),
         new Point2D.Double(box.getMaxX(), box.getMinY())
      };

      for(int i = 0; i < corners.length; i++) {
         corners[i] = trans.transform(corners[i], null);
      }

      return new Polygon(Arrays.stream(corners).mapToInt(c -> (int) c.getX()).toArray(),
                         Arrays.stream(corners).mapToInt(c -> (int) c.getY()).toArray(), 4);
   }

   // get the bounds for circular text.
   private Polygon getArcPolyBounds() {
      Font fn = getFont();
      FontMetrics fm = Common.getFontMetrics(fn);
      double fontH = fm.getHeight();
      double radius = (innerR + outerR) / 2;
      double sin = fm.stringWidth(getText()) * 1.5 / radius;
      double extent0 = 0;

      while(sin > 1) {
         sin -= 1;
         extent0 += Math.PI / 2;
      }

      double extent2 = Math.asin(sin) + extent0;
      double mid = angle + this.extent / 2;
      double extent = Math.min(this.extent, extent2);
      double angle = mid - extent / 2;

      if(radius == 0 || extent == 0) {
         return null;
      }

      double innerR = Math.round(radius - fontH / 2 - 1);
      double outerR = Math.round(radius + fontH / 2 + 1);
      IntList xs = new IntArrayList();
      IntList ys = new IntArrayList();
      int n = 0;

      for(double a = 0; a <= extent; a += Math.PI / 90, n++) {
         if(a > extent - Math.PI / 90) {
            a = extent;
         }

         // inner
         double x1 = cx + Math.cos(angle + a) * innerR;
         double y1 = cy - Math.sin(angle + a) * innerR;
         // outer
         double x2 = cx + Math.cos(angle + a) * outerR;
         double y2 = cy - Math.sin(angle + a) * outerR;

         xs.add(n, (int) x1);
         ys.add(n, (int) y1);
         xs.add(xs.size() - n, (int) x2);
         ys.add(ys.size() - n, (int) y2);
      }

      if(xs.size() > 0 && ys.size() > 0) {
         xs.add(xs.get(0));
         ys.add(ys.get(0));
      }

      return new Polygon(xs.toIntArray(), ys.toIntArray(), xs.size());
   }

   private double cx, cy, outerR, innerR, angle, extent;
   private boolean radial = false;
}
