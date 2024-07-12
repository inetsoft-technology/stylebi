/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.vswizard.recommender.object;

import inetsoft.uql.viewsheet.graph.ChartInfo;
import inetsoft.uql.viewsheet.graph.VSChartInfo;

import java.util.Comparator;
import java.util.Map;

public class VSChartScoreComparator implements Comparator<ChartInfo> {
   public VSChartScoreComparator(Map<Integer, Integer> scores) {
      this.scores = scores;
   }

   /**
    * Compare two infos.
    */
   @Override
   public int compare(ChartInfo chart1, ChartInfo chart2) {
      if(getScore(chart1) == getScore(chart2)) {
         return 0;
      }

      if(getScore(chart1) > getScore(chart2)) {
         return -1;
      }

      return 1;
   }

   private int getScore(ChartInfo info) {
      VSChartInfo cinfo = (VSChartInfo) info;

      if(scores == null) {
         return 0;
      }

      return scores.get(info.hashCode());
   }

   Map<Integer, Integer> scores;
}