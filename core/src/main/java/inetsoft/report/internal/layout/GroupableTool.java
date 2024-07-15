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
package inetsoft.report.internal.layout;

import inetsoft.report.*;
import inetsoft.report.internal.*;
import inetsoft.report.lens.DefaultSectionLens;

import java.util.List;

/**
 * GroupableTool, to support groupable element common action.
 *
 * @version 10.3
 * @author InetSoft Technology Corp
 */
public class GroupableTool {
   /**
    * Get cell binding infos.
    */
   public static List<CellBindingInfo> getCellInfos(BindableElement e, boolean all) {
      if(e instanceof TableElement) {
         TableLayout layout = ((TableElement) e).getTableLayout();
         return layout == null ? null : layout.getCellInfos(all);
      }
      else if(e instanceof SectionElement) {
         return ((DefaultSectionLens)
            ((SectionElement) e).getSection()).getCellInfos(all);
      }

      return null;
   }

   /**
    * Format detail symbol.
    */
   public static void formatDetailSymbol(GroupableCellBinding cell, int expand) {
      cell.setBType(TableCellBinding.DETAIL);
      cell.setExpansion(expand);
   }

   /**
    * Format summary symbol.
    */
   public static void formatSummarySymbol(GroupableCellBinding cell) {
      cell.setBType(TableCellBinding.SUMMARY);
      cell.setExpansion(TableCellBinding.EXPAND_NONE);
   }

   /**
    * Format group symbol.
    */
   public static void formatGroupSymbol(GroupableCellBinding cell, int expand) {
      cell.setBType(TableCellBinding.GROUP);
      cell.setExpansion(expand);
   }
}
