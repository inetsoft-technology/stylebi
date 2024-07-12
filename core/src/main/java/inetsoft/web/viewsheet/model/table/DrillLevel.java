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
package inetsoft.web.viewsheet.model.table;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * Identify the drill level for a selectable area
 */
@JsonFormat(shape = JsonFormat.Shape.NUMBER_INT)
public enum DrillLevel {
   /**
    * Can't drill
    */
   None,

   /**
    * No hierarchy relationship(drill is enabled, that can filter)
    */
   Normal,

   /**
    * At the top of the Hierarchy relationship(only have child)
    */
   Root,

   /**
    * At the middle of the Hierarchy relationship(have child and parent)
    */
   Middle,

   /**
    * Leaf node(only have parent)
    */
   Leaf
}
