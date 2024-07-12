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
package inetsoft.report.internal.table;

/**
 * This interface really should be part of TableFilter. But it's only used
 * in limited situations, so it's kept separate to not require all filters
 * to implementation the methods.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public interface MappedTableLens {
   /**
    * Get the previous logical row index. The definition of logical row 
    * depends on table filter. For example, the freehand logical row is a
    * region. The CalcRuntimeTableLens's logical row is the row that maps
    * to the same original row before the expansion.
    */
   public int getLastLogicalRow(int row);
}
