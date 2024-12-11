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

import inetsoft.graph.GraphConstants;
import inetsoft.graph.VGraph;
import inetsoft.graph.guide.VLabel;
import inetsoft.graph.internal.GTool;
import inetsoft.graph.visual.VOText;

import java.awt.geom.*;

/**
 * Free movement labels.
 */
public class FreeHelper extends LabelHelper {
   public FreeHelper(VLabel label, VGraph vgraph) {
      super(label, vgraph);

      String maxstep = GTool.getProperty("graph.textlayout.maxstep", "1000");

      if(maxstep != null) {
         int maxval = Integer.parseInt(maxstep);
         max_steps = new int[] {maxval, maxval, maxval, maxval};
      }
   }

   /**
    * Set the maximum steps that can be moved in each direction (right, up, left, down).
    */
   public void setMaxSteps(int[] max) {
      this.max_steps = max;
   }

   /**
    * Get the maximum steps that can be moved in each direction (right, up, left, down).
    */
   public int[] getMaxSteps() {
      return max_steps;
   }

   /**
    * Set whether this label should be kept in plot.
    */
   public void setInPlot(boolean flag) {
      this.inPlot = flag;
   }

   /**
    * Check whether this label should be kept in plot.
    */
   public boolean isInPlot() {
      return inPlot;
   }

   /**
    * Reset the resistance values.
    */
   @Override
   public void reset() {
      super.reset();

      Rectangle2D box = getLabel().getBounds();

      // label bounds positions
      Point2D top = new Point2D.Double(box.getX(), box.getY() + box.getHeight());
      Point2D left = new Point2D.Double(box.getX(), box.getY());
      Point2D bottom = new Point2D.Double(box.getX(), box.getY());
      Point2D right = new Point2D.Double(box.getX() + box.getWidth(), box.getY());
      // bounds edge lines
      double bTop = bounds.getY() + bounds.getHeight();
      double bLeft = bounds.getX();
      double bBottom = bounds.getY();
      double bRight = bounds.getX() + bounds.getWidth();
      Line2D topLine = new Line2D.Double(bLeft, bTop, bRight, bTop);
      Line2D leftLine = new Line2D.Double(bLeft, bTop, bLeft, bBottom);
      Line2D bottomLine = new Line2D.Double(bLeft, bBottom, bRight, bBottom);
      Line2D rightLine = new Line2D.Double(bRight, bTop, bRight, bBottom);
      // layout may place text 'on' the edge of the bounding box. if we move it inside it may
      // overlap/touch the bar. ignore 1/2 pixel to avoid this (68364).
      final double OVERLAP_ADJ = 0.5;

      edgedists[0] = edgedists[1] = edgedists[2] = edgedists[3] = 0;

      // 0 degrees
      resistances[0] = getEdgeResistance(rightLine.ptSegDistSq(right),
                                         right.getX() - OVERLAP_ADJ, bTop - 1,
                                         GraphConstants.RIGHT);
      // 90 degrees
      resistances[1] = getEdgeResistance(topLine.ptSegDistSq(top),
                                         bLeft + 1, top.getY() - OVERLAP_ADJ,
                                         GraphConstants.TOP);
      // 180 degrees
      resistances[2] = getEdgeResistance(leftLine.ptSegDistSq(left),
                                         left.getX() + OVERLAP_ADJ, bTop - 1,
                                         GraphConstants.LEFT);
      // 270 degrees
      resistances[3] = getEdgeResistance(bottomLine.ptSegDistSq(bottom),
                                         bLeft + 1, bottom.getY() + OVERLAP_ADJ,
                                         GraphConstants.BOTTOM);

      boolean topOut = resistances[1] >= MAX_RESISTANCE;
      boolean bottomOut = resistances[3] >= MAX_RESISTANCE;
      boolean leftOut = resistances[2] >= MAX_RESISTANCE;
      boolean rightOut = resistances[0] >= MAX_RESISTANCE;
      outside = topOut || bottomOut || leftOut || rightOut;

      switch(getCollisionModifier()) {
      case VLabel.MOVE_RIGHT:
         // out-of-bounds condition overrides the move direction restriction
         resistances[1] = bottomOut && inPlot ? OVERLAP_RESISTANCE: MAX_RESISTANCE;
         resistances[3] = topOut && inPlot ? OVERLAP_RESISTANCE: MAX_RESISTANCE;
         resistances[2] = Math.max(resistances[2], BASE_RESISTANCE);
         break;
      case VLabel.MOVE_UP:
         // out-of-bounds condition overrides the move direction restriction
         resistances[0] = leftOut && inPlot ? OVERLAP_RESISTANCE : MAX_RESISTANCE;
         resistances[2] = rightOut && inPlot ? OVERLAP_RESISTANCE: MAX_RESISTANCE;
         resistances[3] = Math.max(resistances[3], BASE_RESISTANCE);
         break;
      default:
         // if out of bounds, moving side ways won't result in any improvement
         if((topOut || bottomOut) && !leftOut && !rightOut && inPlot) {
            resistances[0] = resistances[2] = MAX_RESISTANCE;
         }

         if((leftOut || rightOut) && !topOut && !bottomOut && inPlot) {
            resistances[1] = resistances[3] = MAX_RESISTANCE;
         }
      }
   }

   /**
    * Calculate the resistance between the two labels.
    */
   @Override
   public void calc(LayoutHelper text) {
      double resist = calcResistance(text);
      int[] dirs = getDirections(getCenter(), text.getCenter());

      if(resist == OVERLAP_RESISTANCE && getCenter().equals(text.getCenter()) &&
         text instanceof FreeHelper)
      {
         FreeHelper other = (FreeHelper) text;
         // if this is down, mark the other at same loc to the opposite direction (up)
         other.sameLocDir = getOtherSide(other.sameLocDir);
      }

      // if a text is not at multiple of 90, the resistance is marked on both
      // the closest multiple of 90, so this text doesn't move toward the
      // other text
      for(int n : dirs) {
         resistances[n] = Math.max(resistances[n], resist);
      }
   }

   /**
    * Get the directions to move the label.
    */
   private int[] getDirections(Point2D p1, Point2D p2) {
      int[] dirs = {};
      double x1 = p1.getX();
      double y1 = p1.getY();
      double x2 = p2.getX();
      double y2 = p2.getY();

      if(x2 < x1) {
         if(y2 > y1) {
            dirs = new int[] {1, 2}; // upper,left
         }
         else if(y2 == y1) {
            dirs = new int[] {2}; // left
         }
         else {
            dirs = new int[] {2, 3}; // lower left
         }
      }
      else if(x2 == x1) {
         if(y2 > y1) {
            dirs = new int[] {1}; // up
         }
         else if(y2 == y1) {
            dirs = new int[] {sameLocDir}; // right
         }
         else {
            dirs = new int[] {3}; // down
         }
      }
      else {
         if(y2 > y1) {
            dirs = new int[] {0, 1}; // upper,right
         }
         else if(y2 == y1) {
            dirs = new int[] {0}; // right
         }
         else {
            dirs = new int[] {0, 3}; // lower right
         }
      }

      return dirs;
   }

   /**
    * Calculate the resistence level between two labels.
    */
   private double calcResistance(LayoutHelper text) {
      if(overlaps(text, 0)) {
         return OVERLAP_RESISTANCE;
      }

      return getResistance(getCenter().distanceSq(text.getCenter()));
   }

   /**
    * Get the resistance level for text with the distance (square).
    */
   private double getEdgeResistance(double distSq, double x, double y, int direction) {
      // label outside of plot bounds.
      if(!bounds.contains(x, y)) {
         // force label inside bounds
         switch(direction) {
         case GraphConstants.TOP:
            if(y <= bounds.getY()) {
               return -MAX_RESISTANCE;
            }
            else {
               edgedists[3] = y - bounds.getY() - bounds.getHeight();
            }
            break;
         case GraphConstants.BOTTOM:
            if(y >= bounds.getY() + bounds.getHeight()) {
               return -MAX_RESISTANCE;
            }
            else {
               edgedists[1] = bounds.getY() - y;
            }
            break;
         case GraphConstants.RIGHT:
            if(x <= bounds.getX()) {
               return -MAX_RESISTANCE;
            }
            else {
               edgedists[2] = x - bounds.getX() - bounds.getWidth();
            }
            break;
         case GraphConstants.LEFT:
            if(x >= bounds.getX() + bounds.getWidth()) {
               return -MAX_RESISTANCE;
            }
            else {
               edgedists[0] = bounds.getX() - x;
            }
            break;
         }

         return MAX_RESISTANCE;
      }

      if(distSq < STEP * STEP) {
         return OVERLAP_RESISTANCE;
      }

      return getResistance(distSq);
   }

   /**
    * Get the resistance level for text with the distance (square).
    */
   private double getResistance(double distSq) {
      return BASE_RESISTANCE - distSq;
   }

   /**
    * Get the minimum resistance of all possible directions.
    */
   @Override
   public double getMinResistance() {
      double min = MAX_RESISTANCE;

      for(int i = 0; i < resistances.length; i++) {
         if(steps[i] < max_steps[i]) {
            min = Math.min(min, resistances[i]);
         }
      }

      return min;
   }

   /**
    * Move the label in the direction with the least resistance.
    */
   @Override
   public void move() {
      super.move();

      int n = 0;
      double min = MAX_RESISTANCE;

      for(int i = 0; i < resistances.length; i++) {
         if(resistances[i] >= MAX_RESISTANCE || steps[i] > max_steps[i]) {
            continue;
         }

         int n2 = getOtherSide(i); // opposite direction
         // if the opposite has a very high resistance, favor this direction
         // since it's most likely to resolve overlapping. Moving in other
         // directions may not result in any improvement
         double r = resistances[i] - resistances[n2] +
            // moving to the opposite direction of previous moves is a waste
            // (resulting in back and forth) and should be discouraged
            steps[n2] * BASE_RESISTANCE;

         if(r < min) {
            n = i;
            min = r;
         }
      }

      if(steps[n] > max_steps[n]) {
         return;
      }

      move(n);
      steps[n]++;

      int n1 = (n + 1) % 4; // side 1
      int n2 = getOtherSide(n); // opposite
      int n3 = (n + 3) % 4; // side 2

      // in some cases, the text is moved in two opposite directions because
      // each move makes the opposite direction more favorable. This traps
      // the text at the same spot. The following condition forces it to move
      // in one direction in this case.
      if(steps[n] > 10 && steps[n2] > 10 && steps[n1] == 0 && steps[n3] == 0) {
         Rectangle2D pbounds = vgraph.getPlotBounds();
         // @by stephenwebster, fix bug1392138574211
         // If the label is trapped at the top (it is not possible for the label
         // to fit), fix the label in place instead of moving in the opposite
         // direction.
         // This is obvious on a bar graph where we do not want to push a value
         // that is smaller (at the top of a bar) below a larger value
         if(pbounds.getMaxY() - bounds.getMaxY() < bounds.getHeight()) {
            getLabel().setCollisionModifier(VLabel.MOVE_NONE);
         }

         steps[n2] = (short) (max_steps[n2] + 1);
         steps[n] = 0;
      }
   }

   /**
    * Move the label in the specified direction.
    * @param n the direction index.
    */
   private void move(int n) {
      double angle = n * 90 * Math.PI / 180;
      double step = STEP + (inPlot ? Math.max(edgedists[n], 0) : 0);
      double x = step * Math.cos(angle);
      double y = step * Math.sin(angle);
      VLabel label = getLabel();
      Point2D pos = label.getPosition();

      label.setPosition(new Point2D.Double(pos.getX() + x, pos.getY() + y));
      super.reset();
   }

   /**
    * Call when an overlapped text is found.
    */
   @Override
   public void processOverlapped(LayoutHelper helper) {
      Point2D center1 = getCenter();
      Point2D center2 = helper.getCenter();
      VLabel label = getLabel();
      double shake = Math.random() * 0.01;

      // randomize the overlapped position so we don't move two overlapped text
      // in the same direction together. For example, if t1 and t2 both are
      // located at (5, 10), and they overlap. If they happen to have same min
      // resistance (very likely) at direction 1, they would be moved together
      // and the overlapping never resolved.

      if(center1.getX() == center2.getX()) {
         Point2D pos = label.getPosition();
         label.setPosition(new Point2D.Double(pos.getX() - shake, pos.getY()));
      }

      if(center1.getY() == center2.getY()) {
         Point2D pos = label.getPosition();
         label.setPosition(new Point2D.Double(pos.getX(), pos.getY() - shake));
      }
   }

   @Override
   public boolean isContained(Rectangle2D pbounds) {
      Rectangle2D bounds = getBounds();
      int gap = 1; // match VLabel.isContained(), allow minor overlap

      switch(getCollisionModifier()) {
      case VLabel.MOVE_RIGHT:
         // ignore top/bottom out-of-bounds since we only move to right
         return bounds.getX() + gap >= pbounds.getX() &&
            bounds.getMaxX() - gap <= pbounds.getMaxX();
      case VLabel.MOVE_UP:
         // ignore left/right out-of-bounds since we only move up
         return bounds.getY() + gap >= pbounds.getY() &&
            bounds.getMaxY() - gap <= pbounds.getMaxY();
      }

      return super.isContained(pbounds);
   }

   // for labels on point, if the label overlaps and there is space on the other side,
   // flip the label to the other side.
   public void flipIfOverlap() {
      if(getLabel() instanceof VOText && getLabel().getZIndex() >= 0) {
         VOText votext = (VOText) getLabel();

         if(votext.getGraphElement().getLabelPlacement() == GraphConstants.AUTO) {
            boolean flipDown = shouldFlip(1);

            if(votext.getPlacement() == GraphConstants.TOP && flipDown) {
               // move to below the point
               votext.getElementVO().flip(votext, 1);
            }
            // centered on nil shape (contour), should still flip.
            else if(votext.getPlacement() == GraphConstants.CENTER) {
               if(flipDown) {
                  // just create enough space for labels to be moved apart.
                  votext.getElementVO().flip(votext, 0.4);
               }
               else if(shouldFlip(3)) {
                  votext.getElementVO().flip(votext, -0.4);
               }
            }
         }
      }
   }

   // if overlaps on this side and the other side is empty.
   private boolean shouldFlip(int n) {
      int otherSide = getOtherSide(n);
      return resistances[n] >= OVERLAP_RESISTANCE && resistances[otherSide] < 0;
   }

   private static int getOtherSide(int n) {
      return (n + 2) % 4;
   }

   @Override
   public boolean needsMoveInside() {
      return inPlot && outside;
   }

   public String toString() {
      return super.toString() + "[" + getLabel().getText() + ":" + resistances[0] + "," +
         resistances[1] + "," + resistances[2] + "," + resistances[3] + "]";
   }

   private static final double STEP = 2;

   // resistance at 0, 90, 180, 270 degrees
   private double[] resistances = new double[4];
   private short[] steps = {(short) 0, (short) 0, (short) 0, (short) 0};
   private double[] edgedists = {0,  0,  0,  0};
   private int[] max_steps;
   private boolean inPlot = true;
   private boolean outside = false;
   private int sameLocDir = 3; // direction (down) to move if two labels fall on exact same point
}
