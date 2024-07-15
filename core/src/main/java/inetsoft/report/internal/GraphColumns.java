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
package inetsoft.report.internal;

import inetsoft.graph.visual.ElementVO;
import inetsoft.report.composition.graph.IntervalDataSet;
import inetsoft.uql.XCondition;
import inetsoft.uql.XConstants;
import inetsoft.uql.viewsheet.XDimensionRef;
import inetsoft.uql.viewsheet.graph.ChartAggregateRef;
import inetsoft.uql.viewsheet.graph.ChartDimensionRef;
import inetsoft.util.Tool;

import java.util.*;

/**
 * This class stores the columns required in a dataset.
 *
 * @version 11.4, 7/24/2012
 * @author InetSoft Technology Corp
 */
public class GraphColumns extends HashSet<String> {
   public GraphColumns() {
   }

   @Override
   public boolean add(String name) {
      // ignore discrete prefix.
      name = ChartAggregateRef.getBaseName(name);

      if(name.startsWith(IntervalDataSet.TOP_PREFIX)) {
         name = name.substring(IntervalDataSet.TOP_PREFIX.length());
      }

      return super.add(name);
   }

   /**
    * Set the dimension info.
    */
   public void setDimensionInfo(ChartDimensionRef dim) {
      // we should keep track of all dims (possibly with same column but different sorting
      // or ranking), so the matchSortRanking() would find the dim. (59272)
      dimsInBinding.computeIfAbsent(dim.getFullName(), k -> new ArrayList<>()).add(dim);
   }

   /**
    * Check if the sorting/ranking defined in the dimension matches the
    * sorting/ranking required for the column.
    */
   public boolean matchSortRanking(XDimensionRef dim) {
      return dimsInBinding.containsKey(dim.getFullName()) &&
         dimsInBinding.get(dim.getFullName()).stream().anyMatch(dim2 ->
            matchSortRanking(dim, dim2.getRankingOption(), dim2.getRankingN(),
                             dim2.getRankingCol(), dim2.getOrder(), dim2.getSortByCol()));
   }

   private boolean matchSortRanking(XDimensionRef dim, int op2, int n2, String col2,
                                   int order2, String sortBy2)
   {
      int op = dim.getRankingOption();
      int n = dim.getRankingN();
      String col = dim.getRankingCol();

      if(op != op2) {
         return false;
      }

      if(op == XCondition.TOP_N || op == XCondition.BOTTOM_N) {
         if(n != n2 || !Tool.equals(col, col2)) {
            return false;
         }
      }

      int order = dim.getOrder();

      if(order != order2) {
         return false;
      }

      String sortBy = dim.getSortByCol();

      if(order == XConstants.SORT_VALUE_ASC || order == XConstants.SORT_VALUE_DESC) {
         return Tool.equals(sortBy, sortBy2);
      }

      return true;
   }

   /**
    * Get the columns without ALL prefix.
    */
   public GraphColumns getBaseColumns() {
      GraphColumns cols = new GraphColumns();

      for(String col : this) {
         cols.add(ElementVO.getBaseName(col));
      }

      for(String col : dimsInBinding.keySet()) {
         cols.dimsInBinding.put(ElementVO.getBaseName(col), dimsInBinding.get(col));
      }

      return cols;
   }

   public Map<String, List<ChartDimensionRef>> dimsInBinding = new HashMap<>();
}
