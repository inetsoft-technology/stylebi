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
package inetsoft.report.internal;

import inetsoft.report.composition.graph.GraphUtil;
import inetsoft.uql.XCondition;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.NamedRangeRef;
import inetsoft.uql.viewsheet.XAggregateRef;
import inetsoft.uql.viewsheet.XDimensionRef;
import inetsoft.uql.viewsheet.graph.ChartAggregateRef;
import inetsoft.util.Tool;

import java.util.*;

/**
 * This class records the columns in a query for chart.
 *
 * @version 11.4, 7/24/2012
 * @author InetSoft Technology Corp
 */
public class SubColumns {
   public SubColumns(Set<XDimensionRef> groups, Set<XAggregateRef> aggrs) {
      for(XDimensionRef dim : groups) {
         this.groups.add(dim.getFullName());
         dims.add(dim);
      }

      for(XAggregateRef ref : aggrs) {
         this.aggrs.add(ref.getFullName(false));

         if(ref instanceof ChartAggregateRef) {
            GraphUtil.addSubCol((ChartAggregateRef) ref, this.aggrs);
         }
      }

      this.groupRefs = groups;
      this.aggrRefs = aggrs;
   }

   /**
    * Get group refs.
    */
   public Set<XDimensionRef> getGroupRefs() {
      return groupRefs;
   }

   /**
    * Get aggregate refs.
    */
   public Set<XAggregateRef> getAggRefs() {
      return aggrRefs;
   }

   /**
    * Add aggregate column.
    */
   public void addAggregate(String aggr) {
      aggrs.add(aggr);
   }

   /**
    * Get the grouping dimensions.
    */
   public List<XDimensionRef> getDimensions() {
      return dims;
   }

   /**
    * Check if the columns matches this query.
    */
   public boolean match(GraphColumns columns) {
      Set all = new HashSet(groups);
      Set groups = new HashSet(this.groups);

      all.addAll(aggrs);
      groups.removeAll(columns);

      // all columns are in this query and there is no extra grouping in this
      // query that is not in the columns
      if(all.containsAll(columns) && groups.size() == 0) {
         for(XDimensionRef dim : dims) {
            if(!columns.matchSortRanking(dim)) {
               return false;
            }
         }

         return true;
      }

      return false;
   }

   /**
    * Check if this column is part of this query.
    */
   public boolean contains(String col) {
      return groups.contains(col) || aggrs.contains(col);
   }

   /**
    * Check column exist.
    */
   public boolean lazyContains(String col) {
      if(contains(col)) {
         return true;
      }

      col = getOriginalName(col);

      for(String grp : groups) {
         grp = getOriginalName(grp);

         if(col.equals(grp)) {
            return true;
         }
      }

      for(String aggr : aggrs) {
         aggr = getOriginalName(aggr);

         if(col.equals(aggr)) {
            return true;
         }
      }

      return false;
   }

   private String getOriginalName(String name) {
      // treat DataGroup(...) same as ColorGroup(...). (60850)
      name = NamedRangeRef.getBaseName(name);
      // remove xxx.1
      name = GraphUtil.getFullNameNoCalc(name);
      // remove entity.attribute
      int dot = name.lastIndexOf(".");
      return dot > 0 ? name.substring(dot + 1) : name;
   }

   public String toString() {
      return "group" + groups + ", agg" + aggrs;
   }

   public int hashCode() {
      return groups.hashCode() + 7 * aggrs.hashCode();
   }

   public boolean equals(Object obj) {
      if(!(obj instanceof SubColumns)) {
         return false;
      }

      SubColumns sub = (SubColumns) obj;

      if(!groups.equals(sub.groups) || !aggrs.equals(sub.aggrs)) {
         return false;
      }

      for(int i = 0; i < dims.size(); i++) {
         if(!equalsRef(dims.get(i), sub.dims.get(i))) {
            return false;
         }
      }

      return true;
   }

   /**
    * Check if two dimensions are for same grouping.
    */
   public static boolean equalsRef(Object ref1, Object ref2) {
      if(!ref1.equals(ref2)) {
         return false;
      }

      if(ref1 instanceof XDimensionRef && ref2 instanceof XDimensionRef) {
         XDimensionRef dim1 = (XDimensionRef) ref1;
         XDimensionRef dim2 = (XDimensionRef) ref2;

         if(dim1.isDateTime() && dim2.isDateTime()) {
            if(dim1.getDateLevel() != dim2.getDateLevel()) {
               return false;
            }
         }

         if(dim1.getRankingOption() != dim2.getRankingOption()) {
            return false;
         }

         if(dim1.getRankingOption() == XCondition.TOP_N ||
            dim1.getRankingOption() == XCondition.BOTTOM_N)
         {
            if(dim1.getRankingN() != dim2.getRankingN() ||
               !Tool.equals(dim1.getRankingCol(), dim2.getRankingCol()))
            {
               return false;
            }
         }

         if(dim1.getOrder() != dim2.getOrder()) {
            return false;
         }

         if(dim1.getOrder() == XConstants.SORT_VALUE_ASC ||
            dim1.getOrder() == XConstants.SORT_VALUE_DESC)
         {
            if(!Tool.equals(dim1.getSortByCol(), dim2.getSortByCol())) {
               return false;
            }
         }
      }

      if(ref1 instanceof XAggregateRef && ref2 instanceof XAggregateRef) {
         return ((XAggregateRef) ref1).getFullName().equals(
            ((XAggregateRef) ref2).getFullName());
      }

      return true;
   }

   private Set<String> groups = new HashSet<>();
   private Set<String> aggrs = new HashSet<>();
   private Set<XDimensionRef> groupRefs;
   private Set<XAggregateRef> aggrRefs;
   private List<XDimensionRef> dims = new ArrayList<>();
}
