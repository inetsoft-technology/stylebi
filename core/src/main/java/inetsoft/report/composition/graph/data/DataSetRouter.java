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

      comp = data.getComparator(field);

      if(comp == null) {
         comp = getPartDateGroupComparator(data, field);
      }

      if(comp != null) {
         Collections.sort(v, comp);
      }

      values = new Object[v.size()];
      v.toArray(values);
   }

   /**
    * Fallback natural-order comparator for part-date-group dimensions (HourOfDay, DayOfWeek,
    * MonthOfYear, Quarter, etc.) when no explicit sort comparator is configured. Values for
    * these dimensions are always emitted as Integer, so numeric order is the natural calendar
    * order. Scoped to previous/next calc navigation only (this router) — does not affect
    * axis/legend ordering, which is controlled separately by CategoricalScale.
    */
   private static Comparator getPartDateGroupComparator(DataSet data, String field) {
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

      if((dim.getDateLevel() & XConstants.PART_DATE_GROUP) == 0) {
         return null;
      }

      return Comparator.nullsLast(Comparator.comparingInt(val -> ((Number) val).intValue()));
   }

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
