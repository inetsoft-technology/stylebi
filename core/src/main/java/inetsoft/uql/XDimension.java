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
package inetsoft.uql;

import inetsoft.util.XMLSerializable;

import java.io.Serializable;

/**
 * An XDimension represents a dimension in an OLAP cube. A dimension contains
 * an order list of levels. The level with the lowest index is that with the
 * widest scope.
 *
 * @author  InetSoft Technology
 * @since   10.1
 */
public interface XDimension extends Cloneable, Serializable, XMLSerializable {
   /**
    * Get the name of this dimension.
    *
    * @return the name of this dimension.
    */
   String getName();

   /**
    * Get the number of levels in this dimension.
    *
    * @return the number of levels in this dimension.
    */
   int getLevelCount();

   /**
    * Get the specified level.
    *
    * @param idx the index of the requested level.
    *
    * @throws ArrayIndexOutOfBoundsException if the index is outside the range
    *                                        of levels contained in this
    *                                        dimension.
    * @return specified level.
    */
   XCubeMember getLevelAt(int idx);

   /**
    * Get the scope/level number of the level
    *
    * @param levelName level name
    */
   int getScope(String levelName);

   /**
    * Get the type of this dimension.
    *
    * @return the type of this dimension.
    */
   int getType();
}
