/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.report;

import java.awt.*;

/**
 * Painter that can create shapes that have hyperlinks attached to them.
 *
 * @author InetSoft Technology
 * @since  8.5
 */
public interface LinkedShapePainter extends Painter {
   /**
    * Gets the number of datasets that this painter renders.
    *
    * @return the number of datasets bound to this painter or <code>-1</code> if
    *         this painter is not bound.
    */
   public int getDatasetCount();
   
   /**
    * Gets the number of entries in each dataset that this painter renders.
    *
    * @return the size of each dataset bound to this painter or <code>-1</code>
    *         if this painter is not bound.
    */
   public int getDatasetSize();
   
   /**
    * Gets the data object bound at the specified indexes.
    *
    * @param dataset the index of the dataset.
    * @param index the index of the data value in the dataset.
    *
    * @return the data value at the specified location.
    */
   public Object getData(int dataset, int index);
   
   /**
    * Gets the shapes that define the outline of the graphical element that
    * represents the data value at the specified indexes.
    *
    * @param dataset the index of the dataset.
    * @param index the index of the data value in the dataset.
    *
    * @return the shapes used to represent the data value, or <code>null</code>
    *         if shapes exist for the specified data point.
    */
   public Shape[] getShapes(int dataset, int index);
}
