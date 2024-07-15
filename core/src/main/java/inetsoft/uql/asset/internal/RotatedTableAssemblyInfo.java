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
package inetsoft.uql.asset.internal;

/**
 * RotatedTableAssemblyInfo stores basic rotated table assembly information.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public class RotatedTableAssemblyInfo extends ComposedTableAssemblyInfo {
   /**
    * Constructor.
    */
   public RotatedTableAssemblyInfo() {
      super();
   }

   /**
    * Check if is composed.
    * @return <tt>true</tt> if composed, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isComposed() {
      return false;
   }
}
