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

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.SelectionBaseVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.SelectionListVSAssemblyInfo;
import inetsoft.web.composer.vs.objects.controller.ComposerVSSelectionListService;
import inetsoft.web.composer.vs.objects.event.VSObjectEvent;
import inetsoft.web.composer.vs.objects.event.VSSetMeasuresEvent;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A Selection List's / Tree's design-time layout: the list's column count and the pixel widths of
 * the measure text and measure bar. In the Composer these are set only by dragging (the cell's
 * right handle, the measure dividers), so they are in no property-dialog model.
 *
 * <p>This delegates to {@link ComposerVSSelectionListService}, the service the Composer's
 * websocket handlers call. That service validates nothing (the bounds live in the Angular client,
 * its type check is an {@code assert}), so the client bounds are re-implemented here for the agent
 * path:
 * <ul>
 *   <li>column count is list-only, an integer {@code >= 1}, and clamped to the number of values
 *       (the cell-drag cap in {@code vs-selection.component.ts});</li>
 *   <li>a measure width is {@code -1} (auto) or an integer {@code >= 1}. 0 is refused because the
 *       renderers disagree on it (auto for the bar, zero width for the text);</li>
 *   <li>the Composer service overwrites both measure widths together, so a caller giving only one
 *       gets the other's current stored value merged in.</li>
 * </ul>
 */
@Service
public class SelectionLayoutService {
   public SelectionLayoutService(ViewsheetSessionService sessions,
                                 ComposerVSSelectionListService selectionListService)
   {
      this.sessions = sessions;
      this.selectionListService = selectionListService;
   }

   /**
    * @param columnCount      optional new column count (lists only).
    * @param measureTextWidth optional new measure text width in pixels, or -1 for auto.
    * @param measureBarWidth  optional new measure bar width in pixels, or -1 for auto.
    *
    * @return the post-write layout snapshot (see {@link #snapshot}) plus {@code warnings}.
    */
   public Map<String, Object> setLayout(String sessionToken, Principal user, String assemblyName,
                                        Number columnCount, Number measureTextWidth,
                                        Number measureBarWidth)
      throws Exception
   {
      requireName(assemblyName);

      if(columnCount == null && measureTextWidth == null && measureBarWidth == null) {
         throw new IllegalArgumentException(
            "Nothing to set — give at least one of 'columnCount', 'measureTextWidth', " +
            "'measureBarWidth'.");
      }

      final Integer requestedCols = columnCount == null
         ? null : requireInt("columnCount", columnCount);
      final Integer text = measureTextWidth == null
         ? null : requireWidth("measureTextWidth", measureTextWidth);
      final Integer bar = measureBarWidth == null
         ? null : requireWidth("measureBarWidth", measureBarWidth);

      if(requestedCols != null && requestedCols < 1) {
         throw new IllegalArgumentException(
            "'columnCount' must be an integer >= 1, got " + requestedCols + ".");
      }

      final Map<String, Object> result = new LinkedHashMap<>();
      final List<String> own = new ArrayList<>();

      List<String> captured = sessions.mutate(sessionToken, user, (rvs, runtimeId, dispatcher) -> {
         VSAssembly assembly = resolve(rvs, assemblyName);
         boolean list = assembly instanceof SelectionListVSAssembly;

         if(requestedCols != null && !list) {
            throw new IllegalArgumentException(
               "'columnCount' applies to a Selection List only; '" + assemblyName + "' is a " +
               "Selection Tree, which has a single column. The measure widths can be set on it.");
         }

         SelectionBaseVSAssemblyInfo info =
            (SelectionBaseVSAssemblyInfo) assembly.getVSAssemblyInfo();

         if(requestedCols != null) {
            int count = valueCount(assembly);
            int applied = requestedCols;

            if(count > 0 && requestedCols > count) {
               applied = count;
               own.add("columnCount " + requestedCols + " exceeds the list's " + count +
                       " value(s); set to " + applied + " (the Composer caps columns at the " +
                       "value count).");
            }
            else if(count == 0 && requestedCols > 1) {
               own.add("The list has no values loaded, so columnCount " + requestedCols +
                       " could not be checked against the value count.");
            }

            VSObjectEvent event = new VSObjectEvent();
            event.setName(assemblyName);
            selectionListService.changeColCount(runtimeId, applied, event, user, dispatcher);
         }

         if(text != null || bar != null) {
            VSSetMeasuresEvent event = new VSSetMeasuresEvent();
            event.setName(assemblyName);
            event.setTextWidth(text != null ? text : info.getMeasureSize());
            event.setBarWidth(bar != null ? bar : info.getBarSize());
            selectionListService.updateMeasureSize(runtimeId, event, user, dispatcher);

            if(info.getMeasure() == null) {
               own.add("No measure is bound to '" + assemblyName + "', so the measure widths " +
                       "have no visible effect until one is.");
            }
            else {
               if(text != null && !info.isShowText()) {
                  own.add("showText is off, so measureTextWidth is stored but not visible.");
               }

               if(bar != null && !info.isShowBar()) {
                  own.add("showBar is off, so measureBarWidth is stored but not visible.");
               }
            }
         }

         result.putAll(snapshot(assemblyName, assembly));
      });

      List<String> warnings = new ArrayList<>(own);

      if(captured != null) {
         warnings.addAll(captured);
      }

      ViewsheetSessionService.relayWarnings(result, warnings);
      return result;
   }

   /** The stored layout of a Selection List/Tree; no checkpoint, no broadcast. */
   public Map<String, Object> getLayout(String sessionToken, Principal user, String assemblyName)
      throws Exception
   {
      requireName(assemblyName);
      return sessions.read(sessionToken, user, (rvs, runtimeId, dispatcher) ->
         snapshot(assemblyName, resolve(rvs, assemblyName)));
   }

   /**
    * Raw stored values: {@code -1} is auto. {@code columnCount} and {@code valueCount} are
    * present for a list only.
    */
   Map<String, Object> snapshot(String assemblyName, VSAssembly assembly) {
      SelectionBaseVSAssemblyInfo info = (SelectionBaseVSAssemblyInfo) assembly.getVSAssemblyInfo();
      Map<String, Object> out = new LinkedHashMap<>();
      out.put("assembly", assemblyName);

      if(assembly instanceof SelectionListVSAssembly) {
         out.put("type", "selection_list");
         out.put("columnCount", ((SelectionListVSAssemblyInfo) info).getColumnCount());
         out.put("valueCount", valueCount(assembly));
      }
      else {
         out.put("type", "selection_tree");
      }

      out.put("measureTextWidth", info.getMeasureSize());
      out.put("measureBarWidth", info.getBarSize());
      out.put("measure", info.getMeasure());
      out.put("showText", info.isShowText());
      out.put("showBar", info.isShowBar());
      return out;
   }

   /** Package-private so tests can supply a count without initialising {@link SelectionList}. */
   int valueCount(VSAssembly assembly) {
      SelectionList values = ((SelectionListVSAssembly) assembly).getSelectionList();
      SelectionValue[] array = values == null ? null : values.getSelectionValues();
      return array == null ? 0 : array.length;
   }

   private static VSAssembly resolve(RuntimeViewsheet rvs, String assemblyName) {
      Viewsheet vs = rvs.getViewsheet();
      VSAssembly assembly = vs == null ? null : vs.getAssembly(assemblyName);

      if(assembly == null) {
         throw new IllegalArgumentException("Unknown assembly '" + assemblyName + "'.");
      }

      if(!(assembly instanceof SelectionListVSAssembly) &&
         !(assembly instanceof SelectionTreeVSAssembly))
      {
         throw new IllegalArgumentException(
            "'" + assemblyName + "' is a " + assembly.getClass().getSimpleName() +
            ", not a Selection List or Selection Tree.");
      }

      return assembly;
   }

   private static void requireName(String assemblyName) {
      if(assemblyName == null || assemblyName.isBlank()) {
         throw new IllegalArgumentException(
            "'assembly' is required — name the Selection List or Tree.");
      }
   }

   private static int requireInt(String field, Number value) {
      double d = value.doubleValue();

      if(d != Math.rint(d) || Double.isInfinite(d) || d > Integer.MAX_VALUE ||
         d < Integer.MIN_VALUE)
      {
         throw new IllegalArgumentException("'" + field + "' must be an integer, got " + value + ".");
      }

      return (int) d;
   }

   private static int requireWidth(String field, Number value) {
      int width = requireInt(field, value);

      if(width != -1 && width < 1) {
         throw new IllegalArgumentException(
            "'" + field + "' must be -1 (auto) or a pixel width >= 1, got " + width + ".");
      }

      return width;
   }

   private final ViewsheetSessionService sessions;
   private final ComposerVSSelectionListService selectionListService;
}
