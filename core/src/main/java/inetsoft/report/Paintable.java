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
package inetsoft.report;

import java.awt.*;

/**
 * The Paintable interface encapsulates the printing of an element in
 * a StylePage. A StylePage is consisted of a collection of Paintable
 * objects. The Paintable objects are added to a StylePage during
 * the printNext() call.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public interface Paintable extends java.io.Serializable {
   /**
    * Paint the element on to a page.
    */
   public void paint(Graphics g);

   /**
    * Return the bounds of this paintable area.
    * @return area bounds or null if element does not occupy an area.
    */
   public Rectangle getBounds();

   /**
    * Set the location of this paintable area. This is used internally
    * for small adjustments during printing.
    * @param loc new location for this paintable.
    */
   public void setLocation(Point loc);

   /**
    * Get the location of this paintable.
    */
   public Point getLocation();

   /**
    * Get the report element that this paintable area corresponds to.
    * @return report element.
    */
   public ReportElement getElement();
}
