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
package inetsoft.graph.internal.text;

import inetsoft.graph.*;
import inetsoft.graph.element.GraphElement;
import inetsoft.graph.guide.VLabel;
import inetsoft.graph.visual.FormLabel;
import inetsoft.graph.visual.VOText;
import inetsoft.util.CoreTool;

import java.awt.*;
import java.awt.geom.*;

/**
 * Helper for a vlabel.
 */
public abstract class LayoutHelper implements Comparable {
   public LayoutHelper(Visualizable vo, VGraph vgraph) {
      this.vo = vo;
      this.vgraph = vgraph;
      this.bounds = vgraph.getPlotBounds();

      Graphable elem = vo.getGraphable();

      if(elem == null && vo instanceof VOText) {
         elem = ((VOText) vo).getGraphElement();
      }

      overlay = (elem != null) ? elem.getHint("overlay") : null;
      clipped = elem != null && "true".equals(elem.getHint(GraphElement.HINT_CLIP));
   }

   /**
    * Set if the label can be removed.
    */
   public void setRemovable(boolean removable) {
      this.removable = removable;
   }

   /**
    * Check if the label can be removed.
    */
   public boolean isRemovable() {
      return removable;
   }

   /**
    * Check if the associated element is clipped.
    */
   public boolean isClipped() {
      return clipped;
   }

   /**
    * Set whether to check overlapping with element vos.
    */
   public void setCheckVO(boolean checkVO) {
      this.checkVO = checkVO;
   }

   /**
    * Check whether to check overlapping with element vos.
    */
   public boolean isCheckVO() {
      return checkVO;
   }

   /**
    * Get the center of the label.
    */
   public Point2D getCenter() {
      if(center == null) {
         Rectangle2D box = getBounds();
         center = new Point2D.Double(box.getCenterX(), box.getCenterY());
      }

      return center;
   }

   /**
    * Get label bounds.
    */
   public Rectangle2D getBounds() {
      if(lbounds == null) {
         Shape shp = getShape();
         lbounds = (shp instanceof Rectangle2D) ? (Rectangle2D) shp : shp.getBounds2D();

         // tags have borders and any overlapping is easily visible, increase spacing. (54713)
         if(getVisualizable() instanceof FormLabel) {
            lbounds = new Rectangle2D.Double(lbounds.getX() - 2, lbounds.getY() - 2,
                                             lbounds.getWidth() + 4, lbounds.getHeight() + 4);
         }
      }

      return lbounds;
   }

   /**
    * Compare the minimum resistance.
    */
   @Override
   public int compareTo(Object obj) {
      LayoutHelper holder = (LayoutHelper) obj;
      double min1 = getMinResistance();
      double min2 = holder.getMinResistance();

      if(min1 < min2) {
         return -1;
      }
      else if(min1 > min2) {
         return 1;
      }

      return 0;
   }

   /**
    * Reset the resistance level.
    */
   public void reset() {
      area = null;
   }

   /**
    * Get the actual bounds on screen.
    */
   protected Shape getTransformedBounds() {
      return getShape();
   }

   /**
    * Get the shape of the vo.
    */
   protected Shape getShape() {
      return vo.getBounds();
   }

   /**
    * Check if this label overlaps the other label.
    * @param moe margin of error. The amount of overlapping to ignore.
    */
   public boolean overlaps(LayoutHelper holder, double moe) {
      if(!CoreTool.equals(overlay, holder.overlay)) {
         return false;
      }

      Rectangle2D rect1 = getBounds();
      Rectangle2D rect2 = holder.getBounds();

      if(rect1.intersects(rect2)) {
         double yadj = (checkVO && holder.isCheckVO()) ? -0.5 : 3;

         // optimization, Shape.intersect() is very expensive
         rect1 = new Rectangle2D.Double(rect1.getX() + (moe + 1) / 2,
                                        rect1.getY() + (moe + yadj) / 2,
                                        rect1.getWidth() - (moe + 1),
                                        rect1.getHeight() - (moe + yadj));
         rect2 = new Rectangle2D.Double(rect2.getX() + (moe + 1) / 2,
                                        rect2.getY() + (moe + yadj) / 2,
                                        rect2.getWidth() - (moe + 1),
                                        rect2.getHeight() - (moe + yadj));

         return rect1.intersects(rect2);
         // init();
         // holder.init();

         //Area a1 = (Area) area.clone();
         //a1.intersect(holder.area);

         //Rectangle2D box = a1.getBounds2D();
         // if tag (with border), don't allow any overlapping, otherwise
         // allow top/bottom to overlap a little since the text normally
         // doesn't fill the full height

         //return box.getWidth() > moe + 1 && box.getHeight() > moe + yadj;
      }

      return false;
   }

   /**
    * Check if this label is in plot bounds.
    */
   public boolean isContained(Rectangle2D pbounds) {
      return !getBounds().intersects(pbounds);
   }

   /**
    * Get the visual object.
    */
   public Visualizable getVisualizable() {
      return vo;
   }

   /**
    * Get the option from the VOText.
    */
   public int getCollisionModifier() {
      return VLabel.MOVE_NONE;
   }

   /**
    * Calculate the resistance between the two labels.
    */
   public abstract void calc(LayoutHelper text);

   /**
    * Get the minimum resistance of all possible directions.
    */
   public abstract double getMinResistance();

   /**
    * Call when an overlapped text is found.
    */
   public abstract void processOverlapped(LayoutHelper helper);

   /**
    * Move the label in the direction with the least resistance.
    */
   public void move() {
      center = null;
      lbounds = null;
   }

   /**
    * Called after calc() has been called on all other helpers in one iteration.
    */
   public void postCalc() {
      // no-op
   }

   /**
    * Get the relative importance of this label.
    */
   public double getImportance() {
      return 1;
   }

   /**
    * Check if the label should be moved inside plot.
    */
   public boolean needsMoveInside() {
      return false;
   }

   protected static final double BASE_RESISTANCE = 1000.0;
   protected static final double OVERLAP_RESISTANCE = BASE_RESISTANCE * 1000;
   protected static final double MAX_RESISTANCE = OVERLAP_RESISTANCE * 1000;

   protected Rectangle2D bounds;
   protected VGraph vgraph;
   private Point2D center;
   private Rectangle2D lbounds; // label bounds
   private Visualizable vo;
   private Area area; // label area
   private boolean removable = true;
   private boolean checkVO = true;
   private boolean clipped = false;
   transient Object overlay;
}
