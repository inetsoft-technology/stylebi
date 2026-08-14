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

import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.web.viewsheet.controller.chart.*;
import inetsoft.web.viewsheet.event.chart.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.*;

/**
 * A chart's sub-elements: axis, legend and title visibility, and plot sizing.
 *
 * <p>These are authoring operations rather than runtime exploration — they clone
 * {@code ChartVSAssemblyInfo} and mutate its descriptors, the same shape as every other
 * property write — so they belong with the viewsheet plugin rather than being excluded as
 * interactive analysis.
 *
 * <p><b>The underlying events have a footgun this class hides.</b> {@code isHide()} does not
 * mean "hide it": on the titles event, {@code hide=true} with {@code titleType="chart-title"}
 * hides the chart title, {@code hide=true} with {@code titleType="chart-title-true"}
 * <i>shows</i> it, and {@code hide=false} shows <i>all</i> titles regardless of which one you
 * named. An agent asked to "show the y axis title" that set {@code hide=false} would silently
 * un-hide every title the user had deliberately hidden.
 *
 * <p>So the agent-facing surface is {@code (element, target, visible)} and this class maps it
 * onto whatever combination the event actually needs.
 *
 * <p><b>The events are built with Jackson, not setters.</b> They expose getters over private
 * fields and declare no setters at all — Jackson reaches the field once the getter reveals the
 * property, which is exactly how the STOMP layer populates them in production. Building them
 * any other way would either need reflection or a parallel constructor this package does not
 * own. {@code ChartElementServiceTest} asserts the conversion actually lands its values,
 * because an all-default titles event means "show every title", which is the destructive case.
 */
@Service
public class ChartElementService {
   /** Titles addressable by name, mapped to the descriptor token the event expects. */
   private static final Map<String, String> AXIS_TITLES = Map.of(
      "x", "x_title",
      "x2", "x2_title",
      "y", "y_title",
      "y2", "y2_title");

   @Autowired
   public ChartElementService(ViewsheetSessionService sessions,
                              ObjectMapper objectMapper,
                              VSChartAxesVisibilityService axesService,
                              VSChartLegendsVisibilityService legendsService,
                              VSChartTitlesVisibilityService titlesService,
                              VSChartPlotResizeService plotResizeService)
   {
      this.sessions = sessions;
      this.objectMapper = objectMapper;
      this.axesService = axesService;
      this.legendsService = legendsService;
      this.titlesService = titlesService;
      this.plotResizeService = plotResizeService;
   }

   /**
    * Shows or hides one of a chart's sub-elements.
    *
    * @param element {@code axis}, {@code legend} or {@code title}
    * @param target  which one — an axis column name, a legend field, or an axis title
    *                ({@code x}, {@code x2}, {@code y}, {@code y2}) or {@code chart} for the
    *                chart title. Null means all of that element.
    */
   public void setVisibility(String sessionToken, Principal user, String assemblyName,
                             String element, String target, boolean visible, String linkUri)
      throws Exception
   {
      String kind = requireElement(element);

      // Resolved before the runtime is touched: "show the y title" and "show every title" are
      // different requests, and the event cannot express the first one, so a caller naming a
      // target it cannot honour has to be told rather than quietly given the second.
      if(visible && target != null && !"title".equals(kind)) {
         throw new IllegalArgumentException(
            "Showing a single " + kind + " is not supported by the Composer — showing restores " +
            "all of them at once. Call this without 'target' to show every " + kind + ", or " +
            "hide the ones you do not want.");
      }

      sessions.mutate(sessionToken, user, (rvs, runtimeId, dispatcher) -> {
         requireChart(rvs, assemblyName);

         switch(kind) {
            case "axis" -> axesService.eventHandler(
               runtimeId,
               event(Map.of("chartName", assemblyName, "hide", !visible), "columnName", target,
                     VSChartAxesVisibilityEvent.class),
               linkUri, user, dispatcher);
            case "legend" -> legendsService.eventHandler(
               runtimeId,
               event(Map.of("chartName", assemblyName, "hide", !visible), "field", target,
                     VSChartLegendsVisibilityEvent.class),
               linkUri, user, dispatcher);
            default -> titlesService.eventHandler(
               runtimeId,
               convert(titleFields(assemblyName, target, visible),
                       VSChartTitlesVisibilityEvent.class),
               linkUri, user, dispatcher);
         }
      });
   }

   /**
    * Resizes the plot area, or resets it.
    *
    * @param ratio    the plot's share of the assembly, 0 exclusive to 1 inclusive
    * @param vertical true to resize the height, false the width
    */
   public void resizePlot(String sessionToken, Principal user, String assemblyName, Double ratio,
                          boolean vertical, boolean reset, String linkUri) throws Exception
   {
      if(!reset && (ratio == null || ratio <= 0 || ratio > 1)) {
         throw new IllegalArgumentException(
            "resize_plot needs a 'ratio' greater than 0 and at most 1 — the plot's share of " +
            "the assembly. Got " + ratio + ". Pass reset:true to restore the default instead.");
      }

      sessions.mutate(sessionToken, user, (rvs, runtimeId, dispatcher) -> {
         requireChart(rvs, assemblyName);

         Map<String, Object> fields = new LinkedHashMap<>();
         fields.put("chartName", assemblyName);
         fields.put("reset", reset);
         fields.put("heightResized", vertical);
         fields.put("sizeRatio", reset || ratio == null ? 0d : ratio);
         plotResizeService.eventHandler(runtimeId, convert(fields, VSChartPlotResizeEvent.class),
                                        linkUri, user, dispatcher);
      });
   }

   public Map<String, Object> vocabulary() {
      return Map.of(
         "elements", List.of("axis", "legend", "title"),
         "titleTargets", List.of("x", "x2", "y", "y2", "chart"),
         "note", "An axis target is a column name and a legend target is a field name; call " +
            "get_binding for those. Showing a single axis or legend is not supported — " +
            "showing restores all of them.");
   }

   // ── the titles footgun, contained ─────────────────────────────────────────

   /**
    * Translates {@code (target, visible)} into the event's own idiom.
    *
    * <p>{@code hide=false} means "show every title", so it is only ever used when the caller
    * asked for exactly that. Showing one title goes through the event's
    * {@code chart-title-true} special case, and showing a single <i>axis</i> title has no
    * expression at all — which the caller is told rather than silently given all of them.
    */
   static Map<String, Object> titleFields(String assemblyName, String target, boolean visible) {
      Map<String, Object> fields = new LinkedHashMap<>();
      fields.put("chartName", assemblyName);

      if(target == null) {
         fields.put("hide", !visible);

         if(!visible) {
            fields.put("titleType", "chart-title");
         }

         return fields;
      }

      String name = target.trim().toLowerCase();

      if("chart".equals(name)) {
         // The event expresses "show the chart title" as hide=true plus this token, which is
         // why the agent-facing surface never exposes `hide`.
         fields.put("hide", true);
         fields.put("titleType", visible ? "chart-title-true" : "chart-title");
         return fields;
      }

      String axisTitle = AXIS_TITLES.get(name);

      if(axisTitle == null) {
         throw new IllegalArgumentException(
            "Unknown title target '" + target + "'. Valid targets: " +
            new TreeSet<>(AXIS_TITLES.keySet()) + ", or 'chart' for the chart title.");
      }

      if(visible) {
         throw new IllegalArgumentException(
            "Showing a single axis title is not supported by the Composer — showing restores " +
            "all titles at once. Call this without 'target' to show every title, or hide the " +
            "ones you do not want.");
      }

      fields.put("hide", true);
      fields.put("titleType", axisTitle);
      return fields;
   }

   private <T> T event(Map<String, Object> base, String targetField, String target,
                       Class<T> type)
   {
      Map<String, Object> fields = new LinkedHashMap<>(base);

      if(target != null) {
         fields.put(targetField, target);
      }

      return convert(fields, type);
   }

   private <T> T convert(Map<String, Object> fields, Class<T> type) {
      return objectMapper.convertValue(fields, type);
   }

   private static String requireElement(String element) {
      String kind = element == null ? "" : element.trim().toLowerCase();

      if(!List.of("axis", "legend", "title").contains(kind)) {
         throw new IllegalArgumentException(
            "'element' must be axis, legend or title, got '" + element + "'.");
      }

      return kind;
   }

   private static void requireChart(inetsoft.report.composition.RuntimeViewsheet rvs,
                                    String assemblyName)
   {
      Viewsheet vs = rvs == null ? null : rvs.getViewsheet();
      VSAssembly assembly = vs == null ? null : vs.getAssembly(assemblyName);

      if(assembly == null) {
         throw new IllegalArgumentException("Unknown assembly '" + assemblyName + "'.");
      }

      if(!(assembly instanceof ChartVSAssembly)) {
         throw new IllegalArgumentException(
            "'" + assemblyName + "' is a " + assembly.getClass().getSimpleName() +
            ", not a chart. Axes, legends and titles only exist on charts.");
      }
   }

   private final ViewsheetSessionService sessions;
   private final ObjectMapper objectMapper;
   private final VSChartAxesVisibilityService axesService;
   private final VSChartLegendsVisibilityService legendsService;
   private final VSChartTitlesVisibilityService titlesService;
   private final VSChartPlotResizeService plotResizeService;
}
