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

import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.MaxModeSupportAssembly;
import inetsoft.web.viewsheet.controller.chart.VSChartMaxModeService;
import inetsoft.web.viewsheet.controller.table.VSTableMaxModeService;
import inetsoft.web.viewsheet.event.chart.VSChartEvent;
import inetsoft.web.viewsheet.event.table.ImmutableMaxTableEvent;
import inetsoft.web.viewsheet.event.table.MaxTableEvent;
import inetsoft.web.viewsheet.service.MaxModeAssemblyService;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A viewsheet assembly's runtime Maximize/Restore state -- the title-bar "Show Enlarged"/
 * "Show Actual Size" toggle. Native StyleBI splits this across three assembly-type-scoped
 * STOMP destinations, each backed by its own {@code @ClusterProxy} service
 * ({@link VSTableMaxModeService} for Table/Crosstab/CalcTable, {@link VSChartMaxModeService}
 * for Chart, {@link MaxModeAssemblyService} for everything else that supports it, e.g.
 * Selection List/Tree and Range Slider) -- there is no single shared method one layer down.
 * This service type-dispatches to the right one, calling its local implementation directly
 * (the same in-process-bypass convention {@link InputValueService} already uses for
 * {@code VSInputService}).
 *
 * <p>{@code maxSize == null} means restore, on every one of the three native services alike.
 * There is no server-side equivalent of the real browser-viewport measurement the Viewer's own
 * button sends when maximizing ({@code GuiTool.getMaxModeSize()}), so a headless caller that
 * does not supply an explicit size gets a fixed "typical desktop viewport" default instead --
 * see {@link #DEFAULT_MAX_WIDTH}/{@link #DEFAULT_MAX_HEIGHT}.
 */
@Service
public class AssemblyMaxModeService {
   /**
    * Mirrors the shape of {@code GuiTool.getMaxModeSize()}'s own no-container fallback (a
    * typical browser window size, minus the same fixed margins that fallback subtracts) --
    * there is no real viewport for a headless caller to measure, so this stands in for one.
    */
   private static final int DEFAULT_VIEWPORT_WIDTH = 1600;
   private static final int DEFAULT_VIEWPORT_HEIGHT = 900;
   static final int DEFAULT_MAX_WIDTH = DEFAULT_VIEWPORT_WIDTH - 20;
   static final int DEFAULT_MAX_HEIGHT = DEFAULT_VIEWPORT_HEIGHT - 75;

   public AssemblyMaxModeService(ViewsheetSessionService sessions,
                                 VSTableMaxModeService tableMaxModeService,
                                 VSChartMaxModeService chartMaxModeService,
                                 MaxModeAssemblyService maxModeAssemblyService)
   {
      this.sessions = sessions;
      this.tableMaxModeService = tableMaxModeService;
      this.chartMaxModeService = chartMaxModeService;
      this.maxModeAssemblyService = maxModeAssemblyService;
   }

   /**
    * @param width  optional caller-supplied override for the maximized size (e.g. from a prior
    *               render the caller already knows the effective canvas dimensions of). Ignored
    *               when {@code maximized} is false. Must be positive when given -- refused
    *               loud rather than silently building a degenerate {@link Dimension}, since this
    *               service is reachable directly and not only through the plugin's own tool.
    * @param height see {@code width}.
    */
   public Map<String, Object> setMaxMode(String sessionToken, Principal user, String assemblyName,
                                         boolean maximized, Integer width, Integer height,
                                         String linkUri)
      throws Exception
   {
      if(assemblyName == null || assemblyName.isBlank()) {
         throw new IllegalArgumentException(
            "'assembly' is required — name the assembly to maximize or restore.");
      }

      if(width != null && width <= 0) {
         throw new IllegalArgumentException("'width' must be a positive number, got " + width + ".");
      }

      if(height != null && height <= 0) {
         throw new IllegalArgumentException("'height' must be a positive number, got " + height + ".");
      }

      Dimension maxSize = maximized
         ? new Dimension(width != null ? width : DEFAULT_MAX_WIDTH,
                         height != null ? height : DEFAULT_MAX_HEIGHT)
         : null;

      final Map<String, Object> result = new LinkedHashMap<>();

      sessions.mutate(sessionToken, user, (rvs, runtimeId, dispatcher) -> {
         Viewsheet vs = rvs.getViewsheet();
         VSAssembly assembly = vs == null ? null : vs.getAssembly(assemblyName);

         if(assembly == null) {
            throw new IllegalArgumentException("Unknown assembly '" + assemblyName + "'.");
         }

         if(assembly instanceof TableDataVSAssembly) {
            MaxTableEvent event = ImmutableMaxTableEvent.builder()
               .maxSize(maxSize)
               .tableName(assemblyName)
               .width(0)
               .height(0)
               .build();
            tableMaxModeService.toggleMaxMode(runtimeId, event, user, linkUri, dispatcher);
         }
         else if(assembly instanceof ChartVSAssembly) {
            VSChartEvent event = new VSChartEvent();
            event.setChartName(assemblyName);
            event.setMaxSize(maxSize);
            chartMaxModeService.eventHandler(runtimeId, event, linkUri, user, dispatcher);
         }
         else if(assembly instanceof MaxModeSupportAssembly) {
            maxModeAssemblyService.toggleMaxMode(rvs, assemblyName, maxSize, dispatcher, linkUri);
         }
         else {
            throw new IllegalArgumentException(
               "'" + assemblyName + "' is a " + assembly.getClass().getSimpleName() +
               ", which has no Maximize/Restore runtime state. Only tables/crosstabs/calc " +
               "tables, charts, selection lists/trees and range sliders support it.");
         }

         result.put("assembly", assemblyName);
         result.put("maximized", maximized);

         if(maxSize != null) {
            result.put("maxSize", Map.of("width", maxSize.width, "height", maxSize.height));
         }
      });

      return result;
   }

   private final ViewsheetSessionService sessions;
   private final VSTableMaxModeService tableMaxModeService;
   private final VSChartMaxModeService chartMaxModeService;
   private final MaxModeAssemblyService maxModeAssemblyService;
}
