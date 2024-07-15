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
package inetsoft.graph.treemap;

/**
 * Calculations on treemap layouts. Currently
 * holds routines for readability and aspect ratios. This is a good
 * place to add future metrics.
 */
public class LayoutCalculations {

   public static double averageAspectRatio(MapModel model) {
      return averageAspectRatio(model.getItems());
   }

   public static double averageAspectRatio(Mappable[] m) {
      double s = 0;
      int n = m.length;
      if(m == null || n == 0) {
         System.out.println("Can't measure aspect ratio.");
         return 0;
      }
      for(int i = 0; i < n; i++) {
         s += m[i].getBounds().aspectRatio();
      }
      return s / n;
   }

   public static double getReadability(TreeModel tree) {
      return getReadability(tree.getLeafModels());
   }

   public static double getReadability(MapModel[] model) {
      int weight = 0;
      double r = 0;
      for(int i = 0; i < model.length; i++) {
         int n = model[i].getItems().length;
         weight += n;
         r += n * getReadability(model[i]);
      }
      return weight == 0 ? 1 : r / weight;
   }

   /**
    * Compute the readability of the model.
    * Readability is defined by the number of changes in directions rects are layed out in.
    */
   public static double getReadability(MapModel model) {
      int numTurns = 0;
      double prevAngle = 0;
      double angle = 0;
      double angleChange = 0;
      Mappable[] items = model.getItems();
      Rect b1, b2;
      double dx, dy;
      double readability;

      for(int i = 1; i < items.length; i++) {
         b1 = items[getItemIndex(i - 1, model)].getBounds();
         b2 = items[getItemIndex(i, model)].getBounds();
         dx = (b2.x + 0.5 * b2.w) - (b1.x + 0.5 * b1.w);
         dy = (b2.y + 0.5 * b2.h) - (b1.y + 0.5 * b1.h);
         angle = Math.atan2(dy, dx);
         if(i >= 2) {
            angleChange = Math.abs(angle - prevAngle);
            if(angleChange > Math.PI) {
               angleChange = Math.abs(angleChange - (2 * Math.PI));
            }
            if(angleChange > 0.1) { // allow for rounding
               numTurns++;
            }
         }
         prevAngle = angle;
      }

      readability = 1.0 - ((double) numTurns / items.length);

      return readability;
   }

   public static int getItemIndex(int order, MapModel map) {
      int i;
      Mappable[] items = map.getItems();

      for(i = 0; i < items.length; i++) {
         if(items[i].getOrder() == order) {
            break;
         }
      }

      return i;
   }
}
