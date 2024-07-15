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
package inetsoft.graph.internal;

import java.awt.geom.Point2D;

public class ContourCell {
   public enum Side {
      LEFT, RIGHT, TOP, BOTTOM, NONE
   };

   public boolean isFlipped() {
      return flipped;
   }

   public void setFlipped(boolean flipped) {
      this.flipped = flipped;
   }

   public int getCaseIndex() {
      return caseIndex;
   }

   public void setCaseIndex(int caseIndex) {
      this.caseIndex = caseIndex;
   }

   public Point2D getCrossingPoint(Side cellSide) {
      switch(cellSide) {
      case BOTTOM:
         return new Point2D.Double(bottom, 0);
      case LEFT:
         return new Point2D.Double(0, left);
      case RIGHT:
         return new Point2D.Double(1, right);
      case TOP:
         return new Point2D.Double(top, 1);
      default:
         return null;
      }
   }

   public Side firstSide(Side prev) {
      switch(caseIndex) {
      case 1: case 3: case 7:
         return Side.LEFT;
      case 2: case 6: case 14:
         return Side.BOTTOM;
      case 4: case 12: case 13:
         return Side.RIGHT;
      case 8: case 9: case 11:
         return Side.TOP;
      case 5:
         switch(prev) {
         case LEFT:
            return Side.RIGHT;
         case RIGHT:
            return Side.LEFT;
         default:
            System.err.println("Invalid side: " + prev);
         }
      case 10:
         switch(prev) {
         case BOTTOM:
            return Side.TOP;
         case TOP:
            return Side.BOTTOM;
         default:
            System.err.println("Invalid side: " + prev);
         }
      default:
         System.err.println("Invalid side: " + prev);
      }

      return null;
   }

   public Side nextSide(Side prev) {
      switch(caseIndex) {
      case 8: case 12: case 14:
         return Side.LEFT;
      case 1: case 9: case 13:
         return Side.BOTTOM;
      case 2: case 3: case 11:
         return Side.RIGHT;
      case 4: case 6: case 7:
         return Side.TOP;
      case 5:
         switch(prev) {
         case LEFT:
            return flipped ? Side.BOTTOM : Side.TOP;
         case RIGHT:
            return flipped ? Side.TOP : Side.BOTTOM;
         default:
            System.err.println("Invalid side: " + prev);
         }
      case 10:
         switch(prev) {
         case BOTTOM:
            return flipped ? Side.RIGHT : Side.LEFT;
         case TOP:
            return flipped ? Side.LEFT : Side.RIGHT;
         default:
            System.err.println("Invalid side: " + prev);
         }
      default:
         System.err.println("Invalid side: " + prev);
         return Side.NONE;
      }
   }

   public void clearIndex() {
      switch(caseIndex) {
      case 0: case 5: case 10: case 15:
         break;
      default:
         caseIndex = 15;
      }
   }

   public void setLeftCrossing(double left) {
      this.left = left;
   }

   public void setRightCrossing(double right) {
      this.right = right;
   }

   public void setTopCrossing(double top) {
      this.top = top;
   }

   public void setBottomCrossing(double bottom) {
      this.bottom = bottom;
   }

   private boolean flipped;
   private int caseIndex;
   private double left, right, top, bottom;
}
