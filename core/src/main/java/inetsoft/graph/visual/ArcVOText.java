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

import inetsoft.graph.data.DataSet;
import inetsoft.graph.geometry.Geometry;
import inetsoft.graph.guide.VLabel;
import inetsoft.graph.internal.GTool;

import java.awt.*;
import java.awt.geom.*;
import java.util.List;
import java.util.*;

/**
 * This class defines a text object around a circular shape anchored at an angle and radius.
 *
 * @version 10.0
 * @author InetSoft Technology
 */
public class ArcVOText extends VOText {
   /**
    * Constructor.
    * @param label the output string.
    * @param vo the graph element this text is a label of.
    * @param mname measure name.
    */
   public ArcVOText(Object label, ElementVO vo, String mname, DataSet data,
                    int row, Geometry gobj)
   {
      super(label, vo, mname, data, row, gobj);
      setCollisionModifier(VLabel.MOVE_ARC);
   }

   /**
    * Set the arc this label is attached to.
    * @param cx center x.
    * @param cy center y.
    * @param wr horizontal radius.
    * @param hr vertical radius.
    */
   public void setArc(double cx, double cy, double wr, double hr, double depth) {
      this.cx = cx;
      this.cy = cy;
      this.wr = wr;
      this.hr = hr;
      this.depth = depth;
   }

   /**
    * Get the angle from the center (radian).
    */
   public double getAngle() {
      return angle;
   }

   /**
    * Get the original angle before the text layout manager changes label positions (radian).
    */
   public double getOrigAngle() {
      return angle - moved;
   }

   /**
    * Get the extent of the corresponding pie slice (radian).
    */
   public double getExtent() {
      return extent;
   }

   /**
    * Set the extent of the corresponding pie slice (radian).
    */
   public void setExtent(double extent) {
      this.extent = (float) extent;
   }

   /**
    * Layout the text around an arc.
    * @param angle the angle from the center.
    */
   public void layout(double angle) {
      this.angle = angle;

      double xflag = 0;
      double yflag = 0;

      if(Math.cos(angle) < 0) {
         if(Math.abs(Math.cos(angle)) < 0.1) {
            xflag = -0.5;
         }
         else { 
            xflag = -1;
         }
      }

      if(Math.sin(angle) < 0) {
         yflag = -1;
      }

      Point2D pt = new Point2D.Double(Math.cos(angle) * wr + cx,
                                      Math.sin(angle) * hr + cy + yflag*depth);
      double textx = pt.getX() + xflag * getSize().getWidth();
      double texty = pt.getY() + yflag * getSize().getHeight();
      setPosition(new Point2D.Double(textx, texty));
      Point2D offset = getRotationOffset(pt, angle);
      setOffset(offset);
   }

   /**
    * Move label at specified angle.
    */
   public void move(double degree) {
      double move = Math.toRadians(degree);
      double angle = getAngle() + move;
      layout(angle);

      if(moved == 0) {
         anchor = findCorner();
      }
      
      moved += move;
   }

   /**
    * Paint the visual object on the graphics.
    */
   @Override
   public void paint(Graphics2D g) {
      super.paint(g);
      Graphics2D g2 = (Graphics2D) g.create();

      if(Math.abs(moved) > Math.abs(extent) / 3) {
         drawLeaderLine(g2);
      }

      g2.dispose();
   }

   private void drawLeaderLine(Graphics2D g2) {
      Point2D pt = findTagPosition();

      if(pt != null) {
         g2.setColor(GTool.getColor(getColor(), 0.6));
         g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

         if(depth == 0 && Math.abs(anchor.getX() - pt.getX()) > 3) {
            Point2D anchorLeader = anchor;
            double d90 = Math.PI * 0.5;
            double d270 = Math.PI * 1.5;

            if(Math.abs(angle - d90) < 0.05 || Math.abs(angle - d270) < 0.05) {
               // at (close to) 90//270 degrees, don't add a stem
            }
            else if(angle > Math.PI * 0.5 && angle < Math.PI * 1.5) {
               anchorLeader = new Point2D.Double(anchor.getX() - 3, anchor.getY());
            }
            else {
               anchorLeader = new Point2D.Double(anchor.getX() + 3, anchor.getY());
            }

            g2.draw(new Line2D.Double(anchorLeader, pt));
            g2.draw(new Line2D.Double(anchorLeader, anchor));
         }
         else {
            g2.draw(new Line2D.Double(anchor, pt));
         }
      }
   }

   /**
    * Find the point closes to center.
    */
   private Point2D findCorner() {
      return new Point2D.Double(cx + wr * Math.cos(angle), cy + hr * Math.sin(angle));
   }

   /**
    * Find the line to draw from the label to its original position.
    */
   private Point2D findTagPosition() {
      if(anchor == null) {
         return null;
      }
      
      Rectangle2D bounds = getBounds();
      final int GAP = 3;
      List<TagPosition> tags = new ArrayList<>();

      // add the mid points of each sides

      if(bounds.getMaxX() < anchor.getX() + GAP) {
         tags.add(new TagPosition(anchor.getX() - bounds.getMaxX(),
                                  new Point2D.Double(bounds.getMaxX(), bounds.getCenterY())));
      }

      if(bounds.getMinX() > anchor.getX() + GAP) {
         tags.add(new TagPosition(bounds.getMinX() - anchor.getX(),
                                  new Point2D.Double(bounds.getMinX(), bounds.getCenterY())));
      }

      if(bounds.getMaxY() < anchor.getY() - GAP) {
         tags.add(new TagPosition(anchor.getY() - bounds.getMaxY(),
                                  new Point2D.Double(bounds.getCenterX(), bounds.getMaxY())));
      }

      if(bounds.getMinY() > anchor.getY() + GAP) {
         tags.add(new TagPosition(bounds.getMinY() - anchor.getY(),
                                  new Point2D.Double(bounds.getCenterX(), bounds.getMinY())));
      }

      // find the point that is closest to the anchor
      Collections.sort(tags, (a, b) -> (int) (b.dist - a.dist));
      return tags.stream().findFirst().map(a -> a.position).orElse(null);
   }

   private static class TagPosition {
      public TagPosition(double dist, Point2D position) {
         this.dist = dist;
         this.position = position;
      }

      private double dist;
      private Point2D position;
   }

   // all angles in radians
   private double cx, cy, wr, hr, depth, angle;
   private double moved = 0;
   private Point2D anchor = null;
   private float extent = 0;
}
