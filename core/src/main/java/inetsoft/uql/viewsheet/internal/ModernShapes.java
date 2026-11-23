/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
package inetsoft.uql.viewsheet.internal;

import inetsoft.graph.aesthetic.CategoricalShapeFrame;
import inetsoft.graph.aesthetic.GShape;
import inetsoft.report.StyleConstants;
import inetsoft.uql.viewsheet.graph.aesthetic.ShapeFrameWrapper;
import inetsoft.util.Tool;

/**
 * The categorical shape lists a chart takes by mark. A frame whose list equals one of the
 * classic defaults is the default's, not the author's, and swaps with the mark.
 */
public final class ModernShapes {
   private ModernShapes() {
   }

   /**
    * The list a new categorical shape frame starts with.
    */
   public static GShape[] classicDefault() {
      CategoricalShapeFrame frame = new CategoricalShapeFrame();
      GShape[] list = new GShape[frame.getShapeCount()];

      for(int i = 0; i < list.length; i++) {
         list[i] = frame.getShape(i);
      }

      return list;
   }

   /**
    * The classic default with the filled markers first.
    */
   public static GShape[] modernDefault() {
      return repeat(new GShape[] {
         GShape.FILLED_CIRCLE, GShape.FILLED_TRIANGLE, GShape.FILLED_SQUARE,
         GShape.FILLED_DIAMOND, GShape.CIRCLE, GShape.TRIANGLE, GShape.SQUARE, GShape.CROSS,
         GShape.STAR, GShape.DIAMOND, GShape.XSHAPE
      });
   }

   /**
    * The list the Shape pane's Reset leaves on the frame: the pane's shape ids in order, cut to
    * the frame's size.
    */
   public static GShape[] classicReset() {
      String[] ids = {
         StyleConstants.CIRCLE + "", StyleConstants.TRIANGLE + "", StyleConstants.SQUARE + "",
         StyleConstants.CROSS + "", StyleConstants.STAR + "", StyleConstants.DIAMOND + "",
         StyleConstants.X + "", StyleConstants.FILLED_CIRCLE + "",
         StyleConstants.FILLED_TRIANGLE + "", StyleConstants.FILLED_SQUARE + "",
         StyleConstants.FILLED_DIAMOND + "", StyleConstants.V_ANGLE + "",
         StyleConstants.RIGHT_ANGLE + "", StyleConstants.LT_ANGLE + "",
         StyleConstants.V_LINE + "", StyleConstants.H_LINE + "",
         "100ArrowDown.svg", "101ArrowUp.svg", "102Check.svg", "103Cancel.svg",
         "104Exclamation.svg", "105Flag.svg", "106Light.svg", "107Star.svg", "108No.svg",
         "109Man.svg", "110Woman.svg", "111FaceHappy.svg", "112FaceSad.svg", "113Face.svg",
         "114ArrowUperRight.svg", "115ArrowLowerRight.svg"
      };
      GShape[] list = new GShape[SIZE];

      for(int i = 0; i < SIZE; i++) {
         list[i] = ShapeFrameWrapper.getGShape(ids[i]);
      }

      return list;
   }

   /**
    * The Reset list with its first eleven entries reordered as the filled list.
    */
   public static GShape[] modernReset() {
      GShape[] list = classicReset();
      System.arraycopy(modernDefault(), 0, list, 0, 11);
      return list;
   }

   /**
    * Moves a frame between the classic and the marked defaults. A list that is neither is the
    * author's and stays. The frame's per-value shapes are untouched.
    *
    * @return true if the list changed.
    */
   public static boolean swap(CategoricalShapeFrame frame, boolean modern) {
      if(frame == null) {
         return false;
      }

      // every default of a side starts with the same marker, so most frames end here
      GShape first = modern ? GShape.CIRCLE : GShape.FILLED_CIRCLE;

      if(frame.getShapeCount() == 0 || !Tool.equals(frame.getShape(0), first)) {
         return false;
      }

      GShape[] from = modern ? classicDefault() : modernDefault();
      GShape[] to = modern ? modernDefault() : classicDefault();

      if(!replace(frame, from, to)) {
         from = modern ? classicReset() : modernReset();
         to = modern ? modernReset() : classicReset();
         return replace(frame, from, to);
      }

      return true;
   }

   /**
    * A frame with the mark's default list.
    */
   public static CategoricalShapeFrame newFrame(VizContext ctx) {
      CategoricalShapeFrame frame = new CategoricalShapeFrame();

      if(ctx != null && ctx.modern) {
         setList(frame, modernDefault());
      }

      return frame;
   }

   private static boolean replace(CategoricalShapeFrame frame, GShape[] from, GShape[] to) {
      if(frame.getShapeCount() != from.length) {
         return false;
      }

      for(int i = 0; i < from.length; i++) {
         if(!Tool.equals(frame.getShape(i), from[i])) {
            return false;
         }
      }

      setList(frame, to);
      return true;
   }

   private static void setList(CategoricalShapeFrame frame, GShape[] list) {
      for(int i = 0; i < list.length; i++) {
         frame.setShape(i, list[i]);
      }
   }

   private static GShape[] repeat(GShape[] cycle) {
      GShape[] list = new GShape[SIZE];

      for(int i = 0; i < SIZE; i++) {
         list[i] = cycle[i % cycle.length];
      }

      return list;
   }

   private static final int SIZE = 30;
}
