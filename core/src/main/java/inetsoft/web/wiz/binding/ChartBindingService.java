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
package inetsoft.web.wiz.binding;

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.graph.GraphTypes;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import inetsoft.web.binding.controller.ChangeChartRefService;
import inetsoft.web.binding.controller.ChangeChartTypeService;
import inetsoft.web.binding.controller.ChangeSeparateStatusService;
import inetsoft.web.binding.controller.SwapXYBindingService;
import inetsoft.web.binding.event.ChangeChartRefEvent;
import inetsoft.web.binding.event.ChangeChartTypeEvent;
import inetsoft.web.binding.event.ChangeSeparateStatusEvent;
import inetsoft.web.binding.model.ChartBindingModel;
import inetsoft.web.binding.service.VSBindingService;
import inetsoft.web.wiz.binding.model.FieldRef;
import inetsoft.web.wiz.viewsheet.ViewsheetSessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.List;

/**
 * Chart data-binding mutations: shelves, chart type, and axis swap.
 *
 * <p>Each public method is exactly one {@code sessions.mutate}, so it is one undo checkpoint
 * in the user's Composer, driven through the capturing dispatcher and broadcast afterwards.
 *
 * <p>Shelf writes are read-modify-write on the model {@code VSBindingService.createModel}
 * returns. {@code ChangeChartRefEvent} carries the entire {@code ChartBindingModel}, so
 * constructing a fresh one would silently discard the thirteen aesthetic fields in
 * {@link ChartBindingFields#AESTHETIC} along with everything else this class does not set.
 */
@Service
public class ChartBindingService {
   @Autowired
   public ChartBindingService(ViewsheetSessionService sessions,
                              VSBindingService binding,
                              ChangeChartRefService refService,
                              ChangeChartTypeService typeService,
                              SwapXYBindingService swapService,
                              ChangeSeparateStatusService separateStatusService)
   {
      this.sessions = sessions;
      this.binding = binding;
      this.refService = refService;
      this.typeService = typeService;
      this.swapService = swapService;
      this.separateStatusService = separateStatusService;
   }

   public void setShelf(String sessionToken, Principal user, String assemblyName, String shelf,
                        List<FieldRef> fields, String linkUri) throws Exception
   {
      sessions.mutate(sessionToken, user, (rvs, runtimeId, dispatcher) -> {
         ChartVSAssembly chart = requireChart(rvs, assemblyName);
         ChartBindingModel model = (ChartBindingModel) binding.createModel(chart);
         ChartBindingMutator.setShelf(model, shelf, fields);

         ChangeChartRefEvent event = new ChangeChartRefEvent();
         event.setName(assemblyName);
         event.setFieldType(shelf);
         event.setModel(model);
         refService.changeChartRef(runtimeId, event, user, dispatcher, linkUri);
      });
   }

   /**
    * Binds one of the single-field shelves — open/high/low/close, path, source/target,
    * start/end/milestone. A null {@code field} clears it.
    *
    * <p>Goes through the same {@code changeChartRef} event as the list shelves, so the 13
    * snapshotted aesthetic fields are preserved identically.
    */
   public void setSingleShelf(String sessionToken, Principal user, String assemblyName,
                              String shelf, FieldRef field, String linkUri) throws Exception
   {
      sessions.mutate(sessionToken, user, (rvs, runtimeId, dispatcher) -> {
         ChartVSAssembly chart = requireChart(rvs, assemblyName);
         ChartBindingModel model = (ChartBindingModel) binding.createModel(chart);
         ChartBindingMutator.setSingleShelf(model, shelf, field);

         ChangeChartRefEvent event = new ChangeChartRefEvent();
         event.setName(assemblyName);
         event.setFieldType(shelf);
         event.setModel(model);
         refService.changeChartRef(runtimeId, event, user, dispatcher, linkUri);
      });
   }

   public void setChartType(String sessionToken, Principal user, String assemblyName, int type,
                            Boolean multi, Boolean stackMeasures, Boolean separate,
                            String linkUri) throws Exception
   {
      sessions.mutate(sessionToken, user, (rvs, runtimeId, dispatcher) -> {
         requireChart(rvs, assemblyName);

         ChangeChartTypeEvent event = new ChangeChartTypeEvent();
         event.setName(assemblyName);
         event.setType(type);
         event.setMulti(Boolean.TRUE.equals(multi));
         event.setStackMeasures(Boolean.TRUE.equals(stackMeasures));
         event.setSeparate(!Boolean.FALSE.equals(separate));
         typeService.changeChartType(runtimeId, event, user, dispatcher, linkUri);
      });
   }

   /**
    * Toggles separated/merged graphs independently of a chart type change.
    *
    * <p>{@code setChartType}'s own {@code separate} parameter is a silent no-op unless {@code multi}
    * also flips in the same call — {@code ChangeChartTypeService.handleMulti} only reaches
    * {@code ChangeSeparateStatusService} when {@code omulti != nmulti}. This method calls that
    * endpoint directly, the way the Composer's own separate-status toggle does, with {@code multi}
    * read from the chart's current state so the caller does not have to know or restate it.</p>
    */
   public void setSeparateStatus(String sessionToken, Principal user, String assemblyName,
                                 boolean separate, String linkUri) throws Exception
   {
      sessions.mutate(sessionToken, user, (rvs, runtimeId, dispatcher) -> {
         ChartVSAssembly chart = requireChart(rvs, assemblyName);
         VSChartInfo chartInfo = chart.getVSChartInfo();
         boolean multi = chartInfo.isMultiStyles();

         // ChangeSeparateStatusService forces separated=true unconditionally for these types,
         // regardless of what is requested (event.isSeparate() is OR'd with the type checks, so
         // it can only ever push the result toward true, never override it back to false). A
         // caller asking for `separate: false` here would otherwise get `ok: true` and a message
         // describing merged graphs while the chart stayed separated — the same
         // plausible-but-wrong-result shape set_chart_type's own gap had. Refuse up front rather
         // than report a resulting state that contradicts what was asked for; an agent told "no"
         // learns something, one told "yes" when the answer is no builds on a false premise.
         String forcedTypeName = separate ? null : forcedSeparateTypeName(chartInfo.getChartType());

         if(forcedTypeName != null) {
            throw new IllegalArgumentException(
               "'" + assemblyName + "' is a " + forcedTypeName + " chart, which cannot be " +
               "merged — StyleBI always renders it as separated graphs regardless of this " +
               "setting. Call with separate: true, or leave it unset.");
         }

         ChangeSeparateStatusEvent event = new ChangeSeparateStatusEvent(assemblyName, multi, separate);
         separateStatusService.changeSeparateStatus(runtimeId, event, user, dispatcher, linkUri);
      });
   }

   /**
    * Names the chart type if it is one {@link ChangeSeparateStatusService} always forces to
    * separated, regardless of the requested value; {@code null} otherwise. Mirrors that service's
    * own {@code GraphTypes.isTreemap(...) || isMekko(...) || isScatteredContour(...)} check —
    * kept local rather than shared, since it exists only to produce this one error message.
    */
   private static String forcedSeparateTypeName(int chartType) {
      if(GraphTypes.isTreemap(chartType)) {
         if(chartType == GraphTypes.CHART_SUNBURST) {
            return "sunburst";
         }

         if(chartType == GraphTypes.CHART_CIRCLE_PACKING) {
            return "circle-packing";
         }

         if(chartType == GraphTypes.CHART_ICICLE) {
            return "icicle";
         }

         return "treemap";
      }

      if(GraphTypes.isMekko(chartType)) {
         return "mekko";
      }

      if(GraphTypes.isScatteredContour(chartType)) {
         return "scattered-contour";
      }

      return null;
   }

   public void swapAxes(String sessionToken, Principal user, String assemblyName, String linkUri)
      throws Exception
   {
      sessions.mutate(sessionToken, user, (rvs, runtimeId, dispatcher) -> {
         requireChart(rvs, assemblyName);

         ChangeSeparateStatusEvent event = new ChangeSeparateStatusEvent();
         event.setName(assemblyName);
         swapService.swapXYBinding(runtimeId, event, user, dispatcher, linkUri);
      });
   }

   private static ChartVSAssembly requireChart(RuntimeViewsheet rvs, String assemblyName) {
      Viewsheet vs = rvs == null ? null : rvs.getViewsheet();
      VSAssembly assembly = vs == null ? null : vs.getAssembly(assemblyName);

      if(assembly == null) {
         throw new IllegalArgumentException("Unknown assembly '" + assemblyName + "'.");
      }

      if(!(assembly instanceof ChartVSAssembly chart)) {
         throw new IllegalArgumentException(
            "'" + assemblyName + "' is a " + assembly.getClass().getSimpleName() +
            ", not a chart. Chart binding tools only apply to charts.");
      }

      return chart;
   }

   private final ViewsheetSessionService sessions;
   private final VSBindingService binding;
   private final ChangeChartRefService refService;
   private final ChangeChartTypeService typeService;
   private final SwapXYBindingService swapService;
   private final ChangeSeparateStatusService separateStatusService;
}
