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
package inetsoft.report.internal.paging;

/**
 * This interface defines an API for access items in an enumeration using
 * index. This is used to allow random access to items that supports swapping
 * such as the RepletPages and PagedEnumeration for excel exporting.
 *
 * @version 6.1, 11/10/2004
 * @author InetSoft Technology Corp
 */
public interface IndexedEnumeration extends SwappedEnumeration {
   /**
    * Get the item at specified index.
    */
   public Object get(int idx);

   /**
    * Get the number of items in the enumeration.
    */
   public int size();

   /**
    * This method is called when this object is no longer needed.
    */
   @Override
   public void dispose();

   /**
    * Reset the enumeration.
    */
   public void reset();
}
