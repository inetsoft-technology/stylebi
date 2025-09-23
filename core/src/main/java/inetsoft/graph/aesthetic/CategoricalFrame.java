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
package inetsoft.graph.aesthetic;

import java.util.Set;

/**
 * This defines the interface for a categorical frame bound to one dimension.
 *
 * @version 10.0
 * @author InetSoft Technology
 */
public interface CategoricalFrame {
   /**
    * Check if the value is assigned a static aesthetic value (e.g. using
    * setColor(Object, Color) in CategoricalColorFrame).
    */
   public boolean isStatic(Object val);

   /**
    * Get the values with a static color assignment.
    */
   public Set<Object> getStaticValues();

   /**
    * Clear static color assignment set by setColor().
    */
   public void clearStatic();

   /**
    * Check if the value is assigned by scripts.
    */
   public boolean isScripted(Object val);
}
