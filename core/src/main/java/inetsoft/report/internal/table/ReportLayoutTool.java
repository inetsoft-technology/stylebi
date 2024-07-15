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
package inetsoft.report.internal.table;

import inetsoft.report.*;
import inetsoft.report.internal.*;
import inetsoft.report.internal.binding.*;
import inetsoft.report.internal.info.TableElementInfo;
import inetsoft.uql.asset.DateRangeRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.*;

/**
 * Utilities API for change table layout.
 *
 * @version 10.3
 * @author InetSoft Technology Corp
 */
public class ReportLayoutTool extends LayoutTool {

   //----------------------Logic from TableTool---------------------------------
   /**
    * Get the cell binding at the specified cell.
    */
   public static TableCellBinding getCellBinding(TableElement table, int r, int c) {
      TableLayout.RegionIndex index = table.getTableLayout().getRegionIndex(r);

      if(index == null) {
         return null;
      }

      CellBinding binding = index.getRegion().getCellBinding(index.getRow(), c);
      return binding instanceof TableCellBinding ?
         (TableCellBinding) binding : null;
   }

   static Map<Integer, String> DATEOPT = new HashMap<>();

   static {
      DATEOPT.put(DateRangeRef.YEAR_INTERVAL, "date=year");
      DATEOPT.put(DateRangeRef.QUARTER_INTERVAL, "rounddate=quarter");
      DATEOPT.put(DateRangeRef.MONTH_INTERVAL, "rounddate=month");
      DATEOPT.put(DateRangeRef.WEEK_INTERVAL, "rounddate=week");
      DATEOPT.put(DateRangeRef.DAY_INTERVAL, "rounddate=day");
      DATEOPT.put(DateRangeRef.HOUR_INTERVAL, "rounddate=hour");
      DATEOPT.put(DateRangeRef.MINUTE_INTERVAL, "rounddate=minute");
      DATEOPT.put(DateRangeRef.SECOND_INTERVAL, "rounddate=second");
      DATEOPT.put(DateRangeRef.QUARTER_OF_YEAR_PART, "date=quarter");
      DATEOPT.put(DateRangeRef.MONTH_OF_YEAR_PART, "date=month");
      DATEOPT.put(DateRangeRef.WEEK_OF_YEAR_PART, "date=week");
      DATEOPT.put(DateRangeRef.DAY_OF_MONTH_PART, "date=day");
      DATEOPT.put(DateRangeRef.DAY_OF_WEEK_PART, "date=weekday");
      DATEOPT.put(DateRangeRef.HOUR_OF_DAY_PART, "date=hour");
      DATEOPT.put(DateRangeRef.MINUTE_OF_HOUR_PART, "date=minute");
      DATEOPT.put(DateRangeRef.SECOND_OF_MINUTE_PART, "date=second");
      DATEOPT.put(DateRangeRef.NONE_INTERVAL, "rounddate=none");
   }

   private static final Logger LOG = LoggerFactory.getLogger(ReportLayoutTool.class);
}