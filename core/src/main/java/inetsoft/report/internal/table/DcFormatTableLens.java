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
package inetsoft.report.internal.table;

import inetsoft.report.TableLens;
import inetsoft.report.filter.DCMergeDatePartFilter;
import inetsoft.report.internal.Util;
import inetsoft.uql.viewsheet.internal.DateComparisonFormat;
import inetsoft.uql.viewsheet.internal.DateComparisonUtil;
import inetsoft.util.Tool;

import java.text.Format;
import java.util.Date;

public class DcFormatTableLens extends FormatTableLens2 {
   public DcFormatTableLens(TableLens base, DateComparisonFormat dcFormat) {
      super(base);
      this.dcFormat = dcFormat;

      if(dcFormat != null) {
         partCol = Util.findColumn(base, Tool.isEmptyString(dcFormat.getDatePartColAlias()) ?
            dcFormat.getDatePartCol() : dcFormat.getDatePartColAlias());
         dateCol = Util.findColumn(base, Tool.isEmptyString(dcFormat.getDateColAlias()) ? dcFormat.getDateCol() :
            dcFormat.getDateColAlias());
      }
   }

   @Override
   protected Object format(int r, int c, Object obj) {
      if(r < getHeaderRowCount()) {
         return obj;
      }

      if(partCol == c && dateCol >= 0 && r > 0) {
         Object date = getTable().getObject(r, dateCol);
         Format fmt = getUserCellFormat(r, c);

         if(obj instanceof Integer) {
            return dcFormat.format((Integer) obj, (Date) date, fmt);
         }
         else if(obj instanceof DCMergeDatePartFilter.MergePartCell) {
            DCMergeDatePartFilter.MergePartCell cell = (DCMergeDatePartFilter.MergePartCell) obj;
            return fmt == null ? dcFormat.format(cell, (Date) date) :
               DateComparisonUtil.formatPartMergeCell(cell, (Date) date, fmt);
         }
      }

      return super.format(r, c, obj);
   }

   public Format getUserCellFormat(int r, int c) {
      TableLens lens = Util.getNestedTable(this, FormatTableLens.class);

      if(lens == null) {
         return null;
      }

      TableFormat rowf = getRowTableFormat(r);
      TableFormat colf = getColTableFormat(c);
      TableFormat cellf = getCellTableFormat(r, c);

      if(rowf == null && colf == null && cellf == null) {
         return null;
      }

      return getCellFormat(r, c);
   }

   private final DateComparisonFormat dcFormat;
   private int partCol = -1, dateCol = -1;
}
