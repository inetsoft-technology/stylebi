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

import inetsoft.graph.aesthetic.GShape;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.graph.aesthetic.ShapeFrameWrapper;
import inetsoft.uql.viewsheet.graph.aesthetic.StaticShapeFrameWrapper;
import inetsoft.web.vswizard.recommender.ChartRecommenderUtil;
import it.unimi.dsi.fastutil.ints.IntList;

import java.util.Arrays;
import java.util.List;

public class HeatMapFilter extends ChartTypeFilter implements FacetChartFilter {
   public HeatMapFilter(AssetEntry[] entries, VSChartInfo temp, List<List<ChartRef>> hgroup,
                        boolean autoOrder)
   {
      super(entries, temp, hgroup, autoOrder);
   }

   /**
    * HeatMap rule:
    * d > 1, m >= 1 && <= 2 (color, size)
    * d on x/y
    * m on color.
    */
   @Override
   public boolean isValid(ChartRefCombination comb) {
      if(getDimCount() < 2 || getMeaCount() < 1 || getMeaCount() > 2 || dateDimCount > 1) {
         return false;
      }

      IntList x = comb.getX();
      IntList y = comb.getY();
      IntList inside = comb.getInside();

      if(inside.size() != 1 && inside.size() != 2 || hasInsideDimension(comb) ||
         hasXMeasure(comb) || hasYMeasure(comb))
      {
         return false;
      }

      return hasInsideMeasure(comb);

   }

   @Override
   public VSChartInfo createChartInfo(ChartRefCombination comb) {
      List<ChartRef> refs = getAllRefs(true);
      VSChartInfo info = new VSChartInfo();
      info.setChartType(GraphTypes.CHART_POINT);
      addXFields(info, comb.getX(), refs);
      addYFields(info, comb.getY(), refs);
      addInsideField(info, comb, refs);

      return getClassyInfo(setupFacet(info));
   }

   @Override
   protected void putInside(VSChartInfo info, ChartRef ref) {
      // color is harder to compare values, but easier to find pattern. for un-ordered dimensions,
      // pattern is not very useful. so we give size priority for nominal dimension, and color
      // priority for date dimension

      if(hasDateDimension(info.getYFields()) || hasDateDimension(info.getXFields())) {
         if(info.getColorField() == null) {
            addColorField(info, ref);
         }
         else if(info.getSizeField() == null) {
            addSizeField(info, ref);
         }
      }
      else {
         if(info.getSizeField() == null) {
            addSizeField(info, ref);
         }
         else if(info.getColorField() == null) {
            addColorField(info, ref);
         }
      }
   }

   private void addColorField(VSChartInfo info, ChartRef ref) {
      VSAestheticRef aes = createAestheticRef(ref);
      info.setColorField(aes);
   }

   private void addSizeField(VSChartInfo info, ChartRef ref) {
      VSAestheticRef aes = createAestheticRef(ref);
      info.setSizeField(aes);
      info.setUnitHeightRatio(1.2);
      info.setHeightResized(true);

      StaticShapeFrameWrapper shape = new StaticShapeFrameWrapper();
      shape.setShape(ShapeFrameWrapper.getID(GShape.FILLED_SQUARE));
      info.setShapeFrameWrapper(shape);
   }

   private boolean hasDateDimension(ChartRef[] refs) {
      return Arrays.stream(refs).anyMatch(a -> isDateRef(a));
   }

   // For heatmap:
   // is one hierarchy, put last to y, others to x.
   // if one hierarchy + n, one h to x, others to x/y
   // if two hierarchy, one h to x, the other to y
   // if two hierarchy + n, two h to x/y, others to x/y
   // if n, n-1 to x, n to y
   @Override
   protected int getScore(ChartInfo chart) {
      int score = SECOND_SCORE;
      VSChartInfo info = (VSChartInfo) chart;

      if(!isValidFacet(info, hierarchyGroups)) {
         return -1000;
      }

      int xdims = ChartRecommenderUtil.getDimensions(info.getXFields()).size();
      int ydims = ChartRecommenderUtil.getDimensions(info.getYFields()).size();

      if(xdims + ydims > 2 && xdims > 0 && ydims > 0) {
         score = SPECIAL_PURPOSE_SCORE - 1;
      }
      else if(xdims == 0 && ydims > 1 || xdims > 1 && ydims == 0) {
         score -= 10;
      }

      if(!autoOrder) {
         return score;
      }

      if(hierarchyGroups.size() == 0) {
         // If no hierarchy, put d on to x, others to y.
         if(xdims > 0 && isSmallestX(chart)) {
            score += 3;
         }
         else {
            score -= 3;
         }
      }
      else if(hierarchyGroups.size() == 1) {
         ChartRef[] xy = new ChartRef[xdims + ydims];
         System.arraycopy(info.getXFields(), 0, xy, 0, xdims);
         System.arraycopy(info.getYFields(), 0, xy, xdims, ydims);

         // if 2 dims, we can change its order according to user's order.
         if(xdims == 1 && ydims == 1) {
            score += 3;
         }
         // only have one hierarchy group dimensions, not others.
         else if(xy.length == hierarchyGroups.get(0).size()) {
            // If has hierarchy, last col to y, others to x.
            if(ydims == 1 && isMatchHierarchy(xy, hierarchyGroups.get(0))) {
               score += 3;
            }
            else {
               score -= 3;
            }
         }
         else {
            // I has hierarchy, hierarchy to x, others x/y
            if(isMatchHierarchy(info.getXFields(), hierarchyGroups.get(0)) && ydims > 0) {
               score += 3;
            }
            else {
               score -= 3;
            }
         }
      }
      else if(hierarchyGroups.size() > 1) {
         ChartRef[] xfields = info.getXFields();
         ChartRef[] yfields = info.getYFields();
         List<ChartRef> xHierarchy = ChartRecommenderUtil.findHierarchy(xfields, hierarchyGroups);
         List<ChartRef> yHierarchy = ChartRecommenderUtil.findHierarchy(yfields, hierarchyGroups);

         score -= 6;

         if(yHierarchy != null) {
            score += 3;

            if(yfields.length <= yHierarchy.size() + 1) {
               score += 2;
            }
         }

         if(xHierarchy != null) {
            score += 2;

            if(xfields.length <= xHierarchy.size() + 1) {
               score += 1;
            }
         }
      }

      score += getAestheticScore(info);
      return score;
   }

   /**
    * When has no hierarchy, binding one dim to x, or binding some to x, some to y seems have no
    * much difference, and current combination will produce the first combination ealier that others,
    * so here just make sure the first x have the smallest cardinality in xy fields.
    */
   private boolean isSmallestX(ChartInfo chart) {
      VSChartInfo info = (VSChartInfo) chart;
      List<VSChartDimensionRef> fields = ChartRecommenderUtil.getDimensions(info.getXFields());

      if(fields.size() < 1) {
         return false;
      }

      fields.addAll(ChartRecommenderUtil.getDimensions(info.getYFields()));
      int xcardinality = getCardinality(fields.get(0));

      for(int i = 1; i < fields.size(); i++) {
         if(xcardinality > getCardinality(fields.get(i))) {
            return false;
         }
      }

      return true;
   }
}
