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
package inetsoft.graph;

/**
 * Context used for configuring which components of a graph should be painted.
 */
public interface GraphPaintContext {
   /**
    * @return true if legends should be painted, false otherwise.
    */
   boolean paintLegends();

   /**
    * @return true if titles should be painted, false otherwise.
    */
   boolean paintTitles();

   /**
    * @param visual the visual
    *
    * @return true if the visual should be painted, false otherwise.
    */
   boolean paintVisual(Visualizable visual);

   /**
    * @return the default GraphPaintContext, in which all components should be painted.
    */
   static GraphPaintContext getDefault() {
      return new GraphPaintContext() {
         @Override
         public boolean paintLegends() {
            return true;
         }

         @Override
         public boolean paintTitles() {
            return true;
         }

         @Override
         public boolean paintVisual(Visualizable visual) {
            return true;
         }
      };
   }
}
