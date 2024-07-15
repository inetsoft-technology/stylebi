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
package inetsoft.graph.internal;

import java.io.Serializable;

/**
 * ILayout, the object provide common functions for layout.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public interface ILayout extends Cloneable, Serializable {
   /**
    * Get the preferred width of this visualizable.
    * @return the preferred width of this visualizable.
    */
   public double getPreferredWidth();

   /**
    * Get the preferred height of this visualizable.
    * @return the preferred height of this visualizable.
    */
   public double getPreferredHeight();

   /**
    * Get the min width of this visualizable.
    * @return the min width of this visualizable.
    */
   public double getMinWidth();

   /**
    * Get the min height of this visualizable.
    * @return the min height of this visualizable.
    */
   public double getMinHeight();
}
