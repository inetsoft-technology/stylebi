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
package inetsoft.report.script.viewsheet;

import inetsoft.uql.viewsheet.graph.*;
import inetsoft.util.Tool;

/**
 * Classifies what an in-place edit changed in a chart's binding, to tell which of the
 * sandbox's cached results the edit invalidates.
 */
final class ChartInfoDiff {
   /**
    * What changed between two states of a chart info.
    */
   enum Change {
      /** Nothing: both caches stay valid. */
      NONE,
      /** Only how the chart is drawn (frames, axes, highlights, tooltip): the data stays valid. */
      GRAPH,
      /** The binding that produces the chart's data (fields, ranking, formula, ...). */
      DATA
   }

   private ChartInfoDiff() {
   }

   /**
    * Compare the chart info after an edit with a snapshot taken before it. The data-affecting
    * part mirrors the binding members of {@link AbstractChartInfo#equalsContent}, i.e. every
    * member except the visual ones.
    */
   static Change diff(VSChartInfo before, VSChartInfo after) {
      if(before == null || after == null) {
         return Change.NONE;
      }

      if(!sameData(before, after)) {
         return Change.DATA;
      }

      return after.equalsContent(before) ? Change.NONE : Change.GRAPH;
   }

   private static boolean sameData(VSChartInfo before, VSChartInfo after) {
      return before.getChartType() == after.getChartType() &&
         before.isAdhocEnabled() == after.isAdhocEnabled() &&
         Tool.equals(before.getMapType(), after.getMapType()) &&
         Tool.equalsContent(before.getXFields(), after.getXFields()) &&
         Tool.equalsContent(before.getYFields(), after.getYFields()) &&
         Tool.equalsContent(before.getGroupFields(), after.getGroupFields()) &&
         Tool.equalsContent(geoFields(before), geoFields(after)) &&
         Tool.equalsContent(before.getGeoColumns(), after.getGeoColumns()) &&
         Tool.equalsContent(before.getPathField(), after.getPathField()) &&
         Tool.equalsContent(before.getColorField(), after.getColorField()) &&
         Tool.equalsContent(before.getShapeField(), after.getShapeField()) &&
         Tool.equalsContent(before.getSizeField(), after.getSizeField()) &&
         Tool.equalsContent(before.getTextField(), after.getTextField()) &&
         Tool.equalsContent(before.getAggregateInfo(), after.getAggregateInfo());
   }

   private static ChartRef[] geoFields(VSChartInfo info) {
      return info instanceof VSMapInfo ? ((VSMapInfo) info).getGeoFields() : new ChartRef[0];
   }
}
