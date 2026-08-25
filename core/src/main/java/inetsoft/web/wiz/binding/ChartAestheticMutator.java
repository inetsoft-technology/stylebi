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
import inetsoft.uql.asset.SourceInfo;
import inetsoft.uql.viewsheet.graph.GraphTypes;
import inetsoft.web.binding.model.BindingRefModel;
import inetsoft.web.binding.model.ChartBindingModel;
import inetsoft.web.binding.model.graph.AestheticInfo;
import inetsoft.web.binding.model.graph.ChartAggregateRefModel;
import inetsoft.web.binding.model.graph.ChartRefModel;
import inetsoft.web.binding.model.graph.aesthetic.*;
import inetsoft.web.binding.service.DataRefModelFactoryService;
import inetsoft.web.wiz.binding.model.FieldRef;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-modify-write over the thirteen aesthetic fields in {@link ChartBindingFields#AESTHETIC}.
 *
 * <p>The mirror image of {@link ChartBindingMutator}. {@code changeChartAesthetic} posts the
 * same {@code ChangeChartRefEvent} carrying the entire {@code ChartBindingModel}, so the
 * preservation obligation runs both ways: 2b must not disturb these fields, and this class
 * must not disturb the shelves, chart type or options. Callers must pass the model
 * {@code VSBindingService.createModel} returned and mutate it in place — a fresh model would
 * silently drop everything neither class sets.
 */
public final class ChartAestheticMutator {
   private ChartAestheticMutator() {
   }

   /** Binds a field to a channel. Replaces whatever that channel held; leaves the rest alone. */
   public static void setField(ChartBindingModel model, String channel, FieldRef field) {
      setField(model, channel, field, false);
   }

   /** @param relationChart see {@link AestheticChannels#requireFieldChannel(String, boolean)}. */
   public static void setField(ChartBindingModel model, String channel, FieldRef field,
                               boolean relationChart)
   {
      try {
         setField(model, channel, field, relationChart, null, null, null);
      }
      catch(RuntimeException e) {
         throw e; // preserve e.g. requireFieldChannel's IllegalArgumentException as-is
      }
      catch(Exception e) {
         throw new RuntimeException(e);
      }
   }

   /**
    * @param rvs            the runtime viewsheet, so a field's {@code namedGroup} can be
    *                       resolved against a worksheet-local named group.
    * @param source         the chart's own {@code SourceInfo}.
    * @param refModelService needed to resolve a worksheet-local named group's conditions.
    */
   public static void setField(ChartBindingModel model, String channel, FieldRef field,
                               boolean relationChart, RuntimeViewsheet rvs, SourceInfo source,
                               DataRefModelFactoryService refModelService)
      throws Exception
   {
      setField(model, channel, field, relationChart, rvs, source, refModelService,
               AestheticChannels.FRAME_CHANNELS);
   }

   /**
    * @param perMeasureFrameChannels see
    *        {@link #setFrame(ChartBindingModel, String, Map, boolean, Collection)}. Needed here
    *        because the carry-forward below has to read the frame from whichever slot
    *        {@code setFrame} would have written it to.
    */
   public static void setField(ChartBindingModel model, String channel, FieldRef field,
                               boolean relationChart, RuntimeViewsheet rvs, SourceInfo source,
                               DataRefModelFactoryService refModelService,
                               Collection<String> perMeasureFrameChannels)
      throws Exception
   {
      String name = AestheticChannels.requireFieldChannel(channel, relationChart);

      if(field == null) {
         throw new IllegalArgumentException(
            "Binding the " + name + " channel needs a field. To unbind it, use " +
            "clear_aesthetic_field instead — an absent field is not the same request as an " +
            "explicit clear.");
      }

      // A channel's frame can already exist before any field is bound to it -- either on the
      // channel's own AestheticInfo (rebinding a field that already had one) or on the top-level
      // ChartBindingModel.xxxFrame property setFrame() writes to when the channel is unbound (see
      // setFrame() above). Without carrying it forward here, the new AestheticInfo's frame stays
      // null and GraphUtil.fixVisualFrame fabricates a fresh default (useGlobal=true) at render
      // time, silently shadowing whatever the caller had already set.
      AestheticInfo existing = read(model, name);
      AestheticInfo info = existing != null ? existing : new AestheticInfo();
      VisualFrameModel carriedFrame = info.getFrame() != null
         ? info.getFrame() : frameOf(model, name, perMeasureFrameChannels);
      info.setFullName(field.column());
      info.setDataInfo(FieldRefFactory.toChartRef(field, rvs, source, refModelService));
      info.setFrame(carriedFrame);
      assign(model, name, info);
   }

   /** Unbinds a channel. */
   public static void clearField(ChartBindingModel model, String channel) {
      clearField(model, channel, false);
   }

   /** @param relationChart see {@link AestheticChannels#requireFieldChannel(String, boolean)}. */
   public static void clearField(ChartBindingModel model, String channel, boolean relationChart) {
      assign(model, AestheticChannels.requireFieldChannel(channel, relationChart), null);
   }

   /** Sets a channel's visual frame from the agent vocabulary. */
   public static void setFrame(ChartBindingModel model, String channel,
                               Map<String, Object> spec)
   {
      setFrame(model, channel, spec, false);
   }

   /** @param relationChart see {@link AestheticChannels#requireFieldChannel(String, boolean)}. */
   public static void setFrame(ChartBindingModel model, String channel,
                               Map<String, Object> spec, boolean relationChart)
   {
      setFrame(model, channel, spec, relationChart, AestheticChannels.FRAME_CHANNELS);
   }

   /**
    * @param perMeasureFrameChannels the frame channels this chart renders from each measure's own
    *        frame property rather than from the chart-level one — i.e. the channels whose
    *        {@code VSFrameVisitor} strategy answers {@code supportsFieldFrame()} true for this
    *        chart info. It is <b>not</b> the same set for every chart type, which is why it is a
    *        parameter rather than a constant: {@code MergedVSChartInfo} (candle, stock, relation,
    *        map) answers false for colour and shape, {@code RadarVSChartInfo} also for size, and
    *        {@code AbstractChartInfo} answers false for all three on a contour chart. Broadcasting
    *        to the aggregates on those would store the frame in a slot the renderer never reads —
    *        the very defect this method's aggregate branch exists to fix, mirrored.
    *        {@code ChartAestheticAgentService} builds the set by asking the real {@code
    *        VSChartInfo}; callers with no chart in hand pass
    *        {@link AestheticChannels#FRAME_CHANNELS}, correct for every ordinary
    *        {@code DefaultVSChartInfo} chart.
    *
    *        <p>Note that "not per measure" does not always mean "the chart-level slot renders
    *        instead". {@code createFrame()} falls back to {@code getGeneralFrame()} only when the
    *        strategy's {@code supportsGeneralFrame()} is true, and for colour, shape, line and
    *        texture that is {@code info instanceof MergedChartInfo} — true for candle, stock,
    *        relation and map, false for a <em>scatter</em> contour, which is a
    *        {@code DefaultVSChartInfo}. On that one chart neither slot is read and a field-less
    *        frame renders from nowhere; the chart-level slot is still where this method writes it,
    *        because that is at least the slot {@link #frameOf} reports from, but it is a slot the
    *        renderer ignores rather than a working fallback.
    */
   public static void setFrame(ChartBindingModel model, String channel,
                               Map<String, Object> spec, boolean relationChart,
                               Collection<String> perMeasureFrameChannels)
   {
      String name = AestheticChannels.requireFrameChannel(channel, relationChart);
      VisualFrameModel frame = VisualFrameAliases.create(name, spec, relationChart);
      AestheticInfo field = acceptsField(name) ? read(model, name) : null;

      // A bound field carries its own frame (AestheticInfo.frame) -- that, not the top-level
      // ChartBindingModel.xxxFrame property, is what the interactive Composer's own dialog writes
      // (ColorFieldMc.changeColorFrame et al.) and what the render path
      // (VSFrameVisitor.createVisualFrame -> AestheticRef.getVisualFrame) reads at paint time.
      // Writing only the top-level property round-trips cleanly through get_chart_aesthetics/
      // set_visual_frame -- both read and write the same wrong place -- but never reaches the chart.
      if(field != null) {
         field.setFrame(frame);
         return;
      }

      // A field-less color/shape/size/line/texture frame renders from each measure's own frame
      // property (VSChartAggregateRef.getColorFrame() et al.), not from the top-level
      // ChartBindingModel property -- VSFrameVisitor.createFrame() only falls back to the
      // top-level slot for MergedChartInfo charts (Candle/Stock/Radar/Relation/Map), which
      // DefaultVSChartInfo -- built for every ordinary bar/line/point/pie/area chart -- is not.
      // (Gantt is a MergedVSChartInfo but not one of them: it overrides all three
      // supports*FieldFrame() predicates back to true, so it renders per measure like an ordinary
      // chart. aggregates() handles where its measures actually live.)
      // The interactive Composer's own aesthetic dialogs (ColorFieldMc/ShapeFieldMc) confirm this
      // is the real per-measure home for a field-less frame, not an edge case. A field-less
      // request has no way to name a single measure, so it is broadcast to every aggregate
      // aggregates() finds -- which is every aggregate the renderer will build the combined frame
      // from, no more and no less. The common single-measure chart makes this unambiguous, and a
      // multi-measure chart gets the same frame on every measure rather than silently only one.
      //
      // Gated on perMeasureFrameChannels, not on FRAME_CHANNELS: whether the renderer reads the
      // per-measure slot or the chart-level one is a property of the chart type, not of the
      // channel (see the parameter's javadoc).
      List<ChartAggregateRefModel> aggregates =
         perMeasureFrameChannels.contains(name) ? aggregates(model) : List.of();

      if(!aggregates.isEmpty()) {
         switch(name) {
            case "color" -> aggregates.forEach(agg -> agg.setColorFrame((ColorFrameModel) frame));
            case "shape" -> aggregates.forEach(agg -> agg.setShapeFrame((ShapeFrameModel) frame));
            case "size" -> aggregates.forEach(agg -> agg.setSizeFrame((SizeFrameModel) frame));
            case "line" -> aggregates.forEach(agg -> agg.setLineFrame((LineFrameModel) frame));
            case "texture" ->
               aggregates.forEach(agg -> agg.setTextureFrame((TextureFrameModel) frame));
            default -> throw new IllegalArgumentException("Unhandled frame channel '" + name + "'.");
         }

         return;
      }

      // A node channel, which has no per-aggregate slot; a chart type whose renderer reads the
      // chart-level slot for this channel (a MergedChartInfo chart -- candle, stock, radar,
      // relation, map); or no measure to target at all.
      //
      // Only the middle case is a fallback the renderer honours. With no measure, createFrame()
      // leaves the frame null without consulting getGeneralFrame() -- the arr.length > 0 test is
      // nested inside the supportsFieldFrame() branch, not chained after it -- and a scatter
      // contour reads neither slot (see the perMeasureFrameChannels javadoc). Writing here is
      // still right for both: it is the slot frameOf() reports from, so the read keeps agreeing
      // with the write, and a chart with no measure renders nothing to disagree about.
      switch(name) {
         case "color" -> model.setColorFrame((ColorFrameModel) frame);
         case "shape" -> model.setShapeFrame((ShapeFrameModel) frame);
         case "size" -> model.setSizeFrame((SizeFrameModel) frame);
         case "line" -> model.setLineFrame((LineFrameModel) frame);
         case "texture" -> model.setTextureFrame((TextureFrameModel) frame);
         case "node-color" -> model.setNodeColorFrame((ColorFrameModel) frame);
         case "node-size" -> model.setNodeSizeFrame((SizeFrameModel) frame);
         default -> throw new IllegalArgumentException("Unhandled frame channel '" + name + "'.");
      }
   }

   /**
    * The measures a field-less frame channel targets — a port of
    * {@code VSFrameVisitor.getAggregates()}, because the write has to land on the same refs the
    * renderer reads the frame back off, and a paraphrase of that method is not close enough.
    *
    * <p>Ported rather than paraphrased for three reasons, each of which produced a frame in a slot
    * nothing renders from when this took the shape its name suggests instead:
    *
    * <ul>
    *   <li>the Y-or-X choice is made by {@code containsMeasure}, which despite the name tests
    *       only the <em>last</em> ref on the shelf — {@code [Sum(Sales), Region]} sends the
    *       renderer to X, however many measures Y holds;</li>
    *   <li>within a shelf, {@code getAggregates(ChartRef...)} walks <em>backwards</em> and breaks
    *       at the first non-measure, so it sees only the trailing contiguous run —
    *       {@code [Sum(Sales), Region, Sum(Profit)]} renders from {@code Sum(Profit)} alone, and
    *       reporting the shelf's first aggregate instead would name a measure the chart ignores;
    *       </li>
    *   <li>"measure" there is {@code ChartAggregateRef.isMeasure()}, which is {@code !isDiscrete()}
    *       — a discrete aggregate ends the run and fails the last-ref test, so it must not be
    *       counted here either.</li>
    * </ul>
    *
    * <p>A Gantt chart is the one branch the chart-type gate cannot catch: {@code GanttVSChartInfo}
    * overrides all three {@code supports*FieldFrame()} predicates back to {@code true}, so it
    * arrives here, but its X/Y shelves hold only dimensions — {@code
    * ChangeChartTypeProcessor.copyToGantt} routes every measure onto {@code startField}'s own
    * aesthetic channels and every remaining dimension onto Y. {@code getAggregates()} reads
    * start/milestone for exactly that reason, through the same backwards walk, and so does this.
    *
    * <p>What is <b>not</b> mirrored, because it cannot be from here: {@code getAggregates()} reads
    * the runtime refs ({@code getRTYFields()}, {@code getRTStartField()}) and a
    * {@code ChartBindingModel} carries the design-time ones. That is the right side for a write —
    * design time is what persists and what the runtime is rebuilt from — but it means this mirrors
    * the algorithm, not its inputs.
    */
   private static List<ChartAggregateRefModel> aggregates(ChartBindingModel model) {
      if(GraphTypes.isGantt(model.getChartType())) {
         return trailingMeasures(Arrays.asList(model.getStartField(), model.getMilestoneField()));
      }

      List<ChartRefModel> yFields = model.getYFields();
      return endsWithMeasure(yFields)
         ? trailingMeasures(yFields) : trailingMeasures(model.getXFields());
   }

   /** {@code VSFrameVisitor.containsMeasure}: the last ref on the shelf, and only that one. */
   private static boolean endsWithMeasure(List<ChartRefModel> refs) {
      return refs != null && !refs.isEmpty() && isMeasure(refs.get(refs.size() - 1));
   }

   /**
    * {@code VSFrameVisitor.getAggregates(ChartRef...)}: walk from the end, skip a null slot, and
    * stop at the first ref that is not a measure.
    */
   private static List<ChartAggregateRefModel> trailingMeasures(List<ChartRefModel> refs) {
      List<ChartAggregateRefModel> aggregates = new ArrayList<>();

      if(refs == null) {
         return aggregates;
      }

      for(int i = refs.size() - 1; i >= 0; i--) {
         ChartRefModel ref = refs.get(i);

         if(ref == null) {
            continue;
         }

         if(!isMeasure(ref)) {
            break;
         }

         aggregates.add(0, (ChartAggregateRefModel) ref);
      }

      return aggregates;
   }

   /** {@code ChartAggregateRef.isMeasure()}, which is {@code !isDiscrete()}. */
   private static boolean isMeasure(ChartRefModel ref) {
      return ref instanceof ChartAggregateRefModel aggregate && !aggregate.isDiscrete();
   }

   /**
    * Every channel the vocabulary knows, with what it currently holds. Channels are reported
    * even when unbound, so an agent reading a chart learns what it *could* set rather than
    * only what someone already set.
    */
   public static Map<String, Object> describe(ChartBindingModel model) {
      return describe(model, false);
   }

   /**
    * @param relationChart whether to also report {@link AestheticChannels#NODE_CHANNELS}. Left
    *                      out for every other chart type, so the response never advertises a
    *                      channel this chart cannot render.
    */
   public static Map<String, Object> describe(ChartBindingModel model, boolean relationChart) {
      return describe(model, relationChart, AestheticChannels.FRAME_CHANNELS);
   }

   /**
    * @param perMeasureFrameChannels see
    *        {@link #setFrame(ChartBindingModel, String, Map, boolean, Collection)}. The read must
    *        be given the same set as the write, or it reports a slot the chart does not render
    *        from.
    */
   public static Map<String, Object> describe(ChartBindingModel model, boolean relationChart,
                                              Collection<String> perMeasureFrameChannels)
   {
      Map<String, Object> out = new LinkedHashMap<>();

      for(String channel : AestheticChannels.FIELD_CHANNELS) {
         out.put(channel, channelView(model, channel, perMeasureFrameChannels));
      }

      for(String channel : AestheticChannels.FRAME_CHANNELS) {
         out.computeIfAbsent(channel, name -> channelView(model, name, perMeasureFrameChannels));
      }

      if(relationChart) {
         for(String channel : AestheticChannels.NODE_CHANNELS) {
            out.put(channel, channelView(model, channel, perMeasureFrameChannels));
         }
      }

      return out;
   }

   /**
    * The field channels currently holding a binding, in vocabulary order.
    *
    * <p>Lives here, not in the caller that needs it: the 2b/2c ownership split declared in
    * {@link ChartBindingFields} gives these properties one reader, so a check elsewhere that kept
    * its own list of channels would drift from {@link #assign} the first time one is added. A
    * chart can be bound entirely through a channel — a word cloud is nothing but a text channel —
    * and {@code VSAssemblyInfoHandler.validateAestheticFields} deletes exactly these when a chart
    * is repointed, so anything asking "would this repoint discard fields?" has to count them.
    *
    * <p>Node channels are counted whatever the chart type. They only render on a relation chart,
    * but a non-null one is still a binding a repoint would delete, and the read cannot invent one:
    * {@code ChartAestheticService} populates them only for a {@code RelationChartInfo}.
    *
    * <p>A non-null {@code AestheticInfo} <em>is</em> the binding: the read path
    * {@code AestheticRefModelFactory.createAestheticInfo} returns null for an absent
    * {@code AestheticRef} rather than an empty wrapper. Counting the wrapper rather than the field
    * name it resolves to is deliberate — a wrapper whose name cannot be read is still something
    * the user bound, and over-reporting here costs a caller one {@code force}, while
    * under-reporting costs them the binding.
    */
   public static List<String> boundFieldChannels(ChartBindingModel model) {
      List<String> bound = new ArrayList<>();

      for(String channel : AestheticChannels.FIELD_CHANNELS) {
         if(read(model, channel) != null) {
            bound.add(channel);
         }
      }

      for(String channel : AestheticChannels.NODE_CHANNELS) {
         if(read(model, channel) != null) {
            bound.add(channel);
         }
      }

      return bound;
   }

   private static boolean acceptsField(String channel) {
      return AestheticChannels.FIELD_CHANNELS.contains(channel) ||
         AestheticChannels.NODE_CHANNELS.contains(channel);
   }

   private static boolean acceptsFrame(String channel) {
      return AestheticChannels.FRAME_CHANNELS.contains(channel) ||
         AestheticChannels.NODE_CHANNELS.contains(channel);
   }

   private static Map<String, Object> channelView(ChartBindingModel model, String channel,
                                                  Collection<String> perMeasureFrameChannels)
   {
      Map<String, Object> view = new LinkedHashMap<>();
      AestheticInfo info = acceptsField(channel) ? read(model, channel) : null;

      view.put("field", fieldNameOf(info));
      view.put("frame",
               VisualFrameAliases.describe(frameOf(model, channel, perMeasureFrameChannels)));
      view.put("acceptsField", acceptsField(channel));
      view.put("acceptsFrame", acceptsFrame(channel));
      return view;
   }

   /**
    * The name of the field bound to a channel.
    *
    * <p>Read from the {@code dataInfo}, not from {@link AestheticInfo#getFullName()}. The read
    * path — {@code AestheticRefModelFactory.createAestheticInfo} — sets only {@code dataInfo} and
    * {@code frame}; nothing there ever sets the {@code AestheticInfo}'s own {@code fullName}. The
    * only writer of that field is {@link #setField}, ours. So asking the {@code AestheticInfo} for
    * its name reported {@code null} for every channel of every chart, while the aesthetic was
    * visibly rendering — the write worked, the read lied.
    *
    * <p>{@code fullName} is kept only as a fallback, for the models our own writer built.
    */
   private static String fieldNameOf(AestheticInfo info) {
      if(info == null) {
         return null;
      }

      BindingRefModel dataInfo = info.getDataInfo();

      if(dataInfo != null && dataInfo.getFullName() != null) {
         return dataInfo.getFullName();
      }

      return info.getFullName();
   }

   /**
    * A channel's current frame, for {@link #describe}/{@link #channelView} and for
    * {@link #setField}'s carry-forward of a frame set before any field was bound.
    *
    * <p>Must mirror {@link #setFrame}'s write side exactly: for an unbound
    * {@code perMeasureFrameChannels} channel with at least one measure in {@link #aggregates},
    * that is where
    * {@code setFrame} wrote and where the render path ({@code VSFrameVisitor.createFrame}) reads
    * — not the top-level {@code ChartBindingModel} property. Reading the top-level property here
    * while the renderer consults the per-measure one would report a value the chart never shows,
    * exactly the "reads the dead slot" defect this method exists to avoid. The gate is the same
    * chart-type-derived set the write side takes, for the same reason.
    *
    * <p>When several measures disagree (only possible from state {@code setFrame} did not itself
    * produce — it always writes the same frame to every measure), the first of {@link #aggregates}
    * is reported; there is no "mixed" sentinel in the agent vocabulary to report disagreement
    * with. That is the first measure the renderer itself combines, not merely the first on the
    * shelf, which is the reason {@code aggregates} is a port of {@code getAggregates()} rather
    * than a paraphrase — reporting a measure the chart ignores would be the same "reads the dead
    * slot" defect one step removed.
    */
   private static VisualFrameModel frameOf(ChartBindingModel model, String channel,
                                           Collection<String> perMeasureFrameChannels)
   {
      AestheticInfo field = acceptsField(channel) ? read(model, channel) : null;

      if(field != null) {
         return field.getFrame();
      }

      List<ChartAggregateRefModel> aggregates =
         perMeasureFrameChannels.contains(channel) ? aggregates(model) : List.of();

      if(!aggregates.isEmpty()) {
         return aggregateFrameOf(aggregates.get(0), channel);
      }

      return switch(channel) {
         case "color" -> model.getColorFrame();
         case "shape" -> model.getShapeFrame();
         case "size" -> model.getSizeFrame();
         case "line" -> model.getLineFrame();
         case "texture" -> model.getTextureFrame();
         case "node-color" -> model.getNodeColorFrame();
         case "node-size" -> model.getNodeSizeFrame();
         default -> null;
      };
   }

   private static VisualFrameModel aggregateFrameOf(ChartAggregateRefModel aggregate,
                                                     String channel)
   {
      return switch(channel) {
         case "color" -> aggregate.getColorFrame();
         case "shape" -> aggregate.getShapeFrame();
         case "size" -> aggregate.getSizeFrame();
         case "line" -> aggregate.getLineFrame();
         case "texture" -> aggregate.getTextureFrame();
         default -> null;
      };
   }

   private static AestheticInfo read(ChartBindingModel model, String channel) {
      return switch(channel) {
         case "color" -> model.getColorField();
         case "shape" -> model.getShapeField();
         case "size" -> model.getSizeField();
         case "text" -> model.getTextField();
         case "node-color" -> model.getNodeColorField();
         case "node-size" -> model.getNodeSizeField();
         default -> null;
      };
   }

   private static void assign(ChartBindingModel model, String channel, AestheticInfo info) {
      switch(channel) {
         case "color" -> model.setColorField(info);
         case "shape" -> model.setShapeField(info);
         case "size" -> model.setSizeField(info);
         case "text" -> model.setTextField(info);
         case "node-color" -> model.setNodeColorField(info);
         case "node-size" -> model.setNodeSizeField(info);
         default -> throw new IllegalArgumentException("Unhandled field channel '" + channel + "'.");
      }
   }
}
