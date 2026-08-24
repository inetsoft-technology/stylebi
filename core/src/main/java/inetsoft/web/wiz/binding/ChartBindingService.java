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
import inetsoft.uql.viewsheet.graph.ChartAggregateRef;
import inetsoft.uql.viewsheet.graph.ChartDescriptor;
import inetsoft.uql.viewsheet.graph.ChartRef;
import inetsoft.uql.viewsheet.graph.GraphTypes;
import inetsoft.uql.viewsheet.graph.PlotDescriptor;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import inetsoft.web.binding.controller.ChangeChartRefService;
import inetsoft.web.binding.controller.ChangeChartTypeService;
import inetsoft.web.binding.controller.ChangeSeparateStatusService;
import inetsoft.web.binding.controller.SwapXYBindingService;
import inetsoft.web.binding.event.ChangeChartRefEvent;
import inetsoft.web.binding.event.ChangeChartTypeEvent;
import inetsoft.web.binding.event.ChangeSeparateStatusEvent;
import inetsoft.web.binding.model.ChartBindingModel;
import inetsoft.web.binding.model.graph.ChartRefModel;
import inetsoft.web.binding.service.DataRefModelFactoryService;
import inetsoft.web.binding.service.VSBindingService;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.wiz.binding.model.ChartTypeState;
import inetsoft.web.wiz.binding.model.FieldRef;
import inetsoft.web.wiz.viewsheet.ViewsheetSessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.ArrayList;
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
                              ChangeSeparateStatusService separateStatusService,
                              DataRefModelFactoryService refModelService)
   {
      this.sessions = sessions;
      this.binding = binding;
      this.refService = refService;
      this.typeService = typeService;
      this.swapService = swapService;
      this.separateStatusService = separateStatusService;
      this.refModelService = refModelService;
   }

   /**
    * @param sourceTable the table to point the chart at as part of this write, or {@code null} to
    *                    leave its source alone. A chart with no source renders nothing however
    *                    correctly its shelves are filled in, and the Composer avoids that by taking
    *                    the source from the drag; this is the same thing, one call rather than two.
    */
   public void setShelf(String sessionToken, Principal user, String assemblyName, String shelf,
                        List<FieldRef> fields, String sourceTable, String linkUri) throws Exception
   {
      sessions.mutate(sessionToken, user, (rvs, runtimeId, dispatcher) -> {
         ChartVSAssembly chart = requireChart(rvs, assemblyName);
         ChartBindingModel model = (ChartBindingModel) binding.createModel(chart);
         applySource(model, sourceTable);
         ChartBindingMutator.setShelf(
            model, shelf, fields, rvs, chart.getSourceInfo(), refModelService);

         ChangeChartRefEvent event = new ChangeChartRefEvent();
         event.setName(assemblyName);
         event.setFieldType(shelf);
         event.setModel(model);
         refService.changeChartRef(runtimeId, event, user, dispatcher, linkUri);
      });
   }

   /**
    * Establishes the source, if one was worked out and the chart has none.
    *
    * <p>Guarded on the chart already being sourceless rather than trusting the caller: a repoint
    * deletes bound fields, so it stays behind {@code set_chart_source}'s explicit {@code force} and
    * can never happen as a side effect of binding a field.
    */
   static void applySource(ChartBindingModel model, String sourceTable) {
      if(sourceTable != null && model != null && model.getSource() == null) {
         model.setSource(BindingSources.assetSource(sourceTable));
      }
   }

   /**
    * Points a chart at a source table.
    *
    * <p>A chart added in the Composer starts with no source, and nothing on the agent path could
    * assign one. Its shelves can be populated — {@code set_chart_shelf} reports success and
    * {@code get_binding} reads the fields straight back — and it renders nothing at all, because
    * shelves with no source have nothing to query. The Composer assigns one as a side effect of the
    * drag ({@code VSChartDndService} takes it from the drag event's table); the agent's
    * {@code FieldRef} carries no table, so the request has nothing to derive it from.
    * {@code VSBindingService.updateSourceInfo} only ever <em>propagates</em> a source that is
    * already there — it is a no-op when the model's source is null — which is preservation, not the
    * ability to set one.
    *
    * <p>Repointing a bound chart invalidates its fields, since the columns belong to the old
    * source; the Composer handles that by <em>deleting</em> the ones the new source does not have
    * ({@code VSAssemblyInfoHandler.validateChartColumns}). So it is refused unless {@code force},
    * rather than done silently on one call.
    */
   public void setSource(String sessionToken, Principal user, String assemblyName, String table,
                         boolean force, String linkUri) throws Exception
   {
      if(table == null || table.isBlank()) {
         throw new IllegalArgumentException(
            "set_chart_source requires 'table' — the source table's name. " +
            "list_bindable_fields reports what this chart can bind to.");
      }

      sessions.mutate(sessionToken, user, (rvs, runtimeId, dispatcher) -> {
         ChartVSAssembly chart = requireChart(rvs, assemblyName);
         ChartBindingModel model = (ChartBindingModel) binding.createModel(chart);
         String resolved = BindingSources.resolve(model, table, assemblyName);

         if(!force && !BindingSources.alreadyPointedAt(model.getSource(), resolved)) {
            requireNoBoundFields(model, assemblyName, resolved);
         }

         model.setSource(BindingSources.assetSource(resolved));

         ChangeChartRefEvent event = new ChangeChartRefEvent();
         event.setName(assemblyName);
         // No fieldType: this changes no shelf, and changeChartRef never reads it.
         event.setModel(model);
         refService.changeChartRef(runtimeId, event, user, dispatcher, linkUri);
      });
   }

   /**
    * Refuses to discard bound fields.
    *
    * <p>Counts <b>every</b> place a chart keeps a field, not just x/y/group: the three list
    * shelves, the ten single-field shelves, the six aesthetic channels, and a map's geo fields.
    * Each of those is a binding {@code VSAssemblyInfoHandler.validateChartColumns} deletes when a
    * chart is repointed — it clears the shelves, then {@code validateAestheticFields} clears
    * colour/shape/size/text and a relation chart's node channels, and {@code VSMapInfo}'s geo
    * fields alongside them. A check that read fewer of them would report "nothing bound" for a
    * chart that is fully bound and repoint without asking:
    *
    * <ul>
    *   <li>A candlestick's entire binding lives on the single-field shelves, x and y empty.</li>
    *   <li>A word cloud is nothing but a text channel — every shelf empty.</li>
    *   <li>A map keeps its geography on {@code geoFields}, which is no shelf at all.</li>
    * </ul>
    *
    * <p>Those are the chart types whose binding is hardest to rebuild, so they are the worst ones
    * to discard silently.
    */
   private static void requireNoBoundFields(ChartBindingModel model, String assemblyName,
                                            String table)
   {
      List<String> populated = new ArrayList<>();

      for(String shelf : ChartBindingMutator.SHELVES) {
         int count = ChartBindingMutator.readShelf(model, shelf).size();

         if(count > 0) {
            populated.add(count + " on " + shelf);
         }
      }

      for(String shelf : ChartBindingMutator.SINGLE_SHELVES) {
         if(ChartBindingMutator.readSingleShelf(model, shelf) != null) {
            populated.add(shelf);
         }
      }

      // Named as channels rather than shelves because that is the vocabulary the caller binds
      // them with: set_aesthetic_field takes a channel, so a refusal reporting "color" tells it
      // which call to undo.
      for(String channel : ChartAestheticMutator.boundFieldChannels(model)) {
         populated.add(channel + " channel");
      }

      List<ChartRefModel> geoFields = model.getGeoFields();

      if(geoFields != null && !geoFields.isEmpty()) {
         populated.add(geoFields.size() + " on geo");
      }

      if(!populated.isEmpty()) {
         throw new IllegalArgumentException(
            "'" + assemblyName + "' already has fields bound (" + String.join(", ", populated) +
            ") and they belong to its current source, so repointing it to '" + table +
            "' discards them. Pass force: true to do that deliberately, or bind the fields you " +
            "want after the source is set.");
      }
   }

   /**
    * Binds one of the single-field shelves — open/high/low/close, path, source/target,
    * start/end/milestone. A null {@code field} clears it.
    *
    * <p>Goes through the same {@code changeChartRef} event as the list shelves, so the 13
    * snapshotted aesthetic fields are preserved identically.
    */
   public void setSingleShelf(String sessionToken, Principal user, String assemblyName,
                              String shelf, FieldRef field, String sourceTable, String linkUri)
      throws Exception
   {
      sessions.mutate(sessionToken, user, (rvs, runtimeId, dispatcher) -> {
         ChartVSAssembly chart = requireChart(rvs, assemblyName);
         ChartBindingModel model = (ChartBindingModel) binding.createModel(chart);
         applySource(model, sourceTable);
         ChartBindingMutator.setSingleShelf(
            model, shelf, field, rvs, chart.getSourceInfo(), refModelService);

         ChangeChartRefEvent event = new ChangeChartRefEvent();
         event.setName(assemblyName);
         event.setFieldType(shelf);
         event.setModel(model);
         refService.changeChartRef(runtimeId, event, user, dispatcher, linkUri);
      });
   }

   public void setChartType(String sessionToken, Principal user, String assemblyName, int type,
                            Boolean multi, Boolean stackMeasures, Boolean separate, String field,
                            String linkUri) throws Exception
   {
      sessions.mutate(sessionToken, user, (rvs, runtimeId, dispatcher) -> {
         ChartVSAssembly chart = requireChart(rvs, assemblyName);
         String ref = requireTypeableField(chart, assemblyName, field);

         ChangeChartTypeEvent event = new ChangeChartTypeEvent();
         event.setName(assemblyName);
         event.setType(type);
         event.setRef(ref);
         // An omitted flag means "leave it alone", and the event has no way to say that: it carries
         // three primitive booleans, and the obvious coercions -- TRUE.equals(null) for multi and
         // stackMeasures, !FALSE.equals(null) for separate -- turn "unstated" into a concrete value
         // the shared service then applies. Confirmed live on stackMeasures: a chart with it on,
         // retyped once without mentioning it, read back with it off. So send what the chart
         // already has, which leaves the shared service comparing a value to itself.
         //
         // multi has the same hole and escapes it only by accident, since its coerced value travels
         // through the WebSocket path that drops it silently -- repairing that path would otherwise
         // turn every plain retype into a multi-style teardown.
         event.setMulti(multi == null ? isMultiStyles(chart) : multi);
         event.setStackMeasures(
            stackMeasures == null ? isStackMeasures(chart) : stackMeasures);
         event.setSeparate(separate == null ? isSeparatedGraph(chart) : separate);
         typeService.changeChartType(runtimeId, event, user, dispatcher, linkUri);

         applyMultiStyle(rvs, runtimeId, assemblyName, multi, separate, user, dispatcher, linkUri);
      });
   }

   /**
    * Applies the requested multi-style state, which the chart-type change itself cannot.
    *
    * <p>{@code ChangeChartTypeService.handleMulti} is the only code that turns multiStyles on, and
    * it routes through {@code ChangeSeparateStatusController} — a
    * {@code @MessageMapping("/vs/chart/changeSeparateStatus")} handler that takes its runtime id
    * from {@code runtimeViewsheetRef}, described in its own constructor as the runtime "associated
    * with the WebSocket session", and returns silently when that id is null. An agent call is plain
    * HTTP with a pairing token and has no such session, so {@code multi} was accepted, reported ok,
    * and dropped — on every call, including the one case the shared path would otherwise have
    * handled. It is also reached only when the type is unchanged, so asking for a new type and
    * multi-style together lost the multi half twice over.
    *
    * <p>Repaired here rather than there. The shared path has no test coverage, and the defect is an
    * absent WebSocket context rather than wrong logic, so no test at that layer would have caught
    * it; meanwhile {@code setSeparateStatus} in this same class already makes exactly this call
    * correctly, with the runtime id this method holds. Fixing the shared method is still worth
    * doing for whatever calls it next — it is a trap for any non-WebSocket caller — but that is a
    * change to Composer code with its own risk, not part of closing this gap for the agent.
    *
    * <p>The chart's info is re-read after the type change rather than captured before it, because
    * {@code ChangeChartTypeService} replaces {@code VSChartInfo} outright for some transitions.
    */
   private void applyMultiStyle(RuntimeViewsheet rvs, String runtimeId, String assemblyName,
                                Boolean multi, Boolean separate, Principal user,
                                CommandDispatcher dispatcher, String linkUri) throws Exception
   {
      // An omitted multi is "not asked about", not "off" — the shared event builder coerces it to
      // false, which is why a plain retype cannot leave the flag alone.
      if(multi == null) {
         return;
      }

      VSChartInfo info = requireChart(rvs, assemblyName).getVSChartInfo();

      // Nothing to change, nothing to call. This is a second write with its own undo step, so
      // firing it on a state that already matches would put a no-op into the Composer's history
      // for every retype.
      if(info == null || info.isMultiStyles() == multi) {
         return;
      }

      // Separated travels with multi through this service — it sets both — so an unstated separate
      // preserves what the chart has rather than pushing it either way.
      boolean separated = separate == null ? info.isSeparatedGraph() : separate;
      String forcedTypeName = separated ? null : forcedSeparateTypeName(info.getChartType());

      if(forcedTypeName != null) {
         throw new IllegalArgumentException(
            "'" + assemblyName + "' is a " + forcedTypeName + " chart, which cannot be merged — " +
            "StyleBI always renders it as separated graphs regardless of this setting. Call with " +
            "separate: true, or leave it unset.");
      }

      ChangeSeparateStatusEvent event =
         new ChangeSeparateStatusEvent(assemblyName, multi, separated);
      separateStatusService.changeSeparateStatus(runtimeId, event, user, dispatcher, linkUri);
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

   /**
    * Reads a chart's type and the flags that decide what it means, without opening a checkpoint.
    *
    * <p>{@code sessions.resolve} rather than {@code sessions.mutate}: a read that went through the
    * mutating path would drop a no-op step into the user's Composer undo history for every look an
    * agent takes.
    */
   public ChartTypeState readChartType(String sessionToken, Principal user, String assemblyName)
      throws Exception
   {
      RuntimeViewsheet rvs = sessions.resolve(sessionToken, user);
      ChartVSAssembly chart = requireChart(rvs, assemblyName);
      ChartBindingModel model = (ChartBindingModel) binding.createModel(chart);

      return new ChartTypeState(assemblyName, model.getChartType(), model.getRTChartType(),
                                model.isMultiStyles(), model.isSeparated(),
                                model.isStackMeasures());
   }

   /**
    * The named measure, checked against the only two shelves a per-measure type can live on, or
    * null when the caller asked about the whole chart.
    *
    * <p>Both refusals exist because the alternative is a write that reports ok and changes nothing.
    * {@code ChangeChartTypeProcessor} looks for the ref by scanning {@code getXFieldCount()} and
    * {@code getYFieldCount()}, and when it matches neither it sets no type at all — it does not fall
    * back to the assembly, since it is inside the {@code ref != null} branch. So a misspelled
    * column, or one that sits on {@code group} or a single-field shelf, is silently dropped. And a
    * per-measure type on a chart that is not multi-style would be stored where nothing renders from
    * it, which is the inert-write shape this lane already found on the visual frames.
    */
   private static String requireTypeableField(ChartVSAssembly chart, String assemblyName,
                                              String field)
   {
      if(field == null || field.isBlank()) {
         return null;
      }

      String name = field.trim();
      VSChartInfo info = chart == null ? null : chart.getVSChartInfo();

      if(info == null || !info.isMultiStyles()) {
         throw new IllegalArgumentException(
            "'" + assemblyName + "' is not a multi-style chart, so a per-measure type would be " +
            "stored and never rendered. Call set_chart_type with multi: true first, then type the " +
            "field.");
      }

      List<String> typeable = new ArrayList<>();

      for(int i = 0; i < info.getXFieldCount(); i++) {
         collectTypeable(info.getXField(i), typeable);
      }

      for(int i = 0; i < info.getYFieldCount(); i++) {
         collectTypeable(info.getYField(i), typeable);
      }

      if(typeable.contains(name)) {
         return name;
      }

      throw new IllegalArgumentException(
         "'" + assemblyName + "' has no measure '" + name + "' on x or y, which are the only " +
         "shelves a per-measure type applies to. " +
         (typeable.isEmpty()
             ? "It has no measures there at all — bind one first."
             : "Measures that can be typed: " + String.join(", ", typeable) + ".") +
         " get_binding reports what is bound where.");
   }

   private static void collectTypeable(ChartRef ref, List<String> names) {
      if(ref instanceof ChartAggregateRef aggregate && aggregate.getFullName() != null) {
         names.add(aggregate.getFullName());
      }
   }

   private static boolean isMultiStyles(ChartVSAssembly chart) {
      VSChartInfo info = chart == null ? null : chart.getVSChartInfo();
      return info != null && info.isMultiStyles();
   }

   /** Defaults to separated, which is what the event's own null coercion did. */
   private static boolean isSeparatedGraph(ChartVSAssembly chart) {
      VSChartInfo info = chart == null ? null : chart.getVSChartInfo();
      return info == null || info.isSeparatedGraph();
   }

   /** Lives on the plot descriptor rather than the chart info, unlike the other two flags. */
   private static boolean isStackMeasures(ChartVSAssembly chart) {
      ChartDescriptor descriptor = chart == null ? null : chart.getChartDescriptor();
      PlotDescriptor plot = descriptor == null ? null : descriptor.getPlotDescriptor();
      return plot != null && plot.isStackMeasures();
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
   private final DataRefModelFactoryService refModelService;
}
