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
import inetsoft.web.binding.controller.ChangeChartAestheticService;
import inetsoft.web.binding.event.ChangeChartRefEvent;
import inetsoft.web.binding.model.ChartBindingModel;
import inetsoft.web.binding.service.DataRefModelFactoryService;
import inetsoft.web.binding.service.VSBindingService;
import inetsoft.web.wiz.binding.model.FieldRef;
import inetsoft.web.wiz.viewsheet.ViewsheetSessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Chart aesthetic mutations: channel field bindings and visual frames.
 *
 * <p>Structurally identical to {@link ChartBindingService} — each public method is exactly one
 * {@code sessions.mutate}, so it is one undo checkpoint in the user's Composer, driven through
 * the capturing dispatcher and broadcast afterwards.
 *
 * <p>Writes go through {@code changeChartAesthetic} rather than {@code changeChartRef}: it
 * posts the same {@code ChangeChartRefEvent}, but clears the viewsheet's shared frames when
 * the colour or shape channel changes. Routing an aesthetic write through the data-binding
 * service instead would leave stale shared frames behind.
 */
@Service
public class ChartAestheticAgentService {
   @Autowired
   public ChartAestheticAgentService(ViewsheetSessionService sessions,
                                VSBindingService binding,
                                ChangeChartAestheticService aestheticService,
                                DataRefModelFactoryService refModelService)
   {
      this.sessions = sessions;
      this.binding = binding;
      this.aestheticService = aestheticService;
      this.refModelService = refModelService;
   }

   /**
    * @param sourceTable the table to point the chart at as part of this write, or {@code null} to
    *                    leave its source alone. Binding an aesthetic channel is the one way a chart
    *                    can acquire its first field — a word cloud is nothing but a text channel —
    *                    so it establishes a source on the same terms the shelf writes do.
    */
   public void setField(String sessionToken, Principal user, String assemblyName, String channel,
                        FieldRef field, String sourceTable, String linkUri) throws Exception
   {
      // Validated before the runtime is touched, so a bad channel costs nothing and does not
      // open a checkpoint the caller then has to undo. This still needs the chart itself — node
      // channels are only valid on a relation chart, and size only on chart types that render
      // it — so it costs a resolve, not a mutate.
      boolean relationChart = isRelationChart(sessionToken, user, assemblyName);
      boolean sizeSupported = isSizeSupported(sessionToken, user, assemblyName);
      boolean colorShapeSupported = isColorShapeSupported(sessionToken, user, assemblyName);
      String name = AestheticChannels.requireFieldChannel(
         channel, relationChart, sizeSupported, colorShapeSupported);
      requireNotMultiAesthetic(sessionToken, user, assemblyName);

      sessions.mutate(sessionToken, user, (rvs, runtimeId, dispatcher) -> {
         ChartVSAssembly chart = requireChart(rvs, assemblyName);
         ChartBindingModel model = (ChartBindingModel) binding.createModel(chart);
         ChartBindingService.applySource(model, sourceTable);
         ChartAestheticMutator.setField(
            model, name, field, relationChart, rvs, chart.getSourceInfo(), refModelService,
            perMeasureFrameChannels(chart));

         ChangeChartRefEvent event = new ChangeChartRefEvent();
         event.setName(assemblyName);
         event.setFieldType(name);
         event.setModel(model);
         aestheticService.changeChartAesthetic(runtimeId, event, user, dispatcher, linkUri);
      });
   }

   public void clearField(String sessionToken, Principal user, String assemblyName,
                          String channel, String linkUri) throws Exception
   {
      boolean relationChart = isRelationChart(sessionToken, user, assemblyName);
      boolean sizeSupported = isSizeSupported(sessionToken, user, assemblyName);
      boolean colorShapeSupported = isColorShapeSupported(sessionToken, user, assemblyName);
      String name = AestheticChannels.requireFieldChannel(
         channel, relationChart, sizeSupported, colorShapeSupported);

      apply(sessionToken, user, assemblyName, name, linkUri,
            (chart, model) -> ChartAestheticMutator.clearField(model, name, relationChart));
   }

   public void setFrame(String sessionToken, Principal user, String assemblyName, String channel,
                        Map<String, Object> frame, String linkUri) throws Exception
   {
      setFrame(sessionToken, user, assemblyName, channel, frame, null, linkUri);
   }

   public void setFrame(String sessionToken, Principal user, String assemblyName, String channel,
                        Map<String, Object> frame, String measure, String linkUri) throws Exception
   {
      boolean relationChart = isRelationChart(sessionToken, user, assemblyName);
      String name = AestheticChannels.requireFrameChannel(channel, relationChart);

      apply(sessionToken, user, assemblyName, name, linkUri,
            (chart, model) -> ChartAestheticMutator.setFrame(
               model, name, frame, relationChart, perMeasureFrameChannels(chart), measure));
   }

   /** The aesthetic panes' "Reset to Default" button. */
   public void resetFrame(String sessionToken, Principal user, String assemblyName, String channel,
                          String measure, String linkUri) throws Exception
   {
      boolean relationChart = isRelationChart(sessionToken, user, assemblyName);
      String name = AestheticChannels.requireFrameChannel(channel, relationChart);

      apply(sessionToken, user, assemblyName, name, linkUri,
            (chart, model) -> ChartAestheticMutator.resetFrame(
               model, name, relationChart, perMeasureFrameChannels(chart), measure));
   }

   /** Reads the channels without opening a checkpoint. */
   public Map<String, Object> read(String sessionToken, Principal user, String assemblyName)
      throws Exception
   {
      RuntimeViewsheet rvs = sessions.resolve(sessionToken, user);
      ChartVSAssembly chart = requireChart(rvs, assemblyName);
      return ChartAestheticMutator.describe((ChartBindingModel) binding.createModel(chart),
                                            isRelationChart(chart),
                                            perMeasureFrameChannels(chart),
                                            isSizeSupported(chart), isColorShapeSupported(chart));
   }

   /**
    * The frame channels this chart renders from each measure's own frame property rather than
    * from the chart-level one.
    *
    * <p>Asked of the real {@code VSChartInfo} rather than derived from the chart type, because the
    * answer is not a function of the type alone: {@code RadarVSChartInfo.supportsShapeFieldFrame()}
    * consults the plot descriptor (a point radar answers true where a line radar answers false).
    * The three predicates are exactly the ones {@code VSFrameVisitor}'s strategies call, mapped to
    * channels the way those strategies map them — {@code VSLineFrameStrategy} and
    * {@code VSTextureFrameStrategy} both defer to {@code supportsShapeFieldFrame()}.
    *
    * <p>A chart with no binding yet has no info and no measures either, so the empty set is right
    * for it: nothing is rendered per measure when there are no measures.
    *
    * <p>Note that answering true is not the same as "the measures are on X or Y" — a Gantt chart
    * answers true for all three and keeps its measures on start/milestone. Which refs the frame is
    * then written to is {@code ChartAestheticMutator.aggregates}' job, and it mirrors
    * {@code VSFrameVisitor.getAggregates()} for exactly that reason.
    *
    * <p>Package-private rather than private so {@code ChartAestheticAgentServiceTest} can pin the
    * mapping directly. It is the linchpin of the write/read agreement in
    * {@code ChartAestheticMutator}, and its whole reason for asking the info rather than deriving
    * from the type is a case (point radar vs. line radar) that no caller can reach by accident.
    */
   static Collection<String> perMeasureFrameChannels(ChartVSAssembly chart) {
      VSChartInfo info = chart == null ? null : chart.getVSChartInfo();

      if(info == null) {
         return Set.of();
      }

      Set<String> channels = new LinkedHashSet<>();

      if(info.supportsColorFieldFrame()) {
         channels.add("color");
      }

      if(info.supportsShapeFieldFrame()) {
         channels.add("shape");
         channels.add("line");
         channels.add("texture");
      }

      if(info.supportsSizeFieldFrame()) {
         channels.add("size");
      }

      return channels;
   }

   /**
    * The channels and frame types available, so an agent can discover rather than guess.
    *
    * <p>{@link AestheticChannels#NODE_CHANNELS} are deliberately not in {@code fieldChannels}/
    * {@code frameChannels} here: this endpoint has no assembly, so no chart to check the type
    * of, and listing them unconditionally would advertise a channel most charts cannot render.
    * {@code get_chart_aesthetics} reports them per-chart, once there is one to check.
    *
    * <p>{@code frameTypes} used to be a hard-coded {@code ["static", "categorical", "gradient",
    * "palette"]}. That was wrong twice over: it named eleven fewer types than
    * {@link VisualFrameAliases#create} builds, and it implied the answer does not depend on the
    * channel when {@code gradient} exists only for colour and {@code linear} only for size and
    * line. A caller following it would be refused for asking for something supported and
    * accepted-then-refused for asking for something that is not — from the one endpoint whose
    * job is to stop them guessing. It is derived from
    * {@link VisualFrameAliases#typeNames(String)} now, so the two cannot drift again.
    * {@code frameTypes} is kept as the union across channels for callers that read it.
    */
   public Map<String, Object> options() {
      Map<String, Object> typesByChannel = new LinkedHashMap<>();
      Set<String> union = new TreeSet<>();

      for(String channel : AestheticChannels.SUPPORTED_FRAME_CHANNELS) {
         List<String> types = VisualFrameAliases.typeNames(channel);
         typesByChannel.put(channel, types);
         union.addAll(types);
      }

      for(String channel : AestheticChannels.NODE_CHANNELS) {
         typesByChannel.put(channel, VisualFrameAliases.typeNames(channel));
      }

      Map<String, Object> out = new LinkedHashMap<>();
      out.put("fieldChannels", AestheticChannels.FIELD_CHANNELS);
      out.put("frameChannels", AestheticChannels.SUPPORTED_FRAME_CHANNELS);
      out.put("frameTypes", List.copyOf(union));
      out.put("frameTypesByChannel", typesByChannel);
      out.put("frameTypesNote",
              "frameTypes is the union across channels; frameTypesByChannel is what each " +
              "channel actually accepts, and is what set_visual_frame validates against.");
      out.put("palettes", List.copyOf(VisualFrameAliases.PALETTES.keySet()));
      out.put("nodeChannels", AestheticChannels.NODE_CHANNELS);
      out.put("nodeChannelsNote",
              "node-color and node-size apply only to relation charts (network, tree, chord) " +
              "— call get_chart_aesthetics on the chart to see whether they apply here.");
      return out;
   }

   private boolean isRelationChart(String sessionToken, Principal user, String assemblyName)
      throws Exception
   {
      RuntimeViewsheet rvs = sessions.resolve(sessionToken, user);
      return isRelationChart(requireChart(rvs, assemblyName));
   }

   /**
    * A bare mock in a test that never stubs {@code getVSChartInfo()} returns null for it, same
    * as a chart assembly with no binding yet — neither is a relation chart.
    */
   private static boolean isRelationChart(ChartVSAssembly chart) {
      VSChartInfo info = chart.getVSChartInfo();
      return info != null && GraphTypes.isRelation(info.getChartType());
   }

   private boolean isSizeSupported(String sessionToken, Principal user, String assemblyName)
      throws Exception
   {
      RuntimeViewsheet rvs = sessions.resolve(sessionToken, user);
      return isSizeSupported(requireChart(rvs, assemblyName));
   }

   /** No chart type yet (unbound chart) means no type is forbidding the size channel. */
   private static boolean isSizeSupported(ChartVSAssembly chart) {
      VSChartInfo info = chart.getVSChartInfo();
      return info == null || GraphTypes.supportsSize(info.getChartType());
   }

   private boolean isColorShapeSupported(String sessionToken, Principal user, String assemblyName)
      throws Exception
   {
      RuntimeViewsheet rvs = sessions.resolve(sessionToken, user);
      return isColorShapeSupported(requireChart(rvs, assemblyName));
   }

   /** No chart type yet (unbound chart) means no type is forbidding color/shape. */
   private static boolean isColorShapeSupported(ChartVSAssembly chart) {
      VSChartInfo info = chart.getVSChartInfo();
      return info == null || !GraphTypes.isContour(info.getChartType());
   }

   /**
    * Refuses a field write while the chart is already multi-style, instead of corrupting it.
    *
    * <p>{@code ChartAestheticMutator.setField} writes to the chart-level field slot
    * unconditionally — it has no equivalent of the per-measure {@code aggr.setShapeField(...)}
    * path {@code VSFrameVisitor}'s rendering strategies read from once
    * {@code info.isMultiAesthetic()} is true ({@code frameField}'s own comment on this class
    * documents that split). Writing there while already multi-style does not merely go
    * unrendered the way an unsupported channel does — it leaves the chart's runtime graph
    * unable to render *at all*, and unlike every other guard in this class, undoing the write
    * (or the {@code set_chart_type(multi:true)} that preceded it) does not recover it: the
    * corruption is in cached render state {@code undo} does not roll back, confirmed live
    * 2026-09-01 by reverting both edits and finding {@code get_viewsheet_image} still failing.
    *
    * <p>The safe order — bind the field, then turn multi-style on — already works: {@code
    * set_chart_type}'s own {@code multi} redistributes an existing chart-level field into each
    * measure correctly, the same transition a human toggling the Composer's own checkbox
    * triggers. Only writing a field while multi-style is already on has no such handling, so
    * this refuses exactly that state rather than attempting a fix at the render layer whose
    * root cause remains open (see the L3 parity report's own note on G2-5).
    */
   private void requireNotMultiAesthetic(String sessionToken, Principal user, String assemblyName)
      throws Exception
   {
      RuntimeViewsheet rvs = sessions.resolve(sessionToken, user);
      ChartVSAssembly chart = requireChart(rvs, assemblyName);
      VSChartInfo info = chart.getVSChartInfo();

      if(info != null && info.isMultiAesthetic()) {
         throw new IllegalArgumentException(
            "This chart is multi-style, and binding a field here while multi-style is already " +
            "on corrupts the chart's rendering with no way to undo it. Turn multi-style off " +
            "(set_chart_type with multi:false), bind the field, then turn multi-style back on " +
            "— that order redistributes the field into each measure correctly.");
      }
   }

   private void apply(String sessionToken, Principal user, String assemblyName, String channel,
                      String linkUri,
                      java.util.function.BiConsumer<ChartVSAssembly, ChartBindingModel> mutation)
      throws Exception
   {
      sessions.mutate(sessionToken, user, (rvs, runtimeId, dispatcher) -> {
         ChartVSAssembly chart = requireChart(rvs, assemblyName);
         ChartBindingModel model = (ChartBindingModel) binding.createModel(chart);
         mutation.accept(chart, model);

         ChangeChartRefEvent event = new ChangeChartRefEvent();
         event.setName(assemblyName);
         event.setFieldType(channel);
         event.setModel(model);
         aestheticService.changeChartAesthetic(runtimeId, event, user, dispatcher, linkUri);
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
            ", not a chart. Chart aesthetic tools only apply to charts.");
      }

      return chart;
   }

   private final ViewsheetSessionService sessions;
   private final VSBindingService binding;
   private final ChangeChartAestheticService aestheticService;
   private final DataRefModelFactoryService refModelService;
}
