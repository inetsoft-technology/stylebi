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
package inetsoft.uql;

import java.io.Serializable;

/**
 * A Hierarchy Item defined a item contains level.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public interface HierarchyItem extends Cloneable, Serializable {
   /**
    * Get the level of the item.
    */
   int getLevel();

   /**
    * Set the level of the item.
    */
   void setLevel(int l);

   /**
    * Clone this object.
    */
   Object clone();
}
