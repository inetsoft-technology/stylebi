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

import inetsoft.report.StyleConstants;
import inetsoft.uql.viewsheet.graph.*;

import java.util.*;

/**
 * The modern chart values that depend on the resolved chart type, one predicate per value.
 */
final class VSChartTypeDefaults {
   private VSChartTypeDefaults() {
   }

   /**
    * Whether a modern chart's value axis draws its line; non-rect types and Gantt keep theirs.
    */
   static boolean valueAxisLineVisible(ResolvedChartType t) {
      return !GraphTypes.isRect(t.type()) || GraphTypes.isGantt(t.type()) ||
         t.measureOnX() && t.measureOnY();
   }

   /**
    * Whether a modern chart shows the axis title; axis is one of x, x2, y and y2.
    */
   static boolean axisTitleVisible(ResolvedChartType t, String axis) {
      int type = t.type();

      if(GraphTypes.isPoint(type) || GraphTypes.isScatteredContour(type) ||
         GraphTypes.isPareto(type) || GraphTypes.isMekko(type))
      {
         return true;
      }

      if(!GraphTypes.isBoxplot(type)) {
         return false;
      }

      // a box plot names its value axis only, on X when it lies horizontally
      boolean xAxis = "x".equals(axis) || "x2".equals(axis);
      return xAxis == (t.measureOnX() && !t.measureOnY());
   }

   /**
    * The modern vertical grid style; a scatter with a measure on each axis gets a thin one.
    */
   static int xGridStyle(ResolvedChartType t) {
      boolean scatter = GraphTypes.isPoint(t.type()) || GraphTypes.isScatteredContour(t.type());
      return scatter && t.measureOnX() && t.measureOnY() ? StyleConstants.THIN_LINE :
         StyleConstants.NONE;
   }

   /**
    * Whether a modern chart draws smooth lines; only a circular network keeps curved chords.
    */
   static boolean smoothLines(int type) {
      return type == GraphTypes.CHART_CIRCULAR;
   }

   /**
    * The modern legend position; a colour ramp, a crowded legend and the non-cartesian families
    * sit on the right, everything else on top.
    */
   static int legendLayout(ResolvedChartType t, boolean colorRamp, boolean crowded) {
      int type = t.type();

      if(colorRamp || crowded || GraphTypes.isPie(type) || GraphTypes.isTreemap(type) ||
         GraphTypes.isFunnel(type) || GraphTypes.isMekko(type) || GraphTypes.isRadar(type) ||
         GraphTypes.isRelation(type) || GraphTypes.isGeo(type) || GraphTypes.isPolygon(type) ||
         GraphTypes.isContour(type))
      {
         return LegendsDescriptor.RIGHT;
      }

      return LegendsDescriptor.TOP;
   }

   /**
    * Whether a measure is on Color, in the chart or in any of its measures.
    */
   static boolean colorRamp(ChartInfo info) {
      if(info == null) {
         return false;
      }

      if(isMeasure(info.getColorField())) {
         return true;
      }

      if(info.isMultiStyles()) {
         for(ChartRef ref : info.getModelRefs(false)) {
            if(ref instanceof ChartAggregateRef agg && isMeasure(agg.getColorField())) {
               return true;
            }
         }
      }

      return info instanceof RelationChartInfo rel && isMeasure(rel.getNodeColorField());
   }

   private static boolean isMeasure(AestheticRef ref) {
      return ref != null && ref.isMeasure();
   }

   /**
    * The bound legend fields, each prefixed by its role where the chart's own accessor names it.
    */
   static List<String> legendBindingKey(ChartInfo info) {
      if(info == null) {
         return List.of();
      }

      Map<AestheticRef, String> roles = new IdentityHashMap<>();
      addRole(roles, info.getColorField(), "color:");
      addRole(roles, info.getShapeField(), "shape:");
      addRole(roles, info.getSizeField(), "size:");
      addRole(roles, info.getTextField(), "text:");

      if(info instanceof RelationChartInfo rel) {
         addRole(roles, rel.getNodeColorField(), "nodeColor:");
         addRole(roles, rel.getNodeSizeField(), "nodeSize:");
      }

      List<String> key = new ArrayList<>();

      for(AestheticRef ref : info.getAestheticRefs(false)) {
         if(ref != null) {
            key.add(roles.getOrDefault(ref, "") + Objects.toString(ref.getFullName(), ""));
         }
      }

      return List.copyOf(key);
   }

   private static void addRole(Map<AestheticRef, String> roles, AestheticRef ref, String role) {
      if(ref != null) {
         roles.put(ref, role);
      }
   }
}
