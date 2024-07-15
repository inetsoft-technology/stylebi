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
package inetsoft.web.vswizard.recommender.chart;

import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.XDimensionRef;
import inetsoft.uql.viewsheet.graph.*;
import it.unimi.dsi.fastutil.ints.IntList;

import java.util.ArrayList;
import java.util.List;

// Facet horizontal bar
public class FacetHBarChartFilter extends FacetBarChartFilter {
   public FacetHBarChartFilter(AssetEntry[] entries, VSChartInfo temp, List<List<ChartRef>> hgroup,
                               boolean autoOrder)
   {
      super(entries, temp, hgroup, autoOrder);
   }

   // facet horizontal bar puts all dimensions on Y, and measures on X
   @Override
   public boolean isValid(ChartRefCombination comb) {
      IntList x = comb.getX();
      IntList y = comb.getY();

      if(isBarValid(comb)) {
         if(getDimensionCount(comb.getY()) > 0 || getMeasureCount(comb.getY()) == 0) {
            return false;
         }

         /* date (e.g. year) can be used as categorical
         if(dateDimCount > 0) {
            return false;
         }
         */

         return x.size() > 1 || y.size() > 1;
      }

      return false;
   }

   @Override
   protected boolean shouldRotate(ChartRefCombination comb) {
      return true;
   }

   @Override
   protected int getScore(ChartInfo chart) {
      int score = PRIMARY_SCORE;
      VSChartInfo info = (VSChartInfo) chart;

      if(!isValidFacet(info, hierarchyGroups)) {
         return -1000;
      }

      if(!autoOrder) {
         return score;
      }

      // If has hierarchy, only show hierarchy on y.
      if(isHierarchy(info)) {
         score += 1 + info.getYFieldCount();
      }
      else if(hierarchyGroups.size() > 0) {
         score -= 3;
      }

      score += getAestheticScore(info);

      return score;
   }

   protected boolean isHierarchy(ChartInfo info) {
      List<ChartRef> refs = new ArrayList<>();
      DataRef baseDate = null;

      for(int i = 0; i < info.getYFields().length; i++) {
         ChartRef ref = info.getYField(i);

         if(ref instanceof XDimensionRef && ((XDimensionRef) ref).getDataRef() != null &&
            XSchema.isDateType(((XDimensionRef) ref).getDataRef().getDataType()))
         {
            DataRef baseDate2 = ((XDimensionRef) ref).getDataRef();

            if(i == 0) {
               baseDate = baseDate2;
            }
            else if(baseDate == null || !baseDate.equals(baseDate2)) {
               return false;
            }

            continue;
         }

         refs.add(ref);
      }

      return baseDate != null || hierarchyGroups.size() > 0 &&
         hierarchyGroups.stream().allMatch(hier -> refs.containsAll(hier));
   }
}
