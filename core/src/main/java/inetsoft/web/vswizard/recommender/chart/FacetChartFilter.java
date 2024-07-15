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

import inetsoft.graph.internal.GDefaults;
import inetsoft.report.composition.graph.GraphTypeUtil;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.web.vswizard.recommender.ChartRecommenderUtil;

import java.util.List;
import java.util.stream.Collectors;

public interface FacetChartFilter {
   int getCardinality(ChartRef ref);

   default VSChartInfo setupFacet(VSChartInfo info) {
      if(GraphTypes.isPie(info.getChartType())) {
         info.setUnitHeightRatio(4);
      }
      else if(GraphTypeUtil.isHeatMapish(info)) {
         info.setUnitHeightRatio(1);
      }
      else {
         info.setUnitHeightRatio(2);
      }

      info.setHeightResized(true);
      return info;
   }

   // global facet validity list.
   default boolean isValidFacet(VSChartInfo info, List<List<ChartRef>> hiers) {
      List<VSChartDimensionRef> xdims = ChartRecommenderUtil.getDimensions(info.getXFields());
      List<VSChartDimensionRef> ydims = ChartRecommenderUtil.getDimensions(info.getYFields());
      List<ChartRef> xhier = ChartRecommenderUtil.findHierarchy(info.getXFields(), hiers);
      List<ChartRef> yhier = ChartRecommenderUtil.findHierarchy(info.getYFields(), hiers);
      // date dimensions
      List<VSChartDimensionRef> xdates = xdims.stream()
         .filter(dim -> dim.getDataRef() != null &&
            XSchema.isDateType(dim.getDataRef().getDataType()))
         .collect(Collectors.toList());
      List<VSChartDimensionRef> ydates = ydims.stream()
         .filter(dim -> dim.getDataRef() != null &&
            XSchema.isDateType(dim.getDataRef().getDataType()))
         .collect(Collectors.toList());

      // date columns are hierarchical but don't have hierarchy in groupHierarchy
      if(xdates.size() > 0) {
         if(xdims.size() - xdates.size() > 1) {
            return false;
         }
      }
      // if there are 3 or more dimensions on a direction, they must be in one hierarchy + 1 dim
      else if(xdims.size() > 2) {
         if(xhier == null || xdims.size() > xhier.size() + 1) {
            return false;
         }
      }

      if(ydates.size() > 0) {
         if(ydims.size() - ydates.size() > 1) {
            return false;
         }
      }
      else if(ydims.size() > 2) {
         if(yhier == null || ydims.size() > yhier.size() + 1) {
            return false;
         }
      }

      xdims.removeAll(xdates);
      ydims.removeAll(ydates);

      long xcardinality = xdates.size() > 0 ? getCardinality(xdates.get(xdates.size() - 1)) : 1;
      long ycardinality = ydates.size() > 0 ? getCardinality(ydates.get(ydates.size() - 1)) : 1;

      for(int i = xhier != null && xhier.size() > 0 ? xhier.size() - 1 : 0; i < xdims.size(); i++) {
         xcardinality *= getCardinality(xdims.get(i));
      }

      for(int i = yhier != null && yhier.size() > 0 ? yhier.size() - 1 : 0; i < ydims.size(); i++) {
         ycardinality *= getCardinality(ydims.get(i));
      }

      return xcardinality * ycardinality <= GDefaults.MAX_GGRAPH_COUNT;

   }
}
