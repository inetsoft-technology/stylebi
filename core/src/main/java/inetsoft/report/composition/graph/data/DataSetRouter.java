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
package inetsoft.report.composition.graph.data;

import inetsoft.graph.data.DataSet;
import inetsoft.graph.data.DataSetFilter;
import inetsoft.report.composition.graph.VSDataSet;
import inetsoft.uql.XConstants;
import inetsoft.uql.viewsheet.VSDataRef;
import inetsoft.uql.viewsheet.XDimensionRef;
import inetsoft.util.Tool;

import java.util.*;

/**
 * A map for data compare, it is used for data calculation,
 * like Change, RunningTotal or Moving.
 *
 * @version 10.3
 * @author InetSoft Technology Corp
 */
public class DataSetRouter extends AbstractRouter {
   /**
    * Default constructor.
    */
   public DataSetRouter() {
      super();
   }

   /**
    * Constructor.
    */
   public DataSetRouter(DataSet data, String field) {
      super();
      keyhash = data.hashCode();
      List v = new ArrayList<>();
      Object val = null;

      for(int i = 0; i < data.getRowCount(); i++) {
         val = data.getData(field, i);

         if(!v.contains(val)) {
            v.add(val);
         }
      }

      // Any display sort configured on the field — ascending, descending, specific order, or
      // value-based (a Top-N "Sort By Value" ranking) — defines the order that previous/next
      // navigation follows, so that the calc stays aligned with the order the values are
      // actually plotted in. Part-date-group fields (HourOfDay, DayOfWeek, MonthOfYear,
      // Quarter) fall back to natural calendar order only when no sort is configured, since
      // raw row-appearance order is arbitrary there. (76039)
      comp = data.getComparator(field);

      if(comp == null && getPartDateDimension(data, field) != null) {
         comp = PART_DATE_ORDER;
      }

      if(comp != null) {
         Collections.sort(v, comp);
      }

      values = new Object[v.size()];
      v.toArray(values);
   }

   /**
    * Get the dimension backing the field if it is a part-date-group dimension (HourOfDay,
    * DayOfWeek, MonthOfYear, Quarter, etc.), or null if the field is not one.
    */
   private static XDimensionRef getPartDateDimension(DataSet data, String field) {
      DataSet root = data instanceof DataSetFilter
         ? ((DataSetFilter) data).getRootDataSet() : data;

      if(!(root instanceof VSDataSet)) {
         return null;
      }

      VSDataRef ref = ((VSDataSet) root).getDataRef(field);

      if(!(ref instanceof XDimensionRef)) {
         return null;
      }

      XDimensionRef dim = (XDimensionRef) ref;
      return (dim.getDateLevel() & XConstants.PART_DATE_GROUP) != 0 ? dim : null;
   }

   /**
    * Natural calendar order for part-date-group dimension values. Values are normally
    * emitted as Integer, so numeric order is calendar order. Nulls sort first to match the
    * position the null group occupies on an ascending axis, and any non-numeric label (e.g.
    * the "Others" group produced by a Top-N ranking) sorts after all numeric values rather
    * than failing the comparison.
    */
   private static final Comparator PART_DATE_ORDER = (a, b) -> {
      if(a == null || b == null) {
         return a == b ? 0 : (a == null ? -1 : 1);
      }

      boolean anum = a instanceof Number;
      boolean bnum = b instanceof Number;

      if(anum && bnum) {
         return Double.compare(((Number) a).doubleValue(), ((Number) b).doubleValue());
      }

      if(anum != bnum) {
         return anum ? -1 : 1;
      }

      return Tool.compare(a, b);
   };

   @Override
   public Object[] getValues() {
      return values;
   }

   @Override
   public boolean isValidFor(DataSet dataSet) {
      return keyhash == dataSet.hashCode();
   }

   private Object[] values;
   private int keyhash;
}
