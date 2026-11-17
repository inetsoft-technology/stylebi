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
package inetsoft.web.wiz.viewsheet;

import inetsoft.graph.GraphConstants;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The one vocabulary of natural line-style names ({@code dashed}, {@code dotted}, {@code thin},
 * ...) the wiz tools accept, shared by the chart line styles ({@link ChartTargetLineService},
 * the grid/trend line properties of {@link AssemblyPropertyService}) and the Line/Oval/Rectangle
 * {@code lineStyle} property ({@link PropertyPath}), so a name means the same thing on every
 * assembly type.
 *
 * <p>Deliberately a leaf holder with no service references: {@code PropertyPath} is exercised
 * by tests that bootstrap no Spring context and must not pull a service class onto its load path.
 * {@code GraphConstants}' line styles are compile-time constants, inlined here.
 */
final class LineStyleNames {
   private LineStyleNames() {
   }

   /**
    * The nine {@code GraphConstants} line styles a chart offers, named. Insertion order is the
    * listed order. Each int equals the {@code StyleConstants} constant of the same name, which
    * is what {@link #SHAPE_TOKENS} names for a shape.
    */
   static final Map<String, Integer> CHART;

   /**
    * Natural name to the {@code StyleConstants} token a Line/Oval/Rectangle stores in its
    * {@code style} (what {@code Util.getStyleConstantsFromString} parses). The nine chart names
    * keep the same meaning; {@code double} has no chart counterpart. Insertion order is the
    * listed order.
    */
   static final Map<String, String> SHAPE_TOKENS;

   static {
      Map<String, Integer> chart = new LinkedHashMap<>();
      chart.put("ultra_thin", GraphConstants.ULTRA_THIN_LINE);
      chart.put("thin_thin", GraphConstants.THIN_THIN_LINE);
      chart.put("thin", GraphConstants.THIN_LINE);
      chart.put("medium", GraphConstants.MEDIUM_LINE);
      chart.put("thick", GraphConstants.THICK_LINE);
      chart.put("dotted", GraphConstants.DOT_LINE);
      chart.put("dashed", GraphConstants.DASH_LINE);
      chart.put("medium_dash", GraphConstants.MEDIUM_DASH);
      chart.put("large_dash", GraphConstants.LARGE_DASH);
      CHART = Collections.unmodifiableMap(chart);

      Map<String, String> shape = new LinkedHashMap<>();
      shape.put("none", "NONE");
      shape.put("ultra_thin", "ULTRA_THIN_LINE");
      shape.put("thin_thin", "THIN_THIN_LINE");
      shape.put("thin", "THIN_LINE");
      shape.put("medium", "MEDIUM_LINE");
      shape.put("thick", "THICK_LINE");
      shape.put("double", "DOUBLE_LINE");
      shape.put("dotted", "DOT_LINE");
      shape.put("dashed", "DASH_LINE");
      shape.put("medium_dash", "MEDIUM_DASH");
      shape.put("large_dash", "LARGE_DASH");
      SHAPE_TOKENS = Collections.unmodifiableMap(shape);
   }

   /**
    * The token a shape reads back as for the one it was written as: a no-line shape reads back
    * as {@code NO_BORDER} ({@code Util.getLineStyleName}), though {@code NONE} is what is
    * written. Every other token reads back as written.
    */
   static String readBackToken(String writeToken) {
      return "NONE".equals(writeToken) ? "NO_BORDER" : writeToken;
   }
}
