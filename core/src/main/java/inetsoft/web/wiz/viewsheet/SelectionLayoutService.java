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
import inetsoft.uql.viewsheet.internal.CurrentSelectionVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.SelectionBaseVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.SelectionListVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.SelectionTreeVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.SelectionVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.TimeSliderVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;
import inetsoft.web.composer.vs.objects.controller.ComposerCurrentSelectionService;
import inetsoft.web.composer.vs.objects.controller.ComposerVSSelectionListService;
import inetsoft.web.composer.vs.objects.controller.VSObjectPropertyService;
import inetsoft.web.composer.vs.objects.event.VSObjectEvent;
import inetsoft.web.composer.vs.objects.event.VSSetMeasuresEvent;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.VSObjectService;
import inetsoft.web.viewsheet.service.VSSelectionContainerService;
import org.springframework.stereotype.Service;

import java.awt.Dimension;
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
 *
 * <p>It also carries two more pieces of filter view state that have no property-dialog field:
 * <ul>
 *   <li>{@code collapsed}: the collapse/expand of a Selection List or Range Slider that sits in a
 *       Selection Container, delegated to {@link VSSelectionContainerService#applySelection}. That
 *       service is toggle-by-state (hide=true on a collapsed child expands it), collapses expanded
 *       siblings to fit when a child expands, and DELETES a target that has no container, so it is
 *       called only for a child of a container that is not already in the requested state;</li>
 *   <li>{@code expandAll}: a Selection Tree's persisted "expand all nodes" flag.</li>
 *   <li>{@code titleRatio}: the fraction of a Selection Container's row width given to the
 *       title label (the rest goes to the selected text). It is ONE value on the container,
 *       shared by every filter in it and by its current-selection rows, written through
 *       {@link ComposerCurrentSelectionService#setTitleRatio}. A child filter as the target is
 *       resolved to its container. The service clamps it silently (min 0.05, max
 *       (width - 50) / width of the container's base layout) and ignores NaN, so the value is
 *       range-checked up front, re-read, and a clamp is reported as a warning. A Selection Tree
 *       or Calendar child does not render the split at all.</li>
 * </ul>
 */
@Service
public class SelectionLayoutService {
   public SelectionLayoutService(ViewsheetSessionService sessions,
                                 ComposerVSSelectionListService selectionListService,
                                 VSSelectionContainerService containerService,
                                 VSObjectPropertyService propertyService,
                                 VSObjectService objectService,
                                 ComposerCurrentSelectionService currentSelectionService)
   {
      this.sessions = sessions;
      this.selectionListService = selectionListService;
      this.containerService = containerService;
      this.propertyService = propertyService;
      this.objectService = objectService;
      this.currentSelectionService = currentSelectionService;
   }

   /**
    * @param columnCount      optional new column count (lists only).
    * @param measureTextWidth optional new measure text width in pixels, or -1 for auto.
    * @param measureBarWidth  optional new measure bar width in pixels, or -1 for auto.
    * @param collapsed        optional: collapse (true) or expand (false) a list or range slider
    *                         that is a child of a Selection Container.
    * @param expandAll        optional: a Selection Tree's expand-all flag (trees only).
    * @param titleRatio       optional: the Selection Container's title / selected-text width
    *                         fraction, strictly between 0 and 1. The target may be the container
    *                         or a filter inside it.
    *
    * @return the post-write layout snapshot (see {@link #snapshot}) plus {@code warnings}.
    */
   public Map<String, Object> setLayout(String sessionToken, Principal user, String assemblyName,
                                        Number columnCount, Number measureTextWidth,
                                        Number measureBarWidth, Boolean collapsed,
                                        Boolean expandAll, Number titleRatio, String linkUri)
      throws Exception
   {
      requireName(assemblyName);

      if(columnCount == null && measureTextWidth == null && measureBarWidth == null &&
         collapsed == null && expandAll == null && titleRatio == null)
      {
         throw new IllegalArgumentException(
            "Nothing to set — give at least one of 'columnCount', 'measureTextWidth', " +
            "'measureBarWidth', 'collapsed', 'expandAll', 'titleRatio'.");
      }

      final Double ratio = titleRatio == null ? null : requireRatio(titleRatio);

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

      final boolean widths = text != null || bar != null;

      // Refuse before mutate(): a refused call inside it still costs an undo step and a revision.
      sessions.read(sessionToken, user, (rvs, runtimeId, dispatcher) -> {
         requireWritable(assemblyName,
                         resolve(rvs, assemblyName, collapsed != null, ratio != null),
                         requestedCols, widths, collapsed, expandAll, ratio);
         return null;
      });

      final Map<String, Object> result = new LinkedHashMap<>();
      final List<String> own = new ArrayList<>();

      List<String> captured = sessions.mutate(sessionToken, user, (rvs, runtimeId, dispatcher) -> {
         VSAssembly assembly = resolve(rvs, assemblyName, collapsed != null, ratio != null);
         requireWritable(assemblyName, assembly, requestedCols, widths, collapsed, expandAll,
                         ratio);

         if(requestedCols != null || widths) {
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

            if(widths) {
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
         }

         if(expandAll != null) {
            applyExpandAll(rvs, assemblyName, assembly, expandAll, linkUri, user, dispatcher);
         }

         List<String> ratioAffects = null;

         if(ratio != null) {
            ratioAffects = applyTitleRatio(runtimeId, assemblyName, assembly, ratio, user,
                                           dispatcher, own);
         }

         Boolean changed = null;
         List<String> alsoCollapsed = null;

         if(collapsed != null) {
            Viewsheet vs = rvs.getViewsheet();
            CurrentSelectionVSAssembly container = (CurrentSelectionVSAssembly) assembly.getContainer();
            Map<String, Boolean> before = childStates(vs, container);
            changed = collapsedState(assembly) != collapsed;

            // Not called when already at the target: applySelection(hide=false) on an expanded
            // child still runs its sibling-collapse loop.
            if(changed) {
               containerService.applySelection(rvs, assemblyName, collapsed, dispatcher,
                                               linkUri == null ? "" : linkUri);
               boolean now = collapsedState(assembly);

               if(now != collapsed) {
                  throw new IllegalStateException(
                     "'" + assemblyName + "' is still " + (now ? "collapsed" : "expanded") +
                     " after the request to " + (collapsed ? "collapse" : "expand") +
                     " it; the Selection Container did not apply it.");
               }

               if(assembly instanceof TimeSliderVSAssembly &&
                  ((TimeSliderVSAssemblyInfo) assembly.getVSAssemblyInfo()).isHidden() != collapsed)
               {
                  own.add("The range slider's 'hidden' flag is " + !collapsed + " although its " +
                          "height says " + (collapsed ? "collapsed" : "expanded") + "; the " +
                          "Viewer toggles by that flag, so a click there may do the opposite of " +
                          "what you expect.");
               }

               alsoCollapsed = new ArrayList<>();

               for(Map.Entry<String, Boolean> e : childStates(vs, container).entrySet()) {
                  if(!e.getKey().equals(assemblyName) && Boolean.TRUE.equals(e.getValue()) &&
                     Boolean.FALSE.equals(before.get(e.getKey())))
                  {
                     alsoCollapsed.add(e.getKey());
                  }
               }
            }
         }

         result.putAll(snapshot(assemblyName, assembly));

         if(changed != null) {
            result.put("changed", changed);
         }

         if(alsoCollapsed != null && !alsoCollapsed.isEmpty()) {
            result.put("alsoCollapsed", alsoCollapsed);
         }

         if(ratioAffects != null) {
            result.put("titleRatioContainer", containerOf(assembly).getAbsoluteName());
            result.put("titleRatioAffects", ratioAffects);
         }
      });

      List<String> warnings = new ArrayList<>(own);

      if(captured != null) {
         warnings.addAll(captured);
      }

      ViewsheetSessionService.relayWarnings(result, warnings);
      return result;
   }

   /** The stored layout of a Selection List/Tree/Range Slider; no checkpoint, no broadcast. */
   public Map<String, Object> getLayout(String sessionToken, Principal user, String assemblyName)
      throws Exception
   {
      requireName(assemblyName);
      return sessions.read(sessionToken, user, (rvs, runtimeId, dispatcher) ->
         snapshot(assemblyName, resolve(rvs, assemblyName, false, true)));
   }

   /**
    * Writes the container's ratio through the Composer service, then re-reads it: that service
    * clamps silently and returns null (no error) for a name that is not a container.
    *
    * @return what else the one container-wide value changes, for the response.
    */
   private List<String> applyTitleRatio(String runtimeId, String assemblyName,
                                        VSAssembly assembly, double ratio, Principal user,
                                        CommandDispatcher dispatcher, List<String> warnings)
      throws Exception
   {
      CurrentSelectionVSAssembly container = containerOf(assembly);
      String containerName = container.getAbsoluteName();
      VSObjectEvent event = new VSObjectEvent();
      event.setName(containerName);
      currentSelectionService.setTitleRatio(runtimeId, ratio, event, user, dispatcher);

      double now = storedRatio(container);

      if(Double.isNaN(now)) {
         throw new IllegalStateException(
            "titleRatio is still unset on the Selection Container '" + containerName +
            "' after the request to set it to " + ratio + "; the write was not applied.");
      }

      if(Math.abs(now - ratio) > 1e-9) {
         warnings.add("titleRatio " + ratio + " was adjusted to " + now + ": the Composer keeps " +
                      "it between 0.05 and (width - 50px) / width of the container's width in " +
                      "the base layout (a container 50px wide or less is forced to 0.05).");
      }

      if(assembly instanceof SelectionTreeVSAssembly || assembly instanceof CalendarVSAssembly) {
         warnings.add("'" + assemblyName + "' is a " +
                      (assembly instanceof CalendarVSAssembly ? "Calendar" : "Selection Tree") +
                      ", which does not show the title / selected-text split. The ratio is " +
                      "stored on its container '" + containerName + "' and changes the other " +
                      "filters in it and the current-selection rows.");
      }

      List<String> affects = new ArrayList<>();
      String[] children = container.getAbsoluteAssemblies();

      for(String child : children == null ? new String[0] : children) {
         if(!child.equals(assemblyName)) {
            affects.add(child);
         }
      }

      affects.add("the current-selection rows of '" + containerName + "'");
      return affects;
   }

   private static CurrentSelectionVSAssembly containerOf(VSAssembly assembly) {
      if(assembly instanceof CurrentSelectionVSAssembly) {
         return (CurrentSelectionVSAssembly) assembly;
      }

      VSAssembly container = assembly.getContainer();
      return container instanceof CurrentSelectionVSAssembly
         ? (CurrentSelectionVSAssembly) container : null;
   }

   /** The container's stored ratio, NaN when unset or unreadable. */
   private static double storedRatio(CurrentSelectionVSAssembly container) {
      VSAssemblyInfo info = container.getVSAssemblyInfo();
      return info instanceof CurrentSelectionVSAssemblyInfo
         ? ((CurrentSelectionVSAssemblyInfo) info).getTitleRatio() : Double.NaN;
   }

   /**
    * {@code titleRatio} and {@code titleRatioSet} for a selection container or a filter inside
    * one; the effective value is 0.5 where none is stored, as the renderers use.
    */
   private static void putRatio(Map<String, Object> out, VSAssembly assembly) {
      CurrentSelectionVSAssembly container = containerOf(assembly);

      if(container == null ||
         !(container.getVSAssemblyInfo() instanceof CurrentSelectionVSAssemblyInfo))
      {
         return;
      }

      double stored = storedRatio(container);
      out.put("titleRatio", Double.isNaN(stored) ? 0.5 : stored);
      out.put("titleRatioSet", !Double.isNaN(stored));
   }

   private void applyExpandAll(RuntimeViewsheet rvs, String assemblyName, VSAssembly assembly,
                               boolean expandAll, String linkUri, Principal user,
                               CommandDispatcher dispatcher)
      throws Exception
   {
      SelectionTreeVSAssemblyInfo current =
         (SelectionTreeVSAssemblyInfo) assembly.getVSAssemblyInfo();

      if(current.isExpandAll() == expandAll) {
         return;
      }

      SelectionTreeVSAssemblyInfo info = (SelectionTreeVSAssemblyInfo) current.clone();
      info.setExpandAll(expandAll);
      propertyService.editObjectProperty(rvs, info, assemblyName, assemblyName,
                                         linkUri == null ? "" : linkUri, user, dispatcher);

      if(((SelectionTreeVSAssemblyInfo) assembly.getVSAssemblyInfo()).isExpandAll() != expandAll) {
         throw new IllegalStateException(
            "'" + assemblyName + "' expandAll is still " + !expandAll + " after the request to " +
            "set it to " + expandAll + "; the write was not applied.");
      }
   }

   /**
    * Raw stored values: {@code -1} is auto. {@code columnCount} and {@code valueCount} are
    * present for a list only; {@code expandAll} for a tree only; {@code inContainer} and
    * {@code collapsed} for a list or range slider ({@code null} when it is not in a Selection
    * Container, where the collapse is not controllable).
    */
   Map<String, Object> snapshot(String assemblyName, VSAssembly assembly) {
      Map<String, Object> out = new LinkedHashMap<>();
      out.put("assembly", assemblyName);

      if(assembly instanceof CurrentSelectionVSAssembly) {
         out.put("type", "selection_container");
         String[] children = ((CurrentSelectionVSAssembly) assembly).getAbsoluteAssemblies();
         out.put("children", children == null ? List.of() : List.of(children));
         putRatio(out, assembly);
         return out;
      }

      if(assembly instanceof CalendarVSAssembly) {
         out.put("type", "calendar");
         putContainerState(out, assembly, false);
         putRatio(out, assembly);
         return out;
      }

      if(assembly instanceof TimeSliderVSAssembly) {
         out.put("type", "range_slider");
         putContainerState(out, assembly);
         putRatio(out, assembly);
         return out;
      }

      SelectionBaseVSAssemblyInfo info = (SelectionBaseVSAssemblyInfo) assembly.getVSAssemblyInfo();

      if(assembly instanceof SelectionListVSAssembly) {
         out.put("type", "selection_list");
         out.put("columnCount", ((SelectionListVSAssemblyInfo) info).getColumnCount());
         out.put("valueCount", valueCount(assembly));
         putContainerState(out, assembly);
         putRatio(out, assembly);
      }
      else {
         out.put("type", "selection_tree");
         out.put("expandAll", ((SelectionTreeVSAssemblyInfo) info).isExpandAll());
         putRatio(out, assembly);
      }

      out.put("measureTextWidth", info.getMeasureSize());
      out.put("measureBarWidth", info.getBarSize());
      out.put("measure", info.getMeasure());
      out.put("showText", info.isShowText());
      out.put("showBar", info.isShowBar());
      return out;
   }

   private void putContainerState(Map<String, Object> out, VSAssembly assembly) {
      putContainerState(out, assembly, true);
   }

   private void putContainerState(Map<String, Object> out, VSAssembly assembly,
                                  boolean withCollapsed)
   {
      VSAssembly container = assembly.getContainer();
      boolean inContainer = container instanceof CurrentSelectionVSAssembly;
      out.put("inContainer", inContainer ? container.getAbsoluteName() : null);

      if(withCollapsed) {
         out.put("collapsed", inContainer ? (Boolean) collapsedState(assembly) : null);
      }
   }

   /**
    * Whether a list or range slider is collapsed, decided exactly as
    * {@link VSSelectionContainerService#applySelection} decides it: a list by its show type, a
    * slider by its height being the title height (not by its {@code hidden} flag).
    */
   private boolean collapsedState(VSAssembly assembly) {
      if(assembly instanceof TimeSliderVSAssembly) {
         TimeSliderVSAssemblyInfo info = (TimeSliderVSAssemblyInfo) assembly.getVSAssemblyInfo();
         Dimension size = objectService.getSize(info);
         return size.height == info.getTitleHeight();
      }

      return ((SelectionListVSAssemblyInfo) assembly.getVSAssemblyInfo()).getShowTypeValue() ==
         SelectionVSAssemblyInfo.DROPDOWN_SHOW_TYPE;
   }

   /** Collapsed state of every list / range slider child of a container, in child order. */
   private Map<String, Boolean> childStates(Viewsheet vs, CurrentSelectionVSAssembly container) {
      Map<String, Boolean> states = new LinkedHashMap<>();
      String[] children = container.getAbsoluteAssemblies();

      for(String name : children == null ? new String[0] : children) {
         VSAssembly child = vs.getAssembly(name);

         if(child instanceof SelectionListVSAssembly || child instanceof TimeSliderVSAssembly) {
            states.put(name, collapsedState(child));
         }
      }

      return states;
   }

   /** Package-private so tests can supply a count without initialising {@link SelectionList}. */
   int valueCount(VSAssembly assembly) {
      SelectionList values = ((SelectionListVSAssembly) assembly).getSelectionList();
      SelectionValue[] array = values == null ? null : values.getSelectionValues();
      return array == null ? 0 : array.length;
   }

   private static VSAssembly resolve(RuntimeViewsheet rvs, String assemblyName,
                                     boolean forCollapse, boolean forRatio)
   {
      Viewsheet vs = rvs.getViewsheet();
      VSAssembly assembly = vs == null ? null : vs.getAssembly(assemblyName);

      if(assembly == null) {
         throw new IllegalArgumentException("Unknown assembly '" + assemblyName + "'.");
      }

      if(forRatio && (assembly instanceof CurrentSelectionVSAssembly ||
                      assembly instanceof CalendarVSAssembly))
      {
         return assembly;
      }

      if(!(assembly instanceof SelectionListVSAssembly) &&
         !(assembly instanceof SelectionTreeVSAssembly) &&
         !(assembly instanceof TimeSliderVSAssembly))
      {
         throw new IllegalArgumentException(
            "'" + assemblyName + "' is a " + assembly.getClass().getSimpleName() +
            ", not a Selection List, Selection Tree or Range Slider." +
            (forCollapse && assembly instanceof CalendarVSAssembly
               ? " A Selection Container collapses only selection lists and range sliders, so a " +
                 "Calendar cannot be collapsed." : ""));
      }

      return assembly;
   }

   private static void requireWritable(String assemblyName, VSAssembly assembly,
                                       Integer requestedCols, boolean widths, Boolean collapsed,
                                       Boolean expandAll, Double titleRatio)
   {
      if(assembly.getVSAssemblyInfo().isEmbedded()) {
         throw new IllegalArgumentException(
            "'" + assemblyName + "' is embedded from another viewsheet and its layout cannot be " +
            "changed here. The change would not be saved; set it in the viewsheet that owns it.");
      }

      if(titleRatio != null) {
         requireRatioTarget(assemblyName, assembly,
                            requestedCols != null || widths || collapsed != null ||
                            expandAll != null);
      }

      if(assembly instanceof TimeSliderVSAssembly &&
         (requestedCols != null || widths || expandAll != null))
      {
         throw new IllegalArgumentException(
            "'" + assemblyName + "' is a Range Slider; only 'collapsed' applies to it. " +
            "'columnCount', 'measureTextWidth', 'measureBarWidth' and 'expandAll' are for " +
            "selection lists and trees.");
      }

      if(requestedCols != null && !(assembly instanceof SelectionListVSAssembly)) {
         throw new IllegalArgumentException(
            "'columnCount' applies to a Selection List only; '" + assemblyName + "' is a " +
            "Selection Tree, which has a single column. The measure widths can be set on it.");
      }

      if(expandAll != null && !(assembly instanceof SelectionTreeVSAssembly)) {
         throw new IllegalArgumentException(
            "'expandAll' applies to a Selection Tree only; '" + assemblyName + "' is not one.");
      }

      if(collapsed != null) {
         if(assembly instanceof SelectionTreeVSAssembly) {
            throw new IllegalArgumentException(
               "'collapsed' does not apply to the Selection Tree '" + assemblyName + "': a " +
               "Selection Container collapses only selection lists and range sliders. A tree's " +
               "nodes are expanded with 'expandAll'.");
         }

         if(!(assembly.getContainer() instanceof CurrentSelectionVSAssembly)) {
            throw new IllegalArgumentException(
               "'collapsed' needs '" + assemblyName + "' to be inside a Selection Container; it " +
               "is not. Collapsing is the container's own layout state. For a standalone " +
               "Selection List use set_assembly_properties showType (dropdown or list).");
         }
      }
   }

   /** Every titleRatio refusal, so that it is raised before mutate() costs an undo step. */
   private static void requireRatioTarget(String assemblyName, VSAssembly assembly,
                                          boolean otherSetters)
   {
      CurrentSelectionVSAssembly container = containerOf(assembly);

      if(container == null) {
         throw new IllegalArgumentException(
            "'titleRatio' is the Selection Container's layout, and '" + assemblyName +
            "' is not inside a Selection Container. Put it in one, or name the container.");
      }

      if(container.getVSAssemblyInfo().isEmbedded()) {
         throw new IllegalArgumentException(
            "The Selection Container '" + container.getAbsoluteName() + "' that holds '" +
            assemblyName + "' is embedded from another viewsheet and its layout cannot be " +
            "changed here. The change would not be saved; set it in the viewsheet that owns it.");
      }

      if(otherSetters &&
         (assembly instanceof CurrentSelectionVSAssembly || assembly instanceof CalendarVSAssembly))
      {
         throw new IllegalArgumentException(
            "'" + assemblyName + "' is a " +
            (assembly instanceof CalendarVSAssembly ? "Calendar" : "Selection Container") +
            "; only 'titleRatio' applies to it. 'columnCount', 'measureTextWidth', " +
            "'measureBarWidth', 'collapsed' and 'expandAll' are for the filters inside it.");
      }
   }

   private static double requireRatio(Number value) {
      double d = value.doubleValue();

      if(Double.isNaN(d) || Double.isInfinite(d)) {
         throw new IllegalArgumentException(
            "'titleRatio' must be a finite fraction strictly between 0 and 1, got " + value + ".");
      }

      if(d > 1) {
         throw new IllegalArgumentException(
            "'titleRatio' is a fraction, not a percentage: " + value + " looks like a percent. " +
            "Give a value strictly between 0 and 1, e.g. 0.3 for a 30% title.");
      }

      if(d <= 0 || d >= 1) {
         throw new IllegalArgumentException(
            "'titleRatio' must be strictly between 0 and 1, got " + value + ".");
      }

      return d;
   }

   private static void requireName(String assemblyName) {
      if(assemblyName == null || assemblyName.isBlank()) {
         throw new IllegalArgumentException(
            "'assembly' is required — name the Selection List, Tree or Range Slider.");
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
   private final VSSelectionContainerService containerService;
   private final VSObjectPropertyService propertyService;
   private final VSObjectService objectService;
   private final ComposerCurrentSelectionService currentSelectionService;
}
