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
import inetsoft.web.vswizard.recommender.ChartRecommenderUtil;

import java.util.List;

public class FacetRadarChartFilter extends RadarChartFilter implements FacetChartFilter {
   public FacetRadarChartFilter(AssetEntry[] entries, VSChartInfo temp,
                                List<List<ChartRef>> hgroup, boolean autoOrder)
   {
      super(entries, temp, hgroup, autoOrder);
   }

   @Override
   protected VSChartInfo getClassyInfo(VSChartInfo info) {
      return super.getClassyInfo(setupFacet(info));
   }

   /**
    * Radar rule:
    * m >= 3.
    * inside <= 2 (color/shape).
    * m can only on y, m can't on x and inside.
    */
   @Override
   public boolean isValid(ChartRefCombination comb) {
      return comb.getX().size() == 0 && getDimCount() > 2 && comb.getY().size() > 1 &&
         isRadarValid(comb);
   }

   // For facet radar:
   // put all m on y, all hierarchy d on y.
   @Override
   protected int getScore(ChartInfo chart) {
      int score = SPECIAL_PURPOSE_SCORE;
      VSChartInfo info = (VSChartInfo) chart;

      if(!isValidFacet(info, hierarchyGroups)) {
         return -1000;
      }

      int ydims = ChartRecommenderUtil.getDimensions(info.getYFields()).size();

      if(!autoOrder) {
         return score;
      }

      if(hierarchyGroups.size() == 0) {
         // If no hierarchy, color cardinality is smaller than y dim
         ChartRef yfld = info.getYField(0);

         if(!yfld.isMeasure()) {
            if(info.getColorField() != null) {
               ChartRef color = (ChartRef) info.getColorField().getDataRef();
               int dintinctc = getCardinality(color);

               if(getCardinality(yfld) < dintinctc) {
                  score += 3;
               }
               else {
                  score -= 1000;
               }
            }
            else if(ydims < 2) {
               score += 3;
            }
            else {
               score -= 1000;
            }
         }
      }
      else if(hierarchyGroups.size() >= 1) {
         // If has hierarchy, only show hierarchy on y.
         if(isMatchHierarchy(info.getYFields(), hierarchyGroups.get(0))) {
            score += 3;
         }
         else {
            score -= 1000;
         }
      }

      // All m should on y.
      if(!GraphUtil.hasMeasureOnX(info) &&
         !ChartRecommenderUtil.hasMeasureOnAesthetic(info))
      {
         score += 3;
      }

      score += getAestheticScore(info);

      return score;
   }
}
