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
import inetsoft.report.composition.graph.GraphUtil;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.graph.aesthetic.StaticShapeFrameWrapper;
import it.unimi.dsi.fastutil.ints.IntList;

import java.util.Arrays;
import java.util.List;

public class ScatterChartFilter extends ChartTypeFilter {
   public ScatterChartFilter(AssetEntry[] entries, VSChartInfo temp, List<List<ChartRef>> hgroup,
                             boolean autoOrder)
   {
      super(entries, temp, hgroup, autoOrder);
   }

   /**
    * Point chart rule:
    * m > 2.
    * inside <= 3 (d on shape/color, m on size/color/shape).
    * x + y > 0 (x or y only have one ref at least)
    *
    */
   @Override
   public boolean isValid(ChartRefCombination comb) {
      if(hasAggCalc(temp)) {
         return false;
      }

      if(dateDimCount > 1) {
         return false;
      }

      IntList x = comb.getX();
      IntList y = comb.getY();

      return x.size() == 1 && y.size() == 1 && isPointValid(comb);
   }

   protected boolean isPointValid(ChartRefCombination comb) {
      IntList inside = comb.getInside();

      if(inside.size() > 3) {
         return false;
      }

      if(getMeaCount() < 2 || getMeaCount() > 5) {
         return false;
      }

      IntList x = comb.getX();
      IntList y = comb.getY();

      if(!hasXMeasure(comb) || !hasYMeasure(comb)) {
         return false;
      }

      return inside.size() != 3 || hasInsideMeasure(comb);

   }

   @Override
   public VSChartInfo createChartInfo(ChartRefCombination comb) {
      boolean clearFormula = hasXMeasure(comb) && hasYMeasure(comb);
      List<ChartRef> refs = getAllRefs(true);
      VSChartInfo info = new VSChartInfo();
      info.setChartType(GraphTypes.CHART_POINT);
      addXFields(info, comb.getX(), refs);
      addYFields(info, comb.getY(), refs);
      addInsideField(info, comb, refs);

      if(clearFormula) {
         info.setClearedFormula(clearFormula(info));

         if(info.getShapeField() == null) {
            StaticShapeFrameWrapper shape = new StaticShapeFrameWrapper();
            shape.setShape(StaticShapeFrameWrapper.getID(GShape.CIRCLE));

            // use unfilled circle which is better for overlapped points
            Arrays.stream(info.getYFields())
               .filter(f -> f instanceof VSChartAggregateRef)
               .forEach(f -> ((VSChartAggregateRef) f).setShapeFrameWrapper(shape));
         }
      }

      return getClassyInfo(info);
   }

   // Do not sort ref, add inside field by select order.
   protected void addInsideField(VSChartInfo info, ChartRefCombination comb, List<ChartRef> refs) {
      getRefs(comb.getInside(), refs).forEach(ref -> putInside(info, ref));
      GraphUtil.fixVisualFrames(info);
   }

   @Override
   protected void putInside(VSChartInfo info, ChartRef ref) {
      VSAestheticRef aes = createAestheticRef(ref);

      if(ref.isMeasure()) {
         // size/color/shape
         if(info.getSizeField() == null) {
            info.setSizeField(aes);
         }
         else if(info.getColorField() == null) {
            info.setColorField(aes);
         }
         else if(info.getShapeField() == null) {
            info.setShapeField(aes);
         }
      }
      else {
         // shape/color
         if(info.getShapeField() == null) {
            info.setShapeField(aes);
         }
         else if(info.getColorField() == null) {
            info.setColorField(aes);
         }
      }
   }

   @Override
   protected int getScore(ChartInfo chart) {
      VSChartInfo info = (VSChartInfo) chart;

      int score = PRIMARY_SCORE;
      score += getAestheticScore(info);
      return score;
   }
}
