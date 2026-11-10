/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
package inetsoft.uql.viewsheet.internal;

import inetsoft.report.composition.graph.GraphTypeUtil;
import inetsoft.report.composition.graph.GraphUtil;
import inetsoft.uql.viewsheet.graph.*;

import java.io.Serializable;
import java.util.*;

/**
 * The resolved chart type and design axis binding; a change re-runs the per-type seed.
 */
public record ResolvedChartType(int type, boolean measureOnX, boolean measureOnY,
                                List<String> xFields, List<String> yFields,
                                boolean mapPolygonOnly)
   implements Serializable
{
   /**
    * A key for a chart that is not a polygon-only map.
    */
   public ResolvedChartType(int type, boolean measureOnX, boolean measureOnY,
                            List<String> xFields, List<String> yFields)
   {
      this(type, measureOnX, measureOnY, xFields, yFields, false);
   }

   /**
    * The key for a chart info as it stands now.
    */
   public static ResolvedChartType of(ChartInfo info) {
      if(info == null) {
         return new ResolvedChartType(GraphTypes.CHART_AUTO, false, false, List.of(), List.of());
      }

      return new ResolvedChartType(resolvedType(info), GraphUtil.hasMeasureOnX(info),
                                   GraphUtil.hasMeasureOnY(info), names(info.getXFields()),
                                   names(info.getYFields()), isPolygonOnlyMap(info));
   }

   // a map's regions with no point layer or anchor points
   private static boolean isPolygonOnlyMap(ChartInfo info) {
      return GraphTypeUtil.isMap(info) && GraphUtil.containsMapPolygon(info) &&
         !GraphUtil.containsMapPoint(info);
   }

   private static int resolvedType(ChartInfo info) {
      if(info.isMultiStyles()) {
         // first measure, Y before X; design refs carry the runtime type before execution
         for(ChartRef[] refs : new ChartRef[][] { info.getRTYFields(), info.getRTXFields(),
                                                  info.getYFields(), info.getXFields() }) {
            for(ChartRef ref : refs == null ? new ChartRef[0] : refs) {
               if(ref instanceof ChartAggregateRef agg) {
                  return agg.getRTChartType();
               }
            }
         }
      }

      return info.getRTChartType();
   }

   private static List<String> names(ChartRef[] refs) {
      List<String> names = new ArrayList<>();

      for(ChartRef ref : refs == null ? new ChartRef[0] : refs) {
         // a ref with no name yet still holds its place
         names.add(ref == null ? "" : Objects.toString(ref.getFullName(), ""));
      }

      return List.copyOf(names);
   }
}
