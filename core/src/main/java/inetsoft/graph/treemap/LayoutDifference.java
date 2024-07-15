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
 * Object that can measure to the amount of
 * structural change between two layouts.
 * The layouts must have identical tree structures.
 * <p>
 * To measure a difference, first do a "recordLayout" on a MapModel,
 * then call "averageDistance" on the changed model.
 */
public class LayoutDifference {
   private Rect[] old;

   public void recordLayout(MapModel model) {
      recordLayout(model.getItems());
   }

   public void recordLayout(Mappable[] m) {
      old = null;
      if(m == null) {
         return;
      }
      old = new Rect[m.length];
      for(int i = 0; i < m.length; i++) {
         old[i] = m[i].getBounds().copy();
      }
   }

   public double averageDistance(MapModel model) {
      return averageDistance(model.getItems());
   }

   public double averageDistance(Mappable[] m) {
      double d = 0;
      int n = m.length;
      if(m == null || old == null || n != old.length) {
         System.out.println("Can't compare models.");
         return 0;
      }
      for(int i = 0; i < n; i++) {
         d += old[i].distance(m[i].getBounds());
      }

      return d / n;
   }
}
