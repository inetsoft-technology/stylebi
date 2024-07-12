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
package inetsoft.report.filter;

import inetsoft.report.TableLens;

/**
 * Binary table filter.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public interface BinaryTableFilter extends TableLens {
   /**
    * Get the left base table lens.
    * @return the left base table lens.
    */
   public TableLens getLeftTable();

   /**
    * Get the right base table lens.
    * @return the right base table lens.
    */
   public TableLens getRightTable();

   /**
    * Invalidate the table filter forcibly, causing the table filter to
    * perform filtering calculation and validate itself.
    */
   public void invalidate();

   default TableLens[] getTables() {
      return new TableLens[] { getLeftTable(), getRightTable() };
   }
}
