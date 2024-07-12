/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.graph.treemap;

/**
 * The interface for all treemap layout algorithms.
 * If you write your own algorith, it should conform
 * to this interface.
 * <p>
 * IMPORTANT: if you want to be able to automatically plug
 * your algorithm into the various demos and test harnesses
 * included in the treemap package, it should have
 * an empty constructor.
 */
public interface MapLayout {
   /**
    * Arrange the items in the given MapModel to fill the given rectangle.
    *
    * @param model  The MapModel.
    * @param bounds The boundsing rectangle for the layout.
    */
   public void layout(MapModel model, Rect bounds);

   /**
    * Return a human-readable name for this layout;
    * used to label figures, tables, etc.
    *
    * @return String naming this layout.
    */
   public String getName();

   /**
    * Return a longer description of this layout;
    * Helpful in creating online-help,
    * interactive catalogs or indices to lists of algorithms.
    *
    * @return String describing this layout.
    */
   public String getDescription();
}
