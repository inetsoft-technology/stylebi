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

import inetsoft.graph.aesthetic.CategoricalColorFrame;
import inetsoft.report.StyleConstants;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.asset.SourceInfo;
import inetsoft.uql.viewsheet.graph.GraphTypes;
import inetsoft.web.binding.model.BindingRefModel;
import inetsoft.web.binding.model.ChartBindingModel;
import inetsoft.web.binding.model.ColorMapModel;
import inetsoft.web.binding.model.graph.AestheticInfo;
import inetsoft.web.binding.model.graph.ChartAggregateRefModel;
import inetsoft.web.binding.model.graph.ChartRefModel;
import inetsoft.web.binding.model.graph.aesthetic.*;
import inetsoft.web.binding.service.DataRefModelFactoryService;
import inetsoft.web.wiz.binding.model.FieldRef;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
      setFrame(model, channel, spec, relationChart, perMeasureFrameChannels, null);
   }

   /**
    * @param measure one of the chart's own measures, to give it its own frame instead of giving
    *        every measure the same one — the Composer's {@code CombinedColor}/{@code CombinedSize}
    *        pane, which draws one editor per measure and labels it with the measure's name. It
    *        applies only where the channel renders per measure and has no field bound, which is
    *        exactly when that pane opens ({@code getEditPaneId} returns Combined when
    *        {@code frames.length > 1}); anywhere else there is one frame for the channel and
    *        naming a measure is refused rather than quietly widened to all of them. Null broadcasts
    *        as before, which is right for the common single-measure chart and keeps "make the bars
    *        blue" a one-call operation.
    */
   public static void setFrame(ChartBindingModel model, String channel,
                               Map<String, Object> spec, boolean relationChart,
                               Collection<String> perMeasureFrameChannels, String measure)
   {
      String name = AestheticChannels.requireFrameChannel(channel, relationChart);
      AestheticInfo field = frameField(model, name);
      VisualFrameModel frame = carryCategoricalColors(
         VisualFrameAliases.create(name, spec, relationChart),
         frameOf(model, name, perMeasureFrameChannels), name, fieldNameOf(field), spec);

      // A bound field carries its own frame (AestheticInfo.frame) -- that, not the top-level
      // ChartBindingModel.xxxFrame property, is what the interactive Composer's own dialog writes
      // (ColorFieldMc.changeColorFrame et al.) and what the render path
      // (VSFrameVisitor.createVisualFrame -> AestheticRef.getVisualFrame) reads at paint time.
      // Writing only the top-level property round-trips cleanly through get_chart_aesthetics/
      // set_visual_frame -- both read and write the same wrong place -- but never reaches the chart.
      if(field != null) {
         if(measure != null) {
            throw new IllegalArgumentException(
               "'" + fieldNameOf(field) + "' is bound to the " + name + " channel, so the channel " +
               "has a single frame that varies over that field's values — there is no per-measure " +
               "frame for 'measure' to name. The Composer opens its Combined pane only while the " +
               "channel is empty. Clear the field with clear_aesthetic_field to give each measure " +
               "its own " + name + ", or drop 'measure' to set the frame the field drives.");
         }

         requireFrameTheFieldCanDrive(name, frame, field);
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
         requireStaticColorOnMeasures(name, frame);

         if(measure != null) {
            assignAggregateFrame(requireTargetMeasure(aggregates, name, measure), name, frame);
            return;
         }

         // A fresh instance per measure, not one object handed to all of them. They are separate
         // frames in the Composer's own model -- that is what the Combined pane edits -- and
         // sharing one meant a later per-measure write, or a reset, which edits the frame in
         // place, reached every measure at once. Rebuilt from the spec rather than copied because
         // create() is a pure function of it; carryCategoricalColors is not reapplied because it
         // only touches a CategoricalColorModel, which requireStaticColorOnMeasures has already
         // ruled out here.
         for(ChartAggregateRefModel aggregate : aggregates) {
            assignAggregateFrame(aggregate, name,
                                 VisualFrameAliases.create(name, spec, relationChart));
         }

         return;
      }

      // 'measure' can only mean one of the per-measure slots, and this call is not writing one.
      if(measure != null) {
         throw new IllegalArgumentException(
            "'measure' gives one of the chart's measures its own " + name + " — the Composer's " +
            "Combined pane — and applies only where the channel renders per measure. " +
            (perMeasureFrameChannels.contains(name)
                ? "This chart has no measure for " + name + " to render from."
                : "This chart type renders " + name + " from one chart-level frame rather than " +
                  "from each measure, so every measure already shares it.") +
            " Drop 'measure' to set the frame the channel does render from.");
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
    * Puts a channel's frame back to the values it would have had untouched — the "Reset to
    * Default" button on the aesthetic panes.
    *
    * <p>Four panes carry that button and no two do the same thing:
    *
    * <ul>
    *   <li>{@code categorical-color-pane} restores each colour to {@code cssColors[i]} or, failing
    *       that, {@code defaultColors[i]} — the CSS theme's palette or the built-in one.</li>
    *   <li>{@code combined-color-pane} restores each measure's colour to the default palette entry
    *       for its position and clears {@code changed}. That position is what gives two measures
    *       two colours again rather than both the first one.</li>
    *   <li>{@code categorical-shape-pane} serves three families from one button, switching on the
    *       chart's own: shapes back to {@code SHAPE_STYLES + IMAGE_SHAPES}, lines back to
    *       {@code M_LINE_STYLES} twice over, textures back to {@code TEXTURE_STYLES} minus its
    *       first entry (PATTERN_NONE, which is "no texture" and would reset a category to
    *       invisible).</li>
    *   <li>{@code linear-color-pane}'s {@code resetEditors()} re-syncs its radio buttons and
    *       dropdowns against the current frame and changes no stored value at all.</li>
    * </ul>
    *
    * <p>So the first three are implemented, each mirroring its pane exactly. Every other frame is
    * refused by name rather than given an invented meaning: a reset that quietly did something
    * other than the button would be worse than no reset. That leaves the graduated frames and the
    * static shape/size/line/texture ones out, which is also where the Composer offers no reset.
    *
    * <p>The per-value pins are left alone, as the categorical pane's own reset leaves them — the
    * button sits beside the palette editors, not beside "Assign Fixed Mapping".
    */
   public static void resetFrame(ChartBindingModel model, String channel, boolean relationChart,
                                 Collection<String> perMeasureFrameChannels, String measure)
   {
      String name = AestheticChannels.requireFrameChannel(channel, relationChart);
      AestheticInfo field = frameField(model, name);

      if(field != null) {
         if(measure != null) {
            throw new IllegalArgumentException(
               "'" + fieldNameOf(field) + "' is bound to the " + name + " channel, so it has one " +
               "frame rather than one per measure. Drop 'measure'.");
         }

         field.setFrame(resetted(field.getFrame(), name, 0));
         return;
      }

      List<ChartAggregateRefModel> aggregates =
         perMeasureFrameChannels.contains(name) ? aggregates(model) : List.of();

      if(!aggregates.isEmpty()) {
         ChartAggregateRefModel only =
            measure == null ? null : requireTargetMeasure(aggregates, name, measure);

         // Walked by index rather than by List.indexOf: the position drives
         // combined-color-pane's default, each measure taking the palette entry for its own place,
         // and two measures alike enough to compare equal would both have looked up as position
         // zero -- resetting to one colour, which is the outcome the position exists to prevent.
         for(int i = 0; i < aggregates.size(); i++) {
            ChartAggregateRefModel aggregate = aggregates.get(i);

            if(only == null || aggregate == only) {
               assignAggregateFrame(
                  aggregate, name, resetted(aggregateFrameOf(aggregate, name), name, i));
            }
         }

         return;
      }

      if(measure != null) {
         throw new IllegalArgumentException(
            "'measure' applies only where the " + name + " channel renders per measure, and this " +
            "chart does not. Drop it to reset the frame the channel does render from.");
      }

      switch(name) {
         case "color" -> model.setColorFrame((ColorFrameModel) resetted(model.getColorFrame(), name, 0));
         case "shape" -> model.setShapeFrame((ShapeFrameModel) resetted(model.getShapeFrame(), name, 0));
         case "size" -> model.setSizeFrame((SizeFrameModel) resetted(model.getSizeFrame(), name, 0));
         case "line" -> model.setLineFrame((LineFrameModel) resetted(model.getLineFrame(), name, 0));
         case "texture" ->
            model.setTextureFrame((TextureFrameModel) resetted(model.getTextureFrame(), name, 0));
         case "node-color" ->
            model.setNodeColorFrame((ColorFrameModel) resetted(model.getNodeColorFrame(), name, 0));
         case "node-size" ->
            model.setNodeSizeFrame((SizeFrameModel) resetted(model.getNodeSizeFrame(), name, 0));
         default -> throw new IllegalArgumentException("Unhandled frame channel '" + name + "'.");
      }
   }

   /** The same frame with its values back at their defaults. See {@link #resetFrame}. */
   private static VisualFrameModel resetted(VisualFrameModel frame, String channel, int position) {
      if(frame instanceof CategoricalColorModel categorical) {
         String[] colors = categorical.getColors();
         String[] css = categorical.getCssColors();
         String[] defaults = categorical.getDefaultColors();

         if(colors == null) {
            return frame;
         }

         for(int i = 0; i < colors.length; i++) {
            String reset = css != null && i < css.length && css[i] != null && !css[i].isBlank()
               ? css[i] : (defaults != null && i < defaults.length ? defaults[i] : colors[i]);

            if(reset != null) {
               colors[i] = reset;
            }
         }

         categorical.setColors(colors);
         return frame;
      }

      if(frame instanceof StaticColorModel staticColor) {
         // combined-color-pane's own order: the palette entry for this measure's position, except
         // for a waterfall's summary frame, which has a default of its own and is the one case
         // that reads defaultColor. Taking defaultColor first instead reset every measure to the
         // same colour, which is exactly what the position is there to prevent.
         String summaryDefault = staticColor.getDefaultColor();
         staticColor.setColor(
            staticColor.isSummary() && summaryDefault != null && !summaryDefault.isBlank()
               ? summaryDefault : defaultPaletteColor(position));
         staticColor.setChanged(false);
         return frame;
      }

      if(frame instanceof CategoricalShapeModel shapes) {
         shapes.setShapes(DEFAULT_SHAPES.toArray(new String[0]));
         return frame;
      }

      if(frame instanceof CategoricalLineModel lines) {
         lines.setLines(DEFAULT_LINES);
         return frame;
      }

      if(frame instanceof CategoricalTextureModel textures) {
         textures.setTextures(DEFAULT_TEXTURES);
         return frame;
      }

      throw new IllegalArgumentException(
         "The " + channel + " channel is holding a '" + VisualFrameAliases.typeName(frame) +
         "' frame, which has no default to go back to — only a categorical palette, a categorical " +
         "shape/line/texture set and the per-measure static colours do, which are the panes the " +
         "Composer puts a \"Reset to Default\" button on. Set the value you want with " +
         "set_visual_frame instead.");
   }

   /**
    * {@code ChartConfig.SHAPE_STYLES.concat(ChartConfig.IMAGE_SHAPES)} — the sixteen point shapes
    * followed by the sixteen bundled images, in the order the pane's own reset uses. NIL is left
    * out, as it is there: resetting a category to "no shape" would hide it.
    */
   private static final List<String> DEFAULT_SHAPES = List.of(
      String.valueOf(StyleConstants.CIRCLE), String.valueOf(StyleConstants.TRIANGLE),
      String.valueOf(StyleConstants.SQUARE), String.valueOf(StyleConstants.CROSS),
      String.valueOf(StyleConstants.STAR), String.valueOf(StyleConstants.DIAMOND),
      String.valueOf(StyleConstants.X), String.valueOf(StyleConstants.FILLED_CIRCLE),
      String.valueOf(StyleConstants.FILLED_TRIANGLE), String.valueOf(StyleConstants.FILLED_SQUARE),
      String.valueOf(StyleConstants.FILLED_DIAMOND), String.valueOf(StyleConstants.V_ANGLE),
      String.valueOf(StyleConstants.RIGHT_ANGLE), String.valueOf(StyleConstants.LT_ANGLE),
      String.valueOf(StyleConstants.V_LINE), String.valueOf(StyleConstants.H_LINE),
      "100ArrowDown.svg", "101ArrowUp.svg", "102Check.svg", "103Cancel.svg",
      "104Exclamation.svg", "105Flag.svg", "106Light.svg", "107Star.svg",
      "108No.svg", "109Man.svg", "110Woman.svg", "111FaceHappy.svg",
      "112FaceSad.svg", "113Face.svg", "114ArrowUperRight.svg", "115ArrowLowerRight.svg");

   /**
    * {@code ChartConfig.M_LINE_STYLES.concat(ChartConfig.M_LINE_STYLES)} — the five line styles
    * the picker offers, listed twice, which is how the pane's reset covers ten categories with
    * five styles.
    */
   private static final int[] DEFAULT_LINES = {
      StyleConstants.THIN_LINE, StyleConstants.DOT_LINE, StyleConstants.DASH_LINE,
      StyleConstants.MEDIUM_DASH, StyleConstants.LARGE_DASH,
      StyleConstants.THIN_LINE, StyleConstants.DOT_LINE, StyleConstants.DASH_LINE,
      StyleConstants.MEDIUM_DASH, StyleConstants.LARGE_DASH
   };

   /** {@code ChartConfig.TEXTURE_STYLES.slice(1)} — PATTERN_0 through PATTERN_19. */
   private static final int[] DEFAULT_TEXTURES = {
      0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19
   };

   /**
    * The default palette entry for a measure's position, matching {@code combined-color-pane}'s
    * {@code DefaultPalette.chart.flat()[i % length]}. {@code CategoricalColorFrame.COLOR_PALETTE}
    * is the same list the Composer's palette is generated from, and is what
    * {@code GraphUtil.fixDuplicateColor} hands a fresh measure.
    */
   private static String defaultPaletteColor(int position) {
      Color[] palette = CategoricalColorFrame.COLOR_PALETTE;
      Color color = palette[Math.max(position, 0) % palette.length];
      return "#" + String.format("%06X", color.getRGB() & 0xFFFFFF);
   }

   /**
    * The one measure a {@code measure}-scoped write targets, or a refusal naming the ones that
    * exist.
    *
    * <p>Matched against every name the model carries for a measure, not just {@code getFullName()}.
    * The aggregated name — {@code Sum(Sales)} — is what {@code get_binding} reports and what
    * {@code set_chart_type}'s own per-measure parameter takes, so it has to work; but the same
    * measure also answers to its bare column, and which of the two is populated depends on how the
    * model was built ({@code FieldRefFactory} sets only the column name; the read path fills the
    * rest in from the live {@code ChartRef}). Accepting either is the forgiving half of the rule
    * that matters here — the caller has named one measure unambiguously, and refusing over which
    * spelling they reached for would be a refusal with no purpose. The names actually available
    * are listed back on a miss, so the next attempt does not have to guess.
    */
   private static ChartAggregateRefModel requireTargetMeasure(
      List<ChartAggregateRefModel> aggregates, String channel, String measure)
   {
      List<String> known = new ArrayList<>();

      for(ChartAggregateRefModel aggregate : aggregates) {
         List<String> names = measureNames(aggregate);
         known.addAll(names);

         if(names.contains(measure)) {
            return aggregate;
         }
      }

      throw new IllegalArgumentException(
         "No measure '" + measure + "' renders the " + channel + " channel on this chart. The " +
         "ones that do answer to: " + String.join(", ", known) + ". get_binding reports them " +
         "under the first of those spellings.");
   }

   /** Every name one measure answers to. See {@link #requireTargetMeasure}. */
   private static List<String> measureNames(ChartAggregateRefModel aggregate) {
      List<String> names = new ArrayList<>();

      for(String name : Arrays.asList(aggregate.getFullName(), aggregate.getOriFullName(),
                                      aggregate.getView(), aggregate.getName()))
      {
         if(name != null && !name.isBlank() && !names.contains(name)) {
            names.add(name);
         }
      }

      return names;
   }

   /** The name to report a measure under — the aggregated one where the model has it. */
   private static String measureLabel(ChartAggregateRefModel aggregate) {
      List<String> names = measureNames(aggregate);
      return names.isEmpty() ? String.valueOf(aggregate.getFullName()) : names.get(0);
   }

   private static void assignAggregateFrame(ChartAggregateRefModel aggregate, String channel,
                                            VisualFrameModel frame)
   {
      switch(channel) {
         case "color" -> aggregate.setColorFrame((ColorFrameModel) frame);
         case "shape" -> aggregate.setShapeFrame((ShapeFrameModel) frame);
         case "size" -> aggregate.setSizeFrame((SizeFrameModel) frame);
         case "line" -> aggregate.setLineFrame((LineFrameModel) frame);
         case "texture" -> aggregate.setTextureFrame((TextureFrameModel) frame);
         default -> throw new IllegalArgumentException("Unhandled frame channel '" + channel + "'.");
      }
   }

   /**
    * Fills in a mapping-only categorical colour frame's {@code colors} from whatever the channel
    * already renders, so the request reaching the factory has the same shape the Composer's own
    * dialog sends.
    *
    * <p>{@code CategoricalColorFrameModelFactory.updateVisualFrameWrapper0} returns early — before
    * {@code assignMappedColors}, {@code setUseGlobal}, {@code setShareColors} and
    * {@code setColorValueFrame} — when the model carries no colours. A frame built from
    * {@code {type: "categorical", mapping: {...}}} alone therefore reached the factory, was
    * accepted, and was discarded whole: the write reported success, {@code get_chart_aesthetics}
    * read back {@code mapping: {}} with {@code useGlobal} flipped back to true, and the chart kept
    * its old colours.
    *
    * <p>That precondition is not something to relax in the factory. The interactive path always
    * satisfies it — {@code categorical-color-pane} renders one editor per entry of
    * {@code frameModel.colors}, so Apply sends the whole palette alongside the colour maps — and
    * the factory is shared with it. What was missing is that the agent path did not satisfy it.
    * So it does now, and carrying the channel's current palette rather than inventing one means
    * "pin these values" changes only those values, exactly as the dialog does: the factory pins
    * every supplied colour with {@code setUserColor}, which is what Apply already does today.
    *
    * <p>With nothing to carry — a colour channel holding a static or linear frame, i.e. one with
    * no categories to map — the request is refused rather than silently dropped. The Composer
    * hides "Assign Fixed Mapping" in exactly that case ({@code isDimension()}), so this is the
    * same answer, given out loud.
    */
   private static VisualFrameModel carryCategoricalColors(VisualFrameModel frame,
                                                          VisualFrameModel existing,
                                                          String channel, String fieldName,
                                                          Map<String, Object> spec)
   {
      if(!(frame instanceof CategoricalColorModel built)) {
         return frame;
      }

      carryShareColorState(built, existing, fieldName, spec);

      if(built.getColors() != null && built.getColors().length > 0) {
         return frame;
      }

      if(existing instanceof CategoricalColorModel current && current.getColors() != null &&
         current.getColors().length > 0)
      {
         built.setColors(current.getColors());
         return frame;
      }

      throw new IllegalArgumentException(
         "The " + channel + " channel has no categorical palette to pin values against — it is " +
         "not currently rendering by category, so there are no categories a 'mapping' could name. " +
         "Bind a dimension to it with set_aesthetic_field first, or pass an explicit 'colors' " +
         "list alongside the mapping.");
   }

   /**
    * Gives an agent-built categorical colour frame the two properties the interactive path fills
    * in for free, both of which {@code VSChartBindingFactory.applyColorsToViewsheet} reads the
    * moment "Share Colors" is on.
    *
    * <p><b>{@code field}.</b> {@code VisualFrameModel}'s wrapper constructor seeds it from
    * {@code wrapper.getVisualFrame().getField()}, so every frame model the Composer sends carries
    * one. A frame built here is a bare {@code new CategoricalColorModel()} and carries none, and
    * {@code applyColorsToViewsheet} passes it straight to {@code Viewsheet.setDimensionColors},
    * whose {@code getAttribute} does {@code column.indexOf(":")} — a hard NPE, a 500, and no edit
    * applied. That is a crash the agent path reached on every single {@code shareColors: true}
    * call against a bound colour channel, so the carry-forward is what makes the option usable at
    * all rather than a nicety.
    *
    * <p><b>{@code globalColorMaps}.</b> These are the viewsheet-level value-to-colour pins behind
    * the categorical pane's "Assign Fixed Mapping" button, stored on the {@code Viewsheet} rather
    * than on the frame and shared by every chart colouring by that column. {@code
    * applyColorsToViewsheet} rewrites the whole column's entry from this array, and {@code
    * Viewsheet.setDimensionColors} removes the column's existing keys before putting the new ones
    * — so shipping an empty array does not leave the pins alone, it deletes them. Every frame
    * write on a shared channel therefore has to restate them, whether or not it is about pins at
    * all, which is what carrying them forward here is for. A {@code mapping} in the spec is merged
    * on top rather than replacing them; see {@link #mergePins}.
    *
    * <p>The read side supplies them: {@code VSChartBindingFactory.applyColorsToFrame} populates
    * {@code globalColorMaps} from {@code viewsheet.getDimensionColors(...)} when {@code createModel}
    * builds the model, provided sharing is already on. When it is off that array is empty, and
    * turning sharing on is precisely the transition {@code categorical-color-pane.shareColorsChange}
    * handles by seeding {@code globalColorMaps} from {@code colorMaps} — the frame's own pins
    * become the viewsheet's. Mirrored here for the same reason: the option is meant to change where
    * the pins live, not whether they exist.
    */
   private static void carryShareColorState(CategoricalColorModel built, VisualFrameModel existing,
                                            String fieldName, Map<String, Object> spec)
   {
      String field = existing == null ? null : existing.getField();
      built.setField(field != null ? field : fieldName);

      CategoricalColorModel current =
         existing instanceof CategoricalColorModel model ? model : null;

      // A spec that does not mention shareColors is not asking about the checkbox, so it leaves it
      // where it is -- the way the Composer's Apply does, which posts back whatever
      // CategoricalColorModel(wrapper) read off the frame. Forcing it off here instead meant every
      // colour change silently switched sharing off for a chart that had it on, and the caller had
      // no way to tell from the request that it would.
      //
      // Both flags are carried, not just the one the vocabulary names. They agree on every frame
      // the Composer or this tool has written; a legacy frame where they do not is one neither can
      // repair, so a write that was not asked about them must not normalise them either.
      if(VisualFrameAliases.shareColors(spec) == null && current != null) {
         built.setShareColors(current.isShareColors());
         built.setUseGlobal(current.isUseGlobal());
      }

      if(!built.isUseGlobal()) {
         return;
      }

      // Sharing is on, so the pins the factory reads are the viewsheet-level ones. Anything the
      // caller supplied is in whichever array VisualFrameAliases could reach: it routes on the
      // spec's own shareColors, which is absent in exactly the carried-forward case above.
      ColorMapModel[] supplied = notEmpty(built.getGlobalColorMaps())
         ? built.getGlobalColorMaps() : built.getColorMaps();
      ColorMapModel[] carried = current == null ? null
         : (notEmpty(current.getGlobalColorMaps())
            ? current.getGlobalColorMaps() : current.getColorMaps());

      built.setGlobalColorMaps(mergePins(carried, supplied));
      // The array the factory is not reading must not keep a stale copy: describe() reports from
      // whichever one the flag selects, and two disagreeing copies is how a read starts lying.
      built.setColorMaps(new ColorMapModel[0]);
   }

   /**
    * The channel's existing pins underneath, the caller's on top, keyed by the value they pin.
    *
    * <p>Merged rather than replaced because the two destinations otherwise behave differently for
    * the same request. Unshared pins reach the chart through
    * {@code ColorFrameModelFactory.assignMappedColors}, which calls {@code setColor(value, colour)}
    * on the frame the channel already has — so naming one value leaves every other pin alone, which
    * is what the tool documents. Shared pins instead reach it through
    * {@code Viewsheet.setDimensionColors}, which drops the column's existing keys before putting
    * the new ones — so the same request would unpin everything it did not name. This makes both
    * mean "change the values I name".
    *
    * <p>The Composer has no equivalent problem to solve: its dialog opens pre-filled with the
    * column's current pins, so the array it posts is already the merge.
    */
   private static ColorMapModel[] mergePins(ColorMapModel[] existing, ColorMapModel[] supplied) {
      Map<String, ColorMapModel> merged = new LinkedHashMap<>();

      for(ColorMapModel[] pins : Arrays.asList(existing, supplied)) {
         for(ColorMapModel pin : pins == null ? new ColorMapModel[0] : pins) {
            if(pin != null && pin.getOption() != null) {
               merged.put(pin.getOption(), pin);
            }
         }
      }

      return merged.values().toArray(new ColorMapModel[0]);
   }

   private static boolean notEmpty(ColorMapModel[] maps) {
      return maps != null && maps.length > 0;
   }

   /**
    * Refuses a frame whose kind the channel's bound field cannot drive.
    *
    * <p>{@code color-field-mc}/{@code shape-field-mc}{@code .getEditPaneId()} pick the editor from
    * the bound field, not from the caller: a dimension (or a discrete aggregate, which groups like
    * one) opens the <em>Categorical</em> pane and nothing else; a measure opens the
    * <em>Linear</em> one — the graduated ramps, shape sets and texture sets. Static is offered
    * only when nothing is bound. So a frame outside that set is a state the Composer cannot
    * produce, and the backend, having never had to cope with it, does not.
    *
    * <p>Live repro: on a line chart with a dimension on shape,
    * {@code set_visual_frame channel="line" {type: "static", line: "large dash"}} answered ok and
    * the image did not change — and the categorical {@code lines} array came back with the static
    * value sitting in one of its slots, so the request was neither honoured nor cleanly dropped.
    * The control on another channel behaves the same: {@code {type: "static", color: "#FF0000"}}
    * on a colour channel with a dimension bound rendered the ordinary categorical palette, with no
    * red anywhere.
    *
    * <p>The measure branch is stated as "not static and not categorical" rather than as a list,
    * because the graduated set is different for every family — gradient/palette/heat and the rest
    * on colour, fill/oval/polygon on shape, {@code linear} on size and line, the prebuilt hatchings
    * on texture — and {@link VisualFrameAliases#typeNames} already knows each of them. Naming the
    * survivors in the message keeps the caller from having to guess which family they are in.
    *
    * <p>Node channels are left alone: their editors are the relation chart's own, this rule has
    * not been checked against them, and refusing on a guess would break a path that may well work.
    */
   private static void requireFrameTheFieldCanDrive(String channel, VisualFrameModel frame,
                                                    AestheticInfo field)
   {
      ChartRefModel dataInfo = field.getDataInfo();

      if(AestheticChannels.NODE_CHANNELS.contains(channel) || dataInfo == null) {
         return;
      }

      String type = VisualFrameAliases.typeName(frame);
      String bound = fieldNameOf(field);

      if(rendersByCategory(dataInfo)) {
         if("categorical".equals(type)) {
            return;
         }

         throw new IllegalArgumentException(
            "'" + bound + "' is bound to the " + channel + " channel and groups into categories, " +
            "so only {type: \"categorical\", ...} can drive it — a '" + type + "' frame is stored " +
            "and never rendered, which reports success while the chart does not change. Give a " +
            "categorical frame, or clear the field with clear_aesthetic_field if the intent was " +
            "one fixed value for the whole chart. The Composer opens the categorical pane and no " +
            "other while a dimension is bound here.");
      }

      if(!"static".equals(type) && !"categorical".equals(type)) {
         return;
      }

      List<String> graduated = VisualFrameAliases.typeNames(channel).stream()
         .filter(name -> !"static".equals(name) && !"categorical".equals(name))
         .toList();

      throw new IllegalArgumentException(
         "'" + bound + "' is a measure bound to the " + channel + " channel, so the channel " +
         "renders a graduated scale over its values and a '" + type + "' frame cannot drive it — " +
         "it is stored and never rendered, which reports success while the chart does not change. " +
         "Use one of " + graduated + ", or clear the field with clear_aesthetic_field if the " +
         "intent was one fixed value for the whole chart.");
   }

   /**
    * Whether a bound field makes its channel render one value per category rather than a graduated
    * scale — {@code getEditPaneId}'s own test: anything that is not a measure, plus a discrete
    * aggregate, which is an aggregate asked to group like a dimension.
    */
   private static boolean rendersByCategory(ChartRefModel dataInfo) {
      if(!dataInfo.isMeasure()) {
         return true;
      }

      return dataInfo instanceof ChartAggregateRefModel aggregate && aggregate.isDiscrete();
   }

   /**
    * One entry per measure when the measures do not all render this channel the same way, or
    * {@code null} when they do.
    *
    * <p>{@link #frameOf} reports the first measure's frame, which is the whole truth right up until
    * the measures disagree — and they can now, because {@code setFrame}'s {@code measure} parameter
    * gives one measure its own frame the way the Composer's Combined pane does. Reporting only the
    * first would then describe a chart that is visibly drawing something else beside it, which is
    * the failure this class spends its length avoiding. Emitted only on disagreement so the common
    * chart's read stays as short as it was.
    */
   private static Map<String, Object> divergentMeasureFrames(
      ChartBindingModel model, String channel, AestheticInfo field,
      Collection<String> perMeasureFrameChannels)
   {
      if(field != null || !perMeasureFrameChannels.contains(channel)) {
         return null;
      }

      List<ChartAggregateRefModel> aggregates = aggregates(model);

      if(aggregates.size() < 2) {
         return null;
      }

      Map<String, Object> byMeasure = new LinkedHashMap<>();
      boolean diverges = false;
      Object first = null;

      for(int i = 0; i < aggregates.size(); i++) {
         Object described =
            VisualFrameAliases.describe(aggregateFrameOf(aggregates.get(i), channel));
         byMeasure.put(measureLabel(aggregates.get(i)), described);

         if(i == 0) {
            first = described;
         }
         else if(!Objects.equals(first, described)) {
            diverges = true;
         }
      }

      return diverges ? byMeasure : null;
   }

   /**
    * Refuses a non-static colour frame about to be broadcast to the measures — i.e. a colour frame
    * on a channel with no field bound, on a chart that renders colour per measure.
    *
    * <p>In exactly that configuration {@code VSFrameVisitor.createFrame} takes its
    * {@code supportsFieldFrame()} branch and calls {@code GraphUtil.fixDuplicateColor}, which casts
    * every aggregate's colour frame wrapper straight to {@code StaticColorFrameWrapper} with no
    * {@code instanceof} in front of it. Anything else there is a {@code ClassCastException} at
    * paint time — not a wrong colour, the whole chart stops rendering, and it stays that way
    * because the bad frame is now stored on the measures. {@code set_visual_frame} still answered
    * ok, and so did the {@code clear_aesthetic_field} and {@code set_aesthetic_field} calls that
    * came after it; only the image was gone.
    *
    * <p>The cast is safe for the Composer because the Composer cannot produce the state:
    * {@code color-field-mc.getEditPaneId()} opens {@code CategoricalColor}/{@code LinearColor}
    * only when a field is bound, and falls back to {@code CombinedColor}/{@code StaticColor} —
    * both static — when one is not. This is that same rule, enforced on the path that could
    * otherwise reach the cast.
    *
    * <p>Checked here, at the aggregate broadcast, rather than for every field-less colour frame,
    * because this is the write whose result the cast reads. The other two destinations cannot
    * reach it: a {@code MergedChartInfo} chart (candle, stock, radar, relation, map) answers false
    * for colour, so {@code createFrame} reads {@code getGeneralFrame()} and never enters that
    * branch — a chart-level gradient there is legitimate — and a chart with no measure renders no
    * colour frame at all. Node channels never reach the broadcast either; relation node colour
    * renders through {@code GraphGenerator}'s own node path.
    */
   private static void requireStaticColorOnMeasures(String channel, VisualFrameModel frame) {
      if(!"color".equals(channel) || frame instanceof StaticColorModel) {
         return;
      }

      throw new IllegalArgumentException(
         "The color channel has no field bound, so its frame is the one fixed colour the measures " +
         "are drawn in — only {type: \"static\", color: \"#RRGGBB\"} can go there. A '" +
         VisualFrameAliases.typeName(frame) + "' frame varies colour across categories or a value " +
         "range, which needs something to vary over: bind a field with set_aesthetic_field first, " +
         "then set this frame. (Storing it here does not merely go unrendered — it stops the chart " +
         "rendering at all.) The Composer draws the same line: with nothing on the color shelf its " +
         "swatch opens the static colour picker, never the categorical or gradient pane.");
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

   /**
    * The {@code AestheticInfo} whose frame the renderer reads for this channel, or {@code null}
    * when the channel currently renders from a field-less slot instead.
    *
    * <p>For most channels that is simply the channel's own field. For {@code line} and
    * {@code texture} it is the <b>shape</b> field, and this method exists for that case alone.
    *
    * <p>The Composer has one control where this tool has three channels. The binding pane's Shape
    * slot edits {@code lineFrame} on a chart whose type {@code GraphTypes.supportsLine} answers
    * for, {@code textureFrame} where {@code supportsTexture} does, and {@code shapeFrame}
    * otherwise — one slot, three frame families, picked by chart type
    * ({@code shape-field-mc.getEditPaneId}). So binding a dimension to shape on a line chart puts
    * a {@code CategoricalLineModel} on the <em>shape</em> field, and
    * {@code VSLineFrameStrategy.getAestheticRef} returns that ref precisely because its frame is a
    * {@code LineFrame}. {@code VSFrameVisitor.createFrame} then prefers it over every field-less
    * slot. {@code VSTextureFrameStrategy} is the same method with {@code TextureFrame} in it.
    *
    * <p>Without this, {@code line} and {@code texture} were treated as channels that never have a
    * field, so both the write and the read went to the per-measure slot — which the renderer
    * ignores while the shape field holds one of their frames. Live repro: on a line chart with a
    * dimension on shape, {@code set_visual_frame channel="line" {type: "static", line: "large
    * dash"}} answered ok, round-tripped through {@code get_chart_aesthetics}, and left the image
    * byte-for-byte unchanged; clearing the shape field made the same stored value render at once.
    *
    * <p>The frame-family test is the strategy's own, not "is anything bound to shape". A shape
    * field holding a {@code ShapeFrame} — a point chart — leaves {@code line} and {@code texture}
    * on their field-less slots, which is where that chart does read them from.
    *
    * <p>Multi-aesthetic charts are out of scope here as they are everywhere else in this class:
    * the strategies read {@code aggr.getShapeField()} per measure and ignore the chart-level one,
    * and {@code set_aesthetic_field} already refuses to run on them.
    */
   private static AestheticInfo frameField(ChartBindingModel model, String channel) {
      if(acceptsField(channel)) {
         return read(model, channel);
      }

      if(!"line".equals(channel) && !"texture".equals(channel)) {
         return null;
      }

      AestheticInfo shape = read(model, "shape");
      VisualFrameModel frame = shape == null ? null : shape.getFrame();
      boolean carriesThisFamily = "line".equals(channel)
         ? frame instanceof LineFrameModel : frame instanceof TextureFrameModel;

      return carriesThisFamily ? shape : null;
   }

   private static boolean acceptsFrame(String channel) {
      return AestheticChannels.FRAME_CHANNELS.contains(channel) ||
         AestheticChannels.NODE_CHANNELS.contains(channel);
   }

   private static Map<String, Object> channelView(ChartBindingModel model, String channel,
                                                  Collection<String> perMeasureFrameChannels)
   {
      Map<String, Object> view = new LinkedHashMap<>();
      AestheticInfo info = frameField(model, channel);

      view.put("field", fieldNameOf(info));
      view.put("frame",
               VisualFrameAliases.describe(frameOf(model, channel, perMeasureFrameChannels)));
      view.put("acceptsField", acceptsField(channel));
      view.put("acceptsFrame", acceptsFrame(channel));

      Map<String, Object> perMeasure =
         divergentMeasureFrames(model, channel, info, perMeasureFrameChannels);

      if(perMeasure != null) {
         view.put("framesByMeasure", perMeasure);
      }

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
      AestheticInfo field = frameField(model, channel);

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
