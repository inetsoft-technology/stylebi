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
import inetsoft.uql.viewsheet.internal.DataVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.MaxModeSupportAssembly;
import inetsoft.uql.viewsheet.internal.MaxModeSupportAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;
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
import java.util.List;
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
    * Whether {@link #setMaxMode} accepts the assembly: the same three-way dispatch it applies, so
    * a reader can tell "not enlarged" from "has no enlarged state".
    */
   public static boolean supportsMaxMode(Object assembly) {
      return assembly instanceof TableDataVSAssembly || assembly instanceof ChartVSAssembly ||
         assembly instanceof MaxModeSupportAssembly;
   }

   /**
    * The assembly's current enlarged size, or null when it is at actual size or has no such state.
    *
    * <p>Read from the assembly info the way the Viewer's own models do
    * ({@code VSObjectModel}, {@code VSChartModel}), not from {@code Viewsheet.isMaxMode()}: that
    * flag is sheet-wide and is not persisted, so a reopened sheet whose chart still carries its
    * saved {@code maxSize} reads false there. Chart and table infos override
    * {@code DataVSAssemblyInfo.getMaxSize()}; selection lists/trees/containers and range sliders
    * implement {@link MaxModeSupportAssemblyInfo}.
    */
   public static Dimension maxSizeOf(Object assembly) {
      VSAssemblyInfo info = assembly instanceof VSAssembly vsAssembly
         ? vsAssembly.getVSAssemblyInfo() : null;

      if(info instanceof DataVSAssemblyInfo data) {
         return data.getMaxSize();
      }

      return info instanceof MaxModeSupportAssemblyInfo support ? support.getMaxSize() : null;
   }

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

      List<String> warnings = sessions.mutate(sessionToken, user, (rvs, runtimeId, dispatcher) -> {
         Viewsheet vs = rvs.getViewsheet();
         VSAssembly assembly = vs == null ? null : vs.getAssembly(assemblyName);

         if(assembly == null) {
            throw new IllegalArgumentException("Unknown assembly '" + assemblyName + "'.");
         }

         // The Viewer offers no Show Enlarged on a Selection Container's child (only the container
         // itself can be enlarged), and enlarging one blanks the Viewer. Restoring stays allowed
         // so a sheet already in that state can be repaired: a child that carries a maxSize is
         // restored, and a child that does not has nothing to restore.
         VSAssembly container = assembly.getContainer();

         if(container instanceof CurrentSelectionVSAssembly) {
            if(maximized) {
               throw new IllegalArgumentException(
                  "'" + assemblyName + "' is inside Selection Container '" +
                  container.getAbsoluteName() + "'; maximize the container instead.");
            }

            if(maxSizeOf(assembly) == null) {
               // restoring would clear the sheet-wide flag a maximized container relies on
               result.put("assembly", assemblyName);
               result.put("maximized", false);
               return;
            }
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
               "'" + assemblyName + "' is a " + AssemblyPropertyService.displayTypeName(assembly) +
               ", which has no Maximize/Restore runtime state. Only tables/crosstabs/calc " +
               "tables, charts, selection lists/trees, range sliders and selection containers " +
               "support it.");
         }

         result.put("assembly", assemblyName);
         result.put("maximized", maximized);

         if(maxSize != null) {
            result.put("maxSize", Map.of("width", maxSize.width, "height", maxSize.height));
         }
      });

      ViewsheetSessionService.relayWarnings(result, warnings);
      return result;
   }

   private final ViewsheetSessionService sessions;
   private final VSTableMaxModeService tableMaxModeService;
   private final VSChartMaxModeService chartMaxModeService;
   private final MaxModeAssemblyService maxModeAssemblyService;
}
