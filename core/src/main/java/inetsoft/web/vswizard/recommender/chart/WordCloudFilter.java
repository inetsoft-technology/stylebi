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

import inetsoft.report.composition.graph.GraphUtil;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.viewsheet.graph.*;
import it.unimi.dsi.fastutil.ints.IntList;

import java.util.List;

public class WordCloudFilter extends ChartTypeFilter {
   public WordCloudFilter(AssetEntry[] entries, VSChartInfo temp, List<List<ChartRef>> hgroup,
                          boolean autoOrder)
   {
      super(entries, temp, hgroup, autoOrder);
   }

   /**
    * WordCloud rule:
    * 1 string d
    * support facet(only x have d, y have d seems not proper).
    * inside---1d 2m(1d on text, 2m on color/size)
    */
   @Override
   public boolean isValid(ChartRefCombination comb) {
      if(dateDimCount > 1) {
         return false;
      }

      IntList x = comb.getX();
      IntList y = comb.getY();
      IntList inside = comb.getInside();

      // word cloud in crowded facet is not very readable and terribly slow
      if(y.size() + x.size() > 0) {
         return false;
      }

      if(inside.size() == 0 || inside.size() > 3) {
         return false;
      }

      // binding dimension on color is not really useful
      if(getDimensionCount(inside) > 1) {
         return false;
      }

      return hasStringDimension(inside);
   }

   @Override
   public VSChartInfo createChartInfo(ChartRefCombination comb) {
      List<ChartRef> refs = getAllRefs(true);
      VSChartInfo info = new VSChartInfo();
      info.setChartType(GraphTypes.CHART_POINT);
      addInsideField(info, comb, refs);

      //If do not have size, add text to size.
      if(info.getSizeField() == null && refs.size() > 0) {
         VSAestheticRef aes = getAestheticRef(0);
         info.setSizeField(aes);
         GraphUtil.fixVisualFrames(info);
      }

      return getClassyInfo(info);
   }

   @Override
   protected void putInside(VSChartInfo info, ChartRef ref) {
      VSAestheticRef aes = createAestheticRef(ref);

      if(ref instanceof ChartDimensionRef) {
         if(info.getTextField() == null) {
            info.setTextField(aes);
         }
         else if(info.getColorField() == null) {
            info.setColorField(aes);
         }
      }
      else {
         if(info.getSizeField() == null) {
            info.setSizeField(aes);
         }
         else if(info.getColorField() == null) {
            info.setColorField(aes);
         }
      }
   }

   // if auto-order, color's cardinality is smaller than text.
   @Override
   protected int getScore(ChartInfo chart) {
      int score = SPECIAL_PURPOSE_SCORE;

      if(!autoOrder) {
         return score;
      }

      VSChartInfo info = (VSChartInfo) chart;

      if(info.getTextField() != null && info.getColorField() != null) {
         ChartRef color = (ChartRef) info.getColorField().getDataRef();
         int dintinctc = getCardinality(color);
         ChartRef text = (ChartRef) info.getTextField().getDataRef();
         int dintinctt = getCardinality(text);

         // if two cardinality is equals, using default order.
         if(dintinctc <= dintinctt) {
            score += 3;
         }
      }

      score += getAestheticScore(info);
      return score;
   }
}
