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
package inetsoft.graph.internal.text;

import inetsoft.graph.VGraph;
import inetsoft.graph.coord.Coordinate;
import inetsoft.graph.coord.PolarCoord;
import inetsoft.graph.visual.ArcVOText;

import java.awt.geom.Rectangle2D;

/**
 * Arc movement labels.
 */
public class ArcHelper extends LabelHelper {
   public ArcHelper(ArcVOText label, VGraph vgraph) {
      super(label, vgraph);
      setCheckVO(false);
      extent = (float) label.getExtent();
      startAngle = (float) Math.toDegrees(label.getAngle() - extent / 2);
      endAngle = (float) Math.toDegrees(label.getAngle() + extent / 2);
      // extend to the start and end of the quadrant.
      startAngle = (float) (Math.floor(startAngle / 90) * 90);
      endAngle = (float) (Math.ceil(endAngle / 90) * 90);
   }

   /**
    * Reset the resistance values.
    */
   @Override
   public void reset() {
      super.reset();
      resistances[0] = 0;
      resistances[1] = 0;
   }

   /**
    * Calculate the resistance between the two labels.
    */
   @Override
   public void calc(LayoutHelper text) {
      if(!(text instanceof ArcHelper)) {
         return;
      }

      double angle = getAngle();
      double angle2 = ((ArcHelper) text).getAngle();
      int n = (angle < angle2) ? 0 : 1;
      double resist = calcResistance(text, n);
      resistances[n] = Math.max(resistances[n], resist);
   }

   @Override
   public void postCalc() {
      if(!isLabelMoveable()) {
         return;
      }

      // calculate resistance to the angle range.
      double angle = getAngle();

      if(angle < endAngle) {
         resistances[0] = Math.max(resistances[0], getResistance(endAngle - angle));
      }

      if(angle > startAngle) {
         resistances[1] = Math.max(resistances[1], getResistance(angle - startAngle));
      }

      if(!isContained(bounds)) {
         Rectangle2D lbounds = getBounds();

         // if not enough space, moving to narrower space (instead of the corner) will
         // make it impossible to fit, so we increase the resistance to that direction.
         if(angle >= 0 && angle < 90) {
            // if the label is out of bounds on top, make to move clockwise.
            if(lbounds.getMaxY() > bounds.getMaxY()) {
               resistances[0] = Math.max(resistances[0], OVERLAP_RESISTANCE * 10);
            }
            // otherwise make counter clockwise to seek more space.
            else {
               resistances[1] = Math.max(resistances[1], OVERLAP_RESISTANCE * 10);
            }

            // similar logic for other quadrants
         }
         else if(angle >= 90 && angle < 180) {
            if(lbounds.getMaxY() > bounds.getMaxY()) {
               resistances[1] = Math.max(resistances[1], OVERLAP_RESISTANCE * 10);
            }
            else {
               resistances[0] = Math.max(resistances[0], OVERLAP_RESISTANCE * 10);
            }
         }
         else if(angle >= 180 && angle < 270) {
            if(lbounds.getMinY() < bounds.getMinY()) {
               resistances[0] = Math.max(resistances[0], OVERLAP_RESISTANCE * 10);
            }
            else {
               resistances[1] = Math.max(resistances[1], OVERLAP_RESISTANCE * 10);
            }
         }
         else { // > 270 && < 360
            if(lbounds.getMinY() < bounds.getMinY()) {
               resistances[1] = Math.max(resistances[1], OVERLAP_RESISTANCE * 10);
            }
            else {
               resistances[0] = Math.max(resistances[0], OVERLAP_RESISTANCE * 10);
            }
         }
      }
   }

   /**
    * Calculate the resistence level between two labels.
    */
   private double calcResistance(LayoutHelper text, int direction) {
      if(overlaps(text, 0)) {
         if(isLabelMoveable()) {
            return OVERLAP_RESISTANCE;
         }

         return MAX_RESISTANCE;
      }

      double angle = getAngle();
      ArcHelper text2 = (ArcHelper) text;
      // if moving labels freely, use the other end of the angle (quadrant) range.
      double angle2 = isLabelMoveable() ? (direction == 0 ? text2.endAngle : text2.startAngle)
         : text2.getAngle();

      return getResistance(angle2 - angle);
   }

   private boolean isLabelMoveable() {
      Coordinate coord = vgraph.getCoordinate();
      return coord instanceof PolarCoord && ((PolarCoord) coord).isLabelMoveable();
   }

   /**
    * Get the resistance level for the angle between two texts.
    */
   private double getResistance(double angle) {
      angle = Math.abs(angle) + 1;
      return BASE_RESISTANCE * 1000 - angle * angle;
   }

   /**
    * Get the minimum resistance of all possible directions.
    */
   @Override
   public double getMinResistance() {
      return Math.min(resistances[0], resistances[1]);
   }

   @Override
   public boolean isContained(Rectangle2D pbounds) {
      if(isLabelMoveable()) {
         return super.isContained(pbounds);
      }

      // allow sides to be clipped in pie
      Rectangle2D bounds = getBounds();
      final int moe = 2; // margin of error
      return bounds.getMaxY() - moe < pbounds.getMaxY() &&
         bounds.getMinY() + moe >= pbounds.getMinY();
   }

   /**
    * Move the label in the direction with the least resistance.
    */
   @Override
   public void move() {
      Object ob = getBounds();
      super.move();

      double degree = (resistances[0] < resistances[1]) ? STEP : -STEP;
      move(degree);
   }

   /**
    * Move label at specified angle.
    */
   private void move(double degree) {
      ArcVOText label = (ArcVOText) getLabel();
      label.move(degree);
      super.reset();
   }

   /**
    * Get the angle from the center of the arc, in degrees.
    */
   public double getAngle() {
      return Math.toDegrees(((ArcVOText) getLabel()).getAngle()) % 360;
   }

   /**
    * Get the original angle (before moving), in degrees.
    */
   public double getOrigAngle() {
      return Math.toDegrees(((ArcVOText) getLabel()).getOrigAngle()) % 360;
   }

   /**
    * Call when an overlapped text is found.
    */
   @Override
   public void processOverlapped(LayoutHelper helper) {
      if(!(helper instanceof ArcHelper)) {
         return;
      }

      // see FreeHelper
      double a1 = getAngle();
      double a2 = ((ArcHelper) helper).getAngle();

      if(a1 == a2) {
         move(-0.1);
      }
   }

   public String toString() {
      return getLabel().getText() + "@" + getAngle();
   }

   @Override
   public double getImportance() {
      return Math.abs(extent);
   }

   private static final double STEP = 1;
   // resistance at counter-clockwise and clockwise
   private double[] resistances = new double[2];
   // the starte/end angle of corresponding pie slice, in degrees.
   private float startAngle, endAngle;
   private float extent; // radian
}
