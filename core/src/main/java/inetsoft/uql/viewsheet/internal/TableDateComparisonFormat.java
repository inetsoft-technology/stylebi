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
package inetsoft.uql.viewsheet.internal;

import inetsoft.report.filter.DCMergeDatePartFilter.MergePartCell;
import inetsoft.uql.asset.DateRangeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XUtil;
import inetsoft.uql.viewsheet.CalculateRef;
import inetsoft.uql.viewsheet.XDimensionRef;

import java.text.*;
import java.util.Date;

public class TableDateComparisonFormat extends Format {
   public TableDateComparisonFormat(Format fmt) {
      super();
      this.format = fmt;
   }

   @Override
   public StringBuffer format(Object obj, StringBuffer toAppendTo, FieldPosition pos) {
      Format dateFmt = XUtil.getDefaultDateFormat(DateRangeRef.DAY_INTERVAL);

      if(obj == null) {
         // ignore
      }
      else if(obj instanceof Date) {
         toAppendTo.append(dateFmt.format(obj));
      }
      else if(obj instanceof MergePartCell &&
         ((MergePartCell) obj).getDateGroupValue() instanceof Date)
      {
         XDimensionRef partRef = ((MergePartCell) obj).getPartRef();

         if(format == null && partRef != null) {
            DataRef dataRef = partRef.getDataRef();

            if(dataRef instanceof CalculateRef && ((CalculateRef) dataRef).isDcRuntime() &&
               dataRef.getName() != null && dataRef.getName().startsWith("MonthOfQuarter("))
            {
               format = XUtil.getDefaultDateFormat(DateRangeRef.MONTH_OF_YEAR_PART, XSchema.DATE);
            }
         }

         Object data = ((MergePartCell) obj).getOriginalRawDate();
         boolean rawDate = data instanceof Date;
         Date date = rawDate ? (Date) data : (Date) ((MergePartCell) obj).getDateGroupValue();
         toAppendTo.append(DateComparisonUtil.formatPartMergeCell((MergePartCell) obj, date, rawDate, format));
      }
      else {
         toAppendTo.append(obj);
      }

      return toAppendTo;
   }

   @Override
   public Object parseObject(String source, ParsePosition pos) {
      return null;
   }

   @Override
   public TableDateComparisonFormat clone() {
      return (TableDateComparisonFormat) super.clone();
   }

   private Format format;
}
