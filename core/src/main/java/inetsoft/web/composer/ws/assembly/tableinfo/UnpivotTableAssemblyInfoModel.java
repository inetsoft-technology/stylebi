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
package inetsoft.web.composer.ws.assembly.tableinfo;

import inetsoft.uql.asset.internal.UnpivotTableAssemblyInfo;

public class UnpivotTableAssemblyInfoModel extends TableAssemblyInfoModel {
   public UnpivotTableAssemblyInfoModel(UnpivotTableAssemblyInfo info) {
      super(info);

      this.headerColumns = info.getHeaderColumns();
   }

   public int getHeaderColumns() {
      return headerColumns;
   }

   public void setHeaderColumns(int headerColumns) {
      this.headerColumns = headerColumns;
   }

   private int headerColumns;
}
