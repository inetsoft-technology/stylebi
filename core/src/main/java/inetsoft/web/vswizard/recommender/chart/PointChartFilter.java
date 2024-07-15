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

import inetsoft.graph.aesthetic.GShape;
import inetsoft.report.composition.graph.GraphTypeUtil;
import inetsoft.uql.CompositeValue;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.graph.aesthetic.*;
import it.unimi.dsi.fastutil.ints.IntList;

import java.util.Arrays;
import java.util.List;

public class PointChartFilter extends ChartTypeFilter {
   public PointChartFilter(AssetEntry[] entries, VSChartInfo temp, List<List<ChartRef>> hgroup,
                         boolean autoOrder)
   {
      super(entries, temp, hgroup, autoOrder);
   }

   /**
    * Point chart rule:
    * x and y can't have measure in the same time.
    * inside <= 3 for measure (color/shape/size).
    * inside <= 2 for dimension (color/shape).
    * x + y > 0 (x or y have one ref at least)
    */
   @Override
   public boolean isValid(ChartRefCombination comb) {
      if(dateDimCount > 1) {
         return false;
      }

      IntList x = comb.getX();
      IntList y = comb.getY();

      return x.size() <= 1 && y.size() <= 1 && isPointValid(comb);
   }

   protected boolean isPointValid(ChartRefCombination comb) {
      if(getDimCount() == 0 || getMeaCount() == 0) {
         return false;
      }

      IntList inside = comb.getInside();

      if(inside.size() > 3 || getDimensionCount(inside) > 2) {
         return false;
      }

      IntList x = comb.getX();
      IntList y = comb.getY();

      if(x.size() + y.size() == 0) {
         return false;
      }

      if(hasXMeasure(comb)) {
         return false;
      }

      return hasXDimension(comb) && hasYMeasure(comb);

   }

   @Override
   public VSChartInfo createChartInfo(ChartRefCombination comb) {
      VSChartInfo info = new VSChartInfo();
      info.setChartType(getChartType(comb));
      List<ChartRef> refs = getAllRefs(true);

      addXFields(info, comb.getX(), refs);
      addYFields(info, comb.getY(), refs);
      addInsideField(info, comb, refs);

      StaticSizeFrameWrapper size = new StaticSizeFrameWrapper();
      size.setSize(3, CompositeValue.Type.DEFAULT);
      info.setSizeFrameWrapper(size);

      StaticShapeFrameWrapper shape = new StaticShapeFrameWrapper();
      GShape point = GraphTypeUtil.isDotPlot(info) ? GShape.FILLED_CIRCLE : GShape.CIRCLE;
      shape.setShape(ShapeFrameWrapper.getID(point));
      info.setShapeFrameWrapper(shape);

      Arrays.stream(info.getBindingRefs(false))
         .filter(r -> r instanceof ChartAggregateRef)
         .forEach(r -> {
            ChartAggregateRef aggr = (ChartAggregateRef) r;
            aggr.setSizeFrameWrapper(size);
            aggr.setShapeFrameWrapper(shape);
            });

      return getClassyInfo(info);
   }

   protected int getChartType(ChartRefCombination comb) {
      return GraphTypes.CHART_POINT;
   }

   @Override
   protected void putInside(VSChartInfo info, ChartRef ref) {
      VSAestheticRef aes = createAestheticRef(ref);

      if(ref instanceof ChartDimensionRef) {
         if(info.getColorField() == null) {
            info.setColorField(aes);
         }
         else if(info.getShapeField() == null) {
            info.setShapeField(aes);
         }
      }
      else {
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
   }

   @Override
   protected int getScore(ChartInfo chart) {
      int score = SECOND_SCORE;
      VSChartInfo info = (VSChartInfo) chart;

      if(!autoOrder) {
         return score;
      }

      score += getAestheticScore(info);
      return score;
   }
}
