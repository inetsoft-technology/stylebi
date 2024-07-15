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
package inetsoft.web.vswizard.recommender.chart;

import inetsoft.report.composition.graph.GraphUtil;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.viewsheet.graph.*;

import java.util.List;

public class CirclePackingChartFilter extends TreemapChartFilter {
   public CirclePackingChartFilter(AssetEntry[] entries, VSChartInfo temp,
                                   List<List<ChartRef>> hgroup, boolean autoOrder)
   {
      super(entries, temp, hgroup, autoOrder);
   }

   @Override
   protected int getChartType() {
      return GraphTypes.CHART_CIRCLE_PACKING;
   }

   @Override
   public boolean isValid(ChartRefCombination comb) {
      return isPackedBubble(comb) || super.isValid(comb);
   }

   // packaged bubble is a single tree level chart where bubbles are packed into a circle,
   // and other dimensions are placed on the color, and measure on size
   private boolean isPackedBubble(ChartRefCombination comb) {
      if(dateDimCount == getDimCount()) {
         return false;
      }

      return comb.getXCount() == 0 && comb.getYCount() == 1 && hasYDimension(comb) &&
         (comb.getInsideCount() == 1 && hasInsideMeasure(comb) ||
            comb.getInsideCount() == 2 && hasInsideMeasure(comb) &&
               hasInsideDimension(comb));
   }

   @Override
   public VSChartInfo createChartInfo(ChartRefCombination comb) {
      if(isPackedBubble(comb)) {
         List<ChartRef> refs = getAllRefs(true);
         VSChartInfo info = new VSChartInfo();
         info.setChartType(getChartType());
         addTField(info, comb, refs);
         addInsidePackedBubble(info, comb, refs);

         if(info.getColorField() == null) {
            addColor(info, comb);
            GraphUtil.fixVisualFrames(info);
         }

         return getClassyInfo(info);
      }

      return super.createChartInfo(comb);
   }

   private void addInsidePackedBubble(VSChartInfo info, ChartRefCombination comb,
                                      List<ChartRef> refs)
   {
      List<ChartRef> insideRefs = getRefs(comb.getInside(), refs);

      for(ChartRef ref : insideRefs) {
         VSAestheticRef aes = createAestheticRef(ref);

         if(ref instanceof ChartDimensionRef) {
            info.setColorField(aes);
         }
         else {
            info.setSizeField(aes);
         }
      }

      GraphUtil.fixVisualFrames(info);
   }

   @Override
   protected int getScore(ChartInfo chart) {
      VSChartInfo info = (VSChartInfo) chart;

      if(dateDimCount > 1 && getDimCount() == dateDimCount) {
         return SPECIAL_PURPOSE_SCORE;
      }

      if(hierarchyGroups.size() == 0) {
         if(getDimCount() > 2) {
            return -1000;
         }
      }
      //If only one hierarchy, put hiearchy on T and first t field to color.
      else if(hierarchyGroups.size() == 1) {
         if(!isMatchHierarchy(info.getGroupFields(), hierarchyGroups.get(0)) ||
            info.getGroupFields().length != hierarchyGroups.get(0).size())
         {
            return -1000;
         }
      }
      else if(hierarchyGroups.size() > 1) {
         return -1000;
      }

      return super.getScore(chart);
   }
}
