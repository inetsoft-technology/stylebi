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
package inetsoft.report.internal;

import inetsoft.uql.viewsheet.BorderColors;

import java.awt.*;

/**
 * The Region BorderedElement provides operations for objects that get and set 
 * the Border attributes.
 *
 * @version 8.0, 9/22/2005
 * @author InetSoft Technology Corp
 */
public interface BorderedElement
{
   /**
    * Set the border colors.
    */
   public void setBorderColors(BorderColors bcolors);

   /**
    * Get the border colors.
    */
   public BorderColors getBorderColors();

   /**
    * Set the individual border line styles.
    * @param border line styles.
    */
   public void setBorders(Insets borders);

   /**
    * Get the individual border line styles.
    * @return border line style..
    */
   public Insets getBorders();

}
