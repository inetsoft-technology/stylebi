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

import inetsoft.uql.viewsheet.graph.GraphTypes;
import inetsoft.web.binding.model.ChartBindingModel;
import inetsoft.web.binding.model.ColorMapModel;
import inetsoft.web.binding.model.graph.*;
import inetsoft.web.binding.model.graph.aesthetic.*;
import inetsoft.web.wiz.binding.model.FieldRef;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@Tag("core")
class ChartAestheticMutatorTest {
   private static FieldRef dimension(String column) {
      return new FieldRef(column, "dimension", null, null, null);
   }

   private static FieldRef measure(String column, String aggregate) {
      return new FieldRef(column, "measure", aggregate, null, null);
   }

   private static Map<String, Object> spec(Object... pairs) {
      Map<String, Object> spec = new LinkedHashMap<>();

      for(int i = 0; i < pairs.length; i += 2) {
         spec.put((String) pairs[i], pairs[i + 1]);
      }

      return spec;
   }

   /**
    * The mirror of {@link ChartBindingMutatorTest}'s preservation test. 2b must not disturb
    * the aesthetics; 2c must not disturb the shelves, chart type or options. Two specs write
    * the same model through the same event, so both directions need the assertion.
    */
   private static Map<String, Object> snapshotNonAesthetics(ChartBindingModel model) {
      Map<String, Object> snapshot = new LinkedHashMap<>();
      snapshot.put("xFields", model.getXFields());
      snapshot.put("yFields", model.getYFields());
      snapshot.put("groupFields", model.getGroupFields());
      snapshot.put("chartType", model.getChartType());
      snapshot.put("multiStyles", model.isMultiStyles());
      snapshot.put("separated", model.isSeparated());
      return snapshot;
   }

   // ── field channels ────────────────────────────────────────────────────────

   @Test
   void bindsADimensionToTheColourChannel() {
      ChartBindingModel model = new ChartBindingModel();

      ChartAestheticMutator.setField(model, "color", dimension("Region"));

      AestheticInfo info = model.getColorField();
      assertNotNull(info);
      assertEquals("Region", info.getFullName());
      assertInstanceOf(ChartDimensionRefModel.class, info.getDataInfo());
   }

   @Test
   void bindsAMeasureToTheSizeChannelCarryingItsAggregate() {
      ChartBindingModel model = new ChartBindingModel();

      ChartAestheticMutator.setField(model, "size", measure("Sales", "Sum"));

      AestheticInfo info = model.getSizeField();
      assertNotNull(info);
      ChartAggregateRefModel ref =
         assertInstanceOf(ChartAggregateRefModel.class, info.getDataInfo());
      assertEquals("Sum", ref.getFormula());
   }

   @Test
   void bindsToTheShapeAndTextChannels() {
      ChartBindingModel model = new ChartBindingModel();

      ChartAestheticMutator.setField(model, "shape", dimension("Category"));
      ChartAestheticMutator.setField(model, "text", dimension("Label"));

      assertNotNull(model.getShapeField());
      assertNotNull(model.getTextField());
   }

   @Test
   void clearingAChannelUnbindsIt() {
      ChartBindingModel model = new ChartBindingModel();
      ChartAestheticMutator.setField(model, "color", dimension("Region"));

      ChartAestheticMutator.clearField(model, "color");

      assertNull(model.getColorField());
   }

   @Test
   void matchesAChannelNameCaseInsensitively() {
      ChartBindingModel model = new ChartBindingModel();

      ChartAestheticMutator.setField(model, "  Color  ", dimension("Region"));

      assertNotNull(model.getColorField());
   }

   // ── frame channels ────────────────────────────────────────────────────────

   @Test
   void setsAStaticColourFrame() {
      ChartBindingModel model = new ChartBindingModel();

      ChartAestheticMutator.setFrame(model, "color", spec("type", "static", "color", "#4e79a7"));

      StaticColorModel frame = assertInstanceOf(StaticColorModel.class, model.getColorFrame());
      assertEquals("#4E79A7", frame.getColor());
   }

   @Test
   void setsANamedPaletteFrame() {
      ChartBindingModel model = new ChartBindingModel();

      ChartAestheticMutator.setFrame(model, "color", spec("type", "palette", "palette", "Blues"));

      assertInstanceOf(BluesColorModel.class, model.getColorFrame());
   }

   /**
    * The render path ({@code VSFrameVisitor.createVisualFrame} -> {@code AestheticRef.getVisualFrame})
    * reads a bound field's own {@code frame} property, not {@code ChartBindingModel.colorFrame} —
    * a frame written only to the top-level property never reaches the chart.
    */
   @Test
   void aFrameSetOnABoundFieldLandsOnTheFieldNotTheTopLevelProperty() {
      ChartBindingModel model = new ChartBindingModel();
      ChartAestheticMutator.setField(model, "color", measure("Sales", "Sum"));

      ChartAestheticMutator.setFrame(
         model, "color", spec("type", "gradient", "from", "#FFFF00", "to", "#8000FF"));

      GradientColorModel onField =
         assertInstanceOf(GradientColorModel.class, model.getColorField().getFrame());
      assertEquals("#FFFF00", onField.getFromColor());
      assertEquals("#8000FF", onField.getToColor());
      assertNull(model.getColorFrame(), "the top-level property must stay untouched");
   }

   @Test
   void aCategoricalFrameSetOnABoundDimensionLandsOnTheFieldNotTheTopLevelProperty() {
      ChartBindingModel model = new ChartBindingModel();
      ChartAestheticMutator.setField(model, "color", dimension("Region"));

      ChartAestheticMutator.setFrame(
         model, "color", spec("type", "categorical", "colors", List.of("#FF0000", "#00FF00")));

      CategoricalColorModel onField =
         assertInstanceOf(CategoricalColorModel.class, model.getColorField().getFrame());
      assertArrayEquals(new String[]{"#FF0000", "#00FF00"}, onField.getColors());
   }

   /**
    * Live-repro of the shadowing defect: a frame set on an unbound channel is stored on
    * {@code ChartBindingModel.colorFrame} (the top-level slot, see {@code setFrame}), not on any
    * {@link AestheticInfo}. Binding a field afterwards must carry that frame onto the field's own
    * {@code AestheticInfo.frame} rather than leaving it null (which {@code GraphUtil.fixVisualFrame}
    * would then fabricate a fresh default frame for at render time).
    */
   @Test
   void aFrameSetBeforeAnyFieldIsBoundCarriesOntoTheFieldWhenOneIsBound() {
      ChartBindingModel model = new ChartBindingModel();
      ChartAestheticMutator.setFrame(
         model, "color", spec("type", "categorical", "colors", List.of("#111111", "#222222")));

      ChartAestheticMutator.setField(model, "color", dimension("Region"));

      CategoricalColorModel onField =
         assertInstanceOf(CategoricalColorModel.class, model.getColorField().getFrame(),
                          "the field's own frame must be the caller's custom frame, not a fabricated default");
      assertArrayEquals(new String[]{"#111111", "#222222"}, onField.getColors());
   }

   /** Rebinding a channel's field must not discard the frame already set on that channel. */
   @Test
   void rebindingAChannelsFieldPreservesItsExistingFrame() {
      ChartBindingModel model = new ChartBindingModel();
      ChartAestheticMutator.setField(model, "color", dimension("Region"));
      ChartAestheticMutator.setFrame(
         model, "color", spec("type", "categorical", "colors", List.of("#333333", "#444444")));

      ChartAestheticMutator.setField(model, "color", dimension("Category"));

      CategoricalColorModel onField =
         assertInstanceOf(CategoricalColorModel.class, model.getColorField().getFrame(),
                          "rebinding the field must preserve the channel's existing frame");
      assertArrayEquals(new String[]{"#333333", "#444444"}, onField.getColors());
   }

   // ── field-less frames target the aggregate slot, not the dead top-level property ──────────

   /**
    * The render path ({@code VSFrameVisitor.createFrame}) builds a field-less colour from each
    * Y/X measure's own {@code getColorFrame()} (Bug 76102) -- {@code ChartBindingModel.colorFrame}
    * is only consulted for {@code MergedChartInfo} charts (Candle/Gantt/Radar/Relation/Map), which
    * {@code DefaultVSChartInfo} -- built for every ordinary chart -- is not. A frame written only
    * to the top-level property round-trips through {@code get_chart_aesthetics} but never renders.
    */
   @Test
   void setsAFieldLessColorFrameOnTheYAxisAggregateNotTheTopLevelProperty() {
      ChartBindingModel model = new ChartBindingModel();
      ChartBindingMutator.setShelf(model, "y", List.of(measure("Sales", "Sum")));

      ChartAestheticMutator.setFrame(model, "color", spec("type", "static", "color", "#cc2222"));

      ChartAggregateRefModel agg = (ChartAggregateRefModel) model.getYFields().get(0);
      StaticColorModel onAggregate = assertInstanceOf(StaticColorModel.class, agg.getColorFrame());
      assertEquals("#CC2222", onAggregate.getColor());
      assertNull(model.getColorFrame(),
                 "the top-level property is dead storage for an ordinary chart -- the render " +
                 "path never reads it, so leaving a value there too would just be a second lie");
   }

   /** {@code line} has {@code acceptsField: false} -- a field-less frame is its only rendering path. */
   @Test
   void setsAFieldLessLineFrameOnTheYAxisAggregate() {
      ChartBindingModel model = new ChartBindingModel();
      ChartBindingMutator.setShelf(model, "y", List.of(measure("Sales", "Sum")));

      ChartAestheticMutator.setFrame(model, "line", spec("type", "static", "line", 3.0));

      ChartAggregateRefModel agg = (ChartAggregateRefModel) model.getYFields().get(0);
      StaticLineModel onAggregate = assertInstanceOf(StaticLineModel.class, agg.getLineFrame());
      assertEquals(3, onAggregate.getLine());
      assertNull(model.getLineFrame());
   }

   /** {@code texture} has {@code acceptsField: false} -- a field-less frame is its only rendering path. */
   @Test
   void setsAFieldLessTextureFrameOnTheYAxisAggregate() {
      ChartBindingModel model = new ChartBindingModel();
      ChartBindingMutator.setShelf(model, "y", List.of(measure("Sales", "Sum")));

      ChartAestheticMutator.setFrame(model, "texture", spec("type", "static", "texture", 2.0));

      ChartAggregateRefModel agg = (ChartAggregateRefModel) model.getYFields().get(0);
      StaticTextureModel onAggregate =
         assertInstanceOf(StaticTextureModel.class, agg.getTextureFrame());
      assertEquals(2, onAggregate.getTexture());
      assertNull(model.getTextureFrame());
   }

   @Test
   void setsAFieldLessShapeFrameOnTheYAxisAggregate() {
      ChartBindingModel model = new ChartBindingModel();
      ChartBindingMutator.setShelf(model, "y", List.of(measure("Sales", "Sum")));

      ChartAestheticMutator.setFrame(model, "shape", spec("type", "static", "shape", "triangle"));

      ChartAggregateRefModel agg = (ChartAggregateRefModel) model.getYFields().get(0);
      StaticShapeModel onAggregate = assertInstanceOf(StaticShapeModel.class, agg.getShapeFrame());
      assertEquals("triangle", onAggregate.getShape());
      assertNull(model.getShapeFrame());
   }

   @Test
   void setsAFieldLessSizeFrameOnTheYAxisAggregate() {
      ChartBindingModel model = new ChartBindingModel();
      ChartBindingMutator.setShelf(model, "y", List.of(measure("Sales", "Sum")));

      ChartAestheticMutator.setFrame(model, "size", spec("type", "static", "size", 5.0));

      ChartAggregateRefModel agg = (ChartAggregateRefModel) model.getYFields().get(0);
      StaticSizeModel onAggregate = assertInstanceOf(StaticSizeModel.class, agg.getSizeFrame());
      assertEquals(5.0, onAggregate.getSize(), 0.0);
      assertNull(model.getSizeFrame());
   }

   /**
    * {@code set_visual_frame} has no way to name a single measure -- it is scoped to
    * {@code {assembly, channel}}, not a specific Y-axis aggregate. Broadcasting to every measure
    * matches the tool's existing chart-level semantics and does not silently apply the caller's
    * frame to only one of several measures.
    */
   @Test
   void broadcastsAFieldLessFrameToEveryYAxisAggregate() {
      ChartBindingModel model = new ChartBindingModel();
      ChartBindingMutator.setShelf(
         model, "y", List.of(measure("Sales", "Sum"), measure("Profit", "Sum")));

      ChartAestheticMutator.setFrame(model, "color", spec("type", "static", "color", "#00ff00"));

      for(ChartRefModel ref : model.getYFields()) {
         ChartAggregateRefModel agg = (ChartAggregateRefModel) ref;
         StaticColorModel onAggregate =
            assertInstanceOf(StaticColorModel.class, agg.getColorFrame());
         assertEquals("#00FF00", onAggregate.getColor());
      }
   }

   /**
    * {@code VSFrameVisitor.containsMeasure} despite its name tests only the <em>last</em> ref on
    * the shelf, so a dimension after a measure on Y sends the renderer to X however many measures
    * Y holds. Choosing Y here because it "has an aggregate somewhere" would write the frame to
    * {@code Sum(Sales)} while the chart renders from {@code Sum(Profit)}.
    */
   @Test
   void aDimensionLastOnYSendsTheFrameToTheXAggregateTheRendererUses() {
      ChartBindingModel model = new ChartBindingModel();
      ChartBindingMutator.setShelf(
         model, "y", List.of(measure("Sales", "Sum"), dimension("Region")));
      ChartBindingMutator.setShelf(model, "x", List.of(measure("Profit", "Sum")));

      ChartAestheticMutator.setFrame(model, "color", spec("type", "static", "color", "#cc2222"));

      ChartAggregateRefModel onX = (ChartAggregateRefModel) model.getXFields().get(0);
      assertInstanceOf(StaticColorModel.class, onX.getColorFrame(),
                       "the renderer reads X when Y does not end in a measure");
      ChartAggregateRefModel onY = (ChartAggregateRefModel) model.getYFields().get(0);
      assertNull(onY.getColorFrame(),
                 "and Y's own measure is not one of the refs it combines");
      assertNull(model.getColorFrame());
   }

   /**
    * {@code getAggregates(ChartRef...)} walks backwards and breaks at the first non-measure, so it
    * sees only the trailing contiguous run. Writing the whole shelf would put the frame on a
    * measure the chart ignores, and — the half that actually shows — {@code frameOf} would report
    * that measure's frame rather than the one being rendered.
    */
   @Test
   void onlyTheTrailingRunOfMeasuresIsTargeted() {
      ChartBindingModel model = new ChartBindingModel();
      ChartBindingMutator.setShelf(
         model, "y",
         List.of(measure("Sales", "Sum"), dimension("Region"), measure("Profit", "Sum")));

      ChartAestheticMutator.setFrame(model, "color", spec("type", "static", "color", "#123456"));

      ChartAggregateRefModel leading = (ChartAggregateRefModel) model.getYFields().get(0);
      ChartAggregateRefModel trailing = (ChartAggregateRefModel) model.getYFields().get(2);
      assertNull(leading.getColorFrame(),
                 "the dimension between them ends the run the renderer walks");
      assertInstanceOf(StaticColorModel.class, trailing.getColorFrame());

      @SuppressWarnings("unchecked")
      Map<String, Object> color =
         (Map<String, Object>) ChartAestheticMutator.describe(model).get("color");
      @SuppressWarnings("unchecked")
      Map<String, Object> frame = (Map<String, Object>) color.get("frame");
      assertEquals("#123456", frame.get("color"),
                   "and the read reports the measure that renders, not the shelf's first");
   }

   /**
    * {@code ChartAggregateRef.isMeasure()} is {@code !isDiscrete()}, so a discrete aggregate ends
    * the run and fails the last-ref test just as a dimension does. With it last on Y the renderer
    * finds no aggregate at all and {@code createFrame()} returns null — so there is nothing to
    * target, and the write falls back to the chart-level slot the read also consults.
    */
   @Test
   void aDiscreteAggregateIsNotAMeasureForThisPurpose() {
      ChartBindingModel model = new ChartBindingModel();
      ChartBindingMutator.setShelf(
         model, "y", List.of(measure("Sales", "Sum"), measure("Quantity", "Sum")));
      ((ChartAggregateRefModel) model.getYFields().get(1)).setDiscrete(true);

      ChartAestheticMutator.setFrame(model, "color", spec("type", "static", "color", "#cc2222"));

      assertNull(((ChartAggregateRefModel) model.getYFields().get(0)).getColorFrame());
      assertNull(((ChartAggregateRefModel) model.getYFields().get(1)).getColorFrame());
      assertInstanceOf(StaticColorModel.class, model.getColorFrame(),
                       "no aggregate to target, so the chart-level slot is where write and read " +
                       "can at least agree");
   }

   /**
    * The Gantt branch goes through the same backwards walk, so a milestone bound to something that
    * is not a measure hides the start field from the renderer too — and must hide it from the
    * write for the same reason.
    */
   @Test
   void aNonMeasureMilestoneEndsTheGanttRunBeforeTheStartField() {
      ChartBindingModel model = new ChartBindingModel();
      model.setChartType(GraphTypes.CHART_GANTT);
      ChartBindingMutator.setSingleShelf(model, "start", measure("Ship Date", "Max"));
      ChartBindingMutator.setSingleShelf(model, "milestone", dimension("Phase"));

      ChartAestheticMutator.setFrame(model, "color", spec("type", "static", "color", "#cc2222"));

      ChartAggregateRefModel start = (ChartAggregateRefModel) model.getStartField();
      assertNull(start.getColorFrame(),
                 "getAggregates() breaks at milestone before it ever reaches start");
      assertInstanceOf(StaticColorModel.class, model.getColorFrame());
   }

   /**
    * Mirrors {@code VSFrameVisitor.getAggregates()}'s own fallback: when the Y shelf has no
    * measure (e.g. an inverted chart with the aggregate on X), a field-less frame must still land
    * where the renderer looks, not on the top-level property.
    */
   @Test
   void setsAFieldLessFrameOnTheXAxisAggregateWhenYHasNone() {
      ChartBindingModel model = new ChartBindingModel();
      ChartBindingMutator.setShelf(model, "x", List.of(measure("Sales", "Sum")));

      ChartAestheticMutator.setFrame(model, "color", spec("type", "static", "color", "#cc2222"));

      ChartAggregateRefModel agg = (ChartAggregateRefModel) model.getXFields().get(0);
      StaticColorModel onAggregate = assertInstanceOf(StaticColorModel.class, agg.getColorFrame());
      assertEquals("#CC2222", onAggregate.getColor());
      assertNull(model.getColorFrame());
   }

   /** With no Y/X measure at all (e.g. a contour chart), the top-level property is genuinely what renders. */
   @Test
   void setsAFieldLessFrameOnTheTopLevelPropertyWhenThereIsNoAggregateToTarget() {
      ChartBindingModel model = new ChartBindingModel();

      ChartAestheticMutator.setFrame(model, "color", spec("type", "static", "color", "#cc2222"));

      StaticColorModel frame = assertInstanceOf(StaticColorModel.class, model.getColorFrame());
      assertEquals("#CC2222", frame.getColor());
   }

   /**
    * {@code get_chart_aesthetics} must report what will actually render, not the dead top-level
    * property -- reading the wrong slot here would be a second, independent lie on top of the
    * write-side defect (Bug 76102's second-order finding).
    */
   @Test
   void describesAFieldLessFrameFromTheAggregateSlotItActuallyRenders() {
      ChartBindingModel model = new ChartBindingModel();
      ChartBindingMutator.setShelf(model, "y", List.of(measure("Sales", "Sum")));
      ChartAestheticMutator.setFrame(model, "color", spec("type", "static", "color", "#123456"));

      @SuppressWarnings("unchecked")
      Map<String, Object> color =
         (Map<String, Object>) ChartAestheticMutator.describe(model).get("color");
      @SuppressWarnings("unchecked")
      Map<String, Object> frame = (Map<String, Object>) color.get("frame");

      assertEquals("static", frame.get("type"));
      assertEquals("#123456", frame.get("color"));
   }

   // ── ...but only on the chart types whose renderer reads that slot ─────────

   /**
    * The broadcast above is right for an ordinary {@code DefaultVSChartInfo} chart and wrong for
    * the ones whose {@code VSFrameVisitor} strategy answers {@code supportsFieldFrame()} false:
    * {@code MergedVSChartInfo} (candle, stock, relation, map) for colour and shape,
    * {@code RadarVSChartInfo} also for size, and {@code AbstractChartInfo} for all three on a
    * contour chart. There {@code createFrame()} falls through to {@code getGeneralFrame()}, which
    * reads the chart-level property — so writing the aggregate slot would put the frame somewhere
    * nothing renders from, the same defect the aggregate branch exists to fix, mirrored.
    *
    * <p>{@code ChartAestheticAgentService} asks the real {@code VSChartInfo} which channels those
    * are; this pins that the mutator honours the answer instead of assuming all five.
    */
   @Test
   void writesAFieldLessFrameToTheChartLevelSlotWhenThisChartTypeDoesNotUsePerMeasureFrames() {
      ChartBindingModel model = new ChartBindingModel();
      ChartBindingMutator.setShelf(model, "y", List.of(measure("Sales", "Sum")));

      // What perMeasureFrameChannels() returns for a candle/stock chart: colour and shape (and
      // therefore line/texture) read the chart-level slot, size reads the per-measure one.
      ChartAestheticMutator.setFrame(model, "color", spec("type", "static", "color", "#cc2222"),
                                     false, Set.of("size"));

      StaticColorModel onModel = assertInstanceOf(StaticColorModel.class, model.getColorFrame(),
         "a chart type whose renderer reads the chart-level colour slot must be written there");
      assertEquals("#CC2222", onModel.getColor());

      ChartAggregateRefModel agg = (ChartAggregateRefModel) model.getYFields().get(0);
      assertNull(agg.getColorFrame(),
                 "and not also into the per-measure slot this chart type never reads");
   }

   /** The channel that <em>is</em> per-measure on the same chart still goes to the aggregate. */
   @Test
   void stillWritesThePerMeasureChannelsThatThisChartTypeDoesUse() {
      ChartBindingModel model = new ChartBindingModel();
      ChartBindingMutator.setShelf(model, "y", List.of(measure("Sales", "Sum")));

      ChartAestheticMutator.setFrame(model, "size", spec("type", "static", "size", 5.0),
                                     false, Set.of("size"));

      ChartAggregateRefModel agg = (ChartAggregateRefModel) model.getYFields().get(0);
      assertInstanceOf(StaticSizeModel.class, agg.getSizeFrame());
      assertNull(model.getSizeFrame());
   }

   /**
    * A Gantt chart answers {@code true} to all three {@code supports*FieldFrame()} predicates, so
    * it takes the per-measure branch — but its measures are not on X or Y. {@code
    * ChangeChartTypeProcessor.copyToGantt} routes every measure onto {@code startField}'s own
    * aesthetic channels and every leftover dimension onto Y, and {@code
    * VSFrameVisitor.getAggregates()} reads start/milestone for exactly that reason. Targeting the
    * Y shelf here would find only dimensions, fall through to the chart-level slot, and land the
    * frame where a Gantt chart never reads it — the same defect the per-measure branch exists to
    * fix, one chart type further along.
    */
   @Test
   void setsAFieldLessFrameOnAGanttChartsStartFieldNotItsYShelf() {
      ChartBindingModel model = new ChartBindingModel();
      model.setChartType(GraphTypes.CHART_GANTT);
      ChartBindingMutator.setSingleShelf(model, "start", measure("Ship Date", "Max"));
      ChartBindingMutator.setShelf(model, "y", List.of(dimension("Task")));

      ChartAestheticMutator.setFrame(model, "color", spec("type", "static", "color", "#cc2222"));

      ChartAggregateRefModel start = (ChartAggregateRefModel) model.getStartField();
      StaticColorModel onStart = assertInstanceOf(StaticColorModel.class, start.getColorFrame());
      assertEquals("#CC2222", onStart.getColor());
      assertNull(model.getColorFrame(),
                 "the chart-level slot is dead for Gantt too: supportsColorFieldFrame() is true, " +
                 "so createFrame() never reaches getGeneralFrame()");
   }

   /** The read side of the same, so get_chart_aesthetics reports what a Gantt chart renders. */
   @Test
   void describesAGanttChartsFieldLessFrameFromItsStartField() {
      ChartBindingModel model = new ChartBindingModel();
      model.setChartType(GraphTypes.CHART_GANTT);
      ChartBindingMutator.setSingleShelf(model, "start", measure("Ship Date", "Max"));
      ChartAestheticMutator.setFrame(model, "color", spec("type", "static", "color", "#123456"));

      @SuppressWarnings("unchecked")
      Map<String, Object> color =
         (Map<String, Object>) ChartAestheticMutator.describe(model).get("color");
      @SuppressWarnings("unchecked")
      Map<String, Object> frame = (Map<String, Object>) color.get("frame");

      assertEquals("static", frame.get("type"));
      assertEquals("#123456", frame.get("color"));
   }

   /** The read has to be given the same set, or get_chart_aesthetics reports the dead slot. */
   @Test
   void describesAFieldLessFrameFromTheChartLevelSlotForTheSameChartType() {
      ChartBindingModel model = new ChartBindingModel();
      ChartBindingMutator.setShelf(model, "y", List.of(measure("Sales", "Sum")));
      ChartAestheticMutator.setFrame(model, "color", spec("type", "static", "color", "#123456"),
                                     false, Set.of("size"));

      @SuppressWarnings("unchecked")
      Map<String, Object> color = (Map<String, Object>)
         ChartAestheticMutator.describe(model, false, Set.of("size")).get("color");
      @SuppressWarnings("unchecked")
      Map<String, Object> frame = (Map<String, Object>) color.get("frame");

      assertEquals("static", frame.get("type"));
      assertEquals("#123456", frame.get("color"));
   }

   // ── preservation, both ways ───────────────────────────────────────────────

   @Test
   void aFieldBindingLeavesTheNonAestheticModelUntouched() {
      ChartBindingModel model = new ChartBindingModel();
      ChartBindingMutator.setShelf(model, "x", List.of(dimension("Region")));
      Map<String, Object> before = snapshotNonAesthetics(model);

      ChartAestheticMutator.setField(model, "color", dimension("Category"));

      assertEquals(before, snapshotNonAesthetics(model),
                   "an aesthetic write must not disturb the shelves spec 2b owns");
   }

   @Test
   void aFrameWriteLeavesTheNonAestheticModelUntouched() {
      ChartBindingModel model = new ChartBindingModel();
      ChartBindingMutator.setShelf(model, "y", List.of(measure("Sales", "Sum")));
      Map<String, Object> before = snapshotNonAesthetics(model);

      ChartAestheticMutator.setFrame(model, "color", spec("type", "static", "color", "#000"));

      assertEquals(before, snapshotNonAesthetics(model));
   }

   @Test
   void writingOneAestheticChannelLeavesTheOthersUntouched() {
      ChartBindingModel model = new ChartBindingModel();
      ChartAestheticMutator.setField(model, "shape", dimension("Category"));
      AestheticInfo shapeBefore = model.getShapeField();

      ChartAestheticMutator.setField(model, "color", dimension("Region"));

      assertSame(shapeBefore, model.getShapeField(),
                 "writing the colour channel must not replace the shape channel");
   }

   // ── refusals ──────────────────────────────────────────────────────────────

   @Test
   void refusesAFieldOnAChannelThatOnlyTakesAFrame() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> ChartAestheticMutator.setField(new ChartBindingModel(), "texture",
                                              dimension("Region")));

      assertTrue(thrown.getMessage().contains("texture"));
      assertTrue(thrown.getMessage().contains("set_visual_frame"));
   }

   @Test
   void refusesAnUnknownChannel() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> ChartAestheticMutator.setField(new ChartBindingModel(), "colour",
                                              dimension("Region")));

      assertTrue(thrown.getMessage().contains("colour"));
   }

   @Test
   void refusesAFieldWithNoType() {
      assertThrows(IllegalArgumentException.class,
                   () -> ChartAestheticMutator.setField(
                      new ChartBindingModel(), "color",
                      new FieldRef("Region", null, null, null, null)));
   }

   @Test
   void refusesANullField() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> ChartAestheticMutator.setField(new ChartBindingModel(), "color", null));

      assertTrue(thrown.getMessage().contains("field"));
   }

   // ── the read direction ────────────────────────────────────────────────────

   @Test
   void describesTheBoundChannelsInTheAgentVocabulary() {
      ChartBindingModel model = new ChartBindingModel();
      ChartAestheticMutator.setField(model, "color", measure("Sales", "Sum"));
      ChartAestheticMutator.setFrame(model, "color", spec("type", "palette", "palette", "Reds"));

      Map<String, Object> described = ChartAestheticMutator.describe(model);
      @SuppressWarnings("unchecked")
      Map<String, Object> color = (Map<String, Object>) described.get("color");

      assertEquals("Sales", color.get("field"));
      @SuppressWarnings("unchecked")
      Map<String, Object> frame = (Map<String, Object>) color.get("frame");
      assertEquals("palette", frame.get("type"));
      assertEquals("Reds", frame.get("palette"));
   }

   /**
    * The read path builds its own {@link AestheticInfo} and never sets {@code fullName} —
    * {@code AestheticRefModelFactory.createAestheticInfo} sets only {@code dataInfo} and
    * {@code frame}. So reading the channel name off {@code AestheticInfo.getFullName()} reported
    * {@code field: null} for every channel of every chart, even while the aesthetic was visibly
    * rendering its legend.
    *
    * <p>The test above this one could not catch it: it builds the model with
    * {@code ChartAestheticMutator.setField}, the one place that <em>does</em> set {@code fullName},
    * so it round-trips through our own writer and never exercises a real read. This one builds the
    * model the way the read path does — {@code dataInfo} populated, {@code fullName} absent.
    */
   @Test
   void describesAChannelBuiltTheWayTheReadPathBuildsIt() {
      ChartDimensionRefModel dataInfo = new ChartDimensionRefModel();
      dataInfo.setFullName("Region");

      AestheticInfo info = new AestheticInfo();
      info.setDataInfo(dataInfo);   // exactly what createAestheticInfo does — no setFullName

      ChartBindingModel model = new ChartBindingModel();
      model.setColorField(info);

      @SuppressWarnings("unchecked")
      Map<String, Object> color =
         (Map<String, Object>) ChartAestheticMutator.describe(model).get("color");

      assertEquals("Region", color.get("field"),
                   "a bound channel must report its field, not null");
   }

   /** A bound channel whose name cannot be resolved must not be indistinguishable from an empty one. */
   @Test
   void prefersTheDataInfoNameOverAStaleFullName() {
      ChartDimensionRefModel dataInfo = new ChartDimensionRefModel();
      dataInfo.setFullName("Region");

      AestheticInfo info = new AestheticInfo();
      info.setFullName("Stale");
      info.setDataInfo(dataInfo);

      ChartBindingModel model = new ChartBindingModel();
      model.setColorField(info);

      @SuppressWarnings("unchecked")
      Map<String, Object> color =
         (Map<String, Object>) ChartAestheticMutator.describe(model).get("color");

      assertEquals("Region", color.get("field"));
   }

   @Test
   void describesAnUnboundChannelWithNulls() {
      Map<String, Object> described = ChartAestheticMutator.describe(new ChartBindingModel());
      @SuppressWarnings("unchecked")
      Map<String, Object> shape = (Map<String, Object>) described.get("shape");

      assertNull(shape.get("field"));
      assertNull(shape.get("frame"));
   }

   @Test
   void describesEveryChannelSoAnAgentSeesWhatIsAvailable() {
      Map<String, Object> described = ChartAestheticMutator.describe(new ChartBindingModel());

      for(String channel : AestheticChannels.FIELD_CHANNELS) {
         assertTrue(described.containsKey(channel), "missing channel " + channel);
      }

      for(String channel : AestheticChannels.FRAME_CHANNELS) {
         assertTrue(described.containsKey(channel), "missing channel " + channel);
      }
   }

   // ── node channels (spec 2c Phase 3) ───────────────────────────────────────

   @Test
   void bindsAFieldToTheNodeColorChannelOnARelationChart() {
      ChartBindingModel model = new ChartBindingModel();

      ChartAestheticMutator.setField(model, "node-color", dimension("Region"), true);

      AestheticInfo info = model.getNodeColorField();
      assertNotNull(info);
      assertEquals("Region", info.getFullName());
      // The regular color channel is a separate property -- binding the node channel must not
      // also populate it, or the two would be indistinguishable to a later read.
      assertNull(model.getColorField());
   }

   @Test
   void bindsAFieldToTheNodeSizeChannelOnARelationChart() {
      ChartBindingModel model = new ChartBindingModel();

      ChartAestheticMutator.setField(model, "node-size", measure("Weight", "Sum"), true);

      assertNotNull(model.getNodeSizeField());
      assertNull(model.getSizeField());
   }

   @Test
   void refusesTheNodeColorChannelWhenTheChartIsNotARelationChart() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> ChartAestheticMutator.setField(new ChartBindingModel(), "node-color",
                                              dimension("Region"), false));

      assertTrue(thrown.getMessage().contains("relation"));
   }

   /** The 3-arg overload every existing caller uses defaults to non-relation. */
   @Test
   void theThreeArgOverloadRefusesNodeChannelsByDefault() {
      assertThrows(
         IllegalArgumentException.class,
         () -> ChartAestheticMutator.setField(new ChartBindingModel(), "node-color",
                                              dimension("Region")));
   }

   @Test
   void clearingTheNodeColorChannelUnbindsItWithoutTouchingColor() {
      ChartBindingModel model = new ChartBindingModel();
      ChartAestheticMutator.setField(model, "color", dimension("Region"));
      ChartAestheticMutator.setField(model, "node-color", dimension("Category"), true);

      ChartAestheticMutator.clearField(model, "node-color", true);

      assertNull(model.getNodeColorField());
      assertNotNull(model.getColorField(), "clearing node-color must not clear color");
   }

   @Test
   void setsANodeColorFrameOnARelationChart() {
      ChartBindingModel model = new ChartBindingModel();

      ChartAestheticMutator.setFrame(model, "node-color",
                                     spec("type", "static", "color", "#4e79a7"), true);

      assertNotNull(model.getNodeColorFrame());
      assertNull(model.getColorFrame());
   }

   @Test
   void describesNodeChannelsOnlyWhenAskedToOnARelationChart() {
      ChartBindingModel model = new ChartBindingModel();
      ChartAestheticMutator.setField(model, "node-color", dimension("Region"), true);

      Map<String, Object> notRelation = ChartAestheticMutator.describe(model);
      Map<String, Object> relation = ChartAestheticMutator.describe(model, true);

      assertFalse(notRelation.containsKey("node-color"),
                  "a non-relation read must not advertise a channel this chart cannot render");
      assertTrue(relation.containsKey("node-color"));
      @SuppressWarnings("unchecked")
      Map<String, Object> nodeColor = (Map<String, Object>) relation.get("node-color");
      assertEquals("Region", nodeColor.get("field"));
      assertEquals(true, nodeColor.get("acceptsField"));
      assertEquals(true, nodeColor.get("acceptsFrame"));
   }

   // ── a mapping-only categorical colour frame keeps the channel's palette ───
   //
   // Live repro: with a dimension bound to colour, {type: "categorical", mapping: {...}} was
   // accepted, reported success, and changed nothing — get_chart_aesthetics read back
   // mapping: {} with useGlobal flipped back to true and the chart kept its old colours.
   // CategoricalColorFrameModelFactory.updateVisualFrameWrapper0 returns early, before
   // assignMappedColors/setUseGlobal/setShareColors, when the model carries no colours. The
   // interactive pane always satisfies that precondition (it sends the whole palette alongside
   // the colour maps); the agent path did not. Carrying the channel's current palette is what
   // makes the request the same shape the dialog sends.

   @Test
   void aMappingOnlyColourFrameCarriesTheChannelsCurrentPalette() {
      ChartBindingModel model = new ChartBindingModel();
      ChartAestheticMutator.setField(model, "color", dimension("Region"));
      ChartAestheticMutator.setFrame(
         model, "color", spec("type", "categorical", "colors", List.of("#111111", "#222222")));

      ChartAestheticMutator.setFrame(
         model, "color", spec("type", "categorical", "mapping", Map.of("East", "#d64541")));

      CategoricalColorModel onField =
         assertInstanceOf(CategoricalColorModel.class, model.getColorField().getFrame());
      assertArrayEquals(new String[]{ "#111111", "#222222" }, onField.getColors(),
                        "without colours the factory discards the whole frame, mapping included");
      assertEquals(1, onField.getColorMaps().length);
      assertEquals("East", onField.getColorMaps()[0].getOption());
   }

   /** The same carry-forward on an unbound channel, whose frame lives in a different slot. */
   @Test
   void aMappingOnlyColourFrameCarriesTheChartLevelPaletteToo() {
      ChartBindingModel model = new ChartBindingModel();
      ChartAestheticMutator.setFrame(
         model, "color", spec("type", "categorical", "colors", List.of("#333333")));

      ChartAestheticMutator.setFrame(
         model, "color", spec("type", "categorical", "mapping", Map.of("West", "#f28e2c")));

      CategoricalColorModel frame =
         assertInstanceOf(CategoricalColorModel.class, model.getColorFrame());
      assertArrayEquals(new String[]{ "#333333" }, frame.getColors());
   }

   /** An explicit colours list is the caller's, not something to overwrite with the old palette. */
   @Test
   void anExplicitColourListIsNotOverwrittenByTheCarryForward() {
      ChartBindingModel model = new ChartBindingModel();
      ChartAestheticMutator.setFrame(
         model, "color", spec("type", "categorical", "colors", List.of("#111111", "#222222")));

      ChartAestheticMutator.setFrame(
         model, "color", spec("type", "categorical", "colors", List.of("#999999")));

      assertArrayEquals(new String[]{ "#999999" },
                        ((CategoricalColorModel) model.getColorFrame()).getColors());
   }

   /**
    * With no categorical palette on the channel there are no categories a mapping could name.
    * The Composer hides "Assign Fixed Mapping" in exactly that case; this says so out loud
    * rather than storing something the factory will drop.
    */
   @Test
   void refusesAMappingOnlyFrameWhenTheChannelHasNoPaletteToPinAgainst() {
      ChartBindingModel model = new ChartBindingModel();
      ChartAestheticMutator.setFrame(model, "color", spec("type", "static", "color", "#4e79a7"));

      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> ChartAestheticMutator.setFrame(
            model, "color", spec("type", "categorical", "mapping", Map.of("East", "#d64541"))));

      assertTrue(thrown.getMessage().contains("mapping"), thrown.getMessage());
   }

   /**
    * The carry-forward is colour-only. Line and texture have no equivalent precondition —
    * CategoricalLineFrameModelFactory leaves the wrapper's own defaults in place for an empty
    * array instead of discarding the frame — so a bare categorical line frame still means "the
    * defaults", not "whatever was there before".
    */
   @Test
   void theCarryForwardLeavesOtherChannelsAlone() {
      ChartBindingModel model = new ChartBindingModel();
      ChartAestheticMutator.setFrame(
         model, "line", spec("type", "categorical", "lines", List.of(4097, 4113)));
      ChartAestheticMutator.setFrame(model, "line", spec("type", "categorical"));

      assertArrayEquals(new int[0],
                        ((CategoricalLineModel) model.getLineFrame()).getLines(),
                        "a bare categorical line frame means the defaults, not the old lines");
   }

   // ── line and texture render off the shape field, when it holds their frame ─
   //
   // The Composer has one Shape slot where this tool has three channels: it edits lineFrame on a
   // chart GraphTypes.supportsLine answers for, textureFrame where supportsTexture does, and
   // shapeFrame otherwise. So a dimension on shape puts a CategoricalLineModel on the SHAPE field,
   // and VSLineFrameStrategy.getAestheticRef returns that ref exactly because its frame is a
   // LineFrame -- createFrame then prefers it over every field-less slot.
   //
   // Live repro: line chart, dimension on shape, set_visual_frame channel="line"
   // {type: "static", line: "large dash"} answered ok, round-tripped through
   // get_chart_aesthetics, and left the image byte-for-byte unchanged. Clearing the shape field
   // made the same stored value render at once.

   @Test
   void aLineFrameLandsOnTheShapeFieldWhenThatFieldCarriesALineFrame() {
      ChartBindingModel model = new ChartBindingModel();
      ChartBindingMutator.setShelf(model, "y", List.of(measure("Sales", "Sum")));
      ChartAestheticMutator.setField(model, "shape", dimension("Year(ORDER_DATE)"));
      model.getShapeField().setFrame(new CategoricalLineModel());

      ChartAestheticMutator.setFrame(
         model, "line", spec("type", "categorical", "lines", List.of(4241, 4097)));

      CategoricalLineModel onShape = assertInstanceOf(
         CategoricalLineModel.class, model.getShapeField().getFrame(),
         "the shape field's frame is the one VSLineFrameStrategy hands the renderer");
      assertArrayEquals(new int[]{ 4241, 4097 }, onShape.getLines());
      ChartAggregateRefModel agg = (ChartAggregateRefModel) model.getYFields().get(0);
      assertNull(agg.getLineFrame(),
                 "writing the per-measure slot too would leave a second value nothing renders");
   }

   @Test
   void aTextureFrameLandsOnTheShapeFieldWhenThatFieldCarriesATextureFrame() {
      ChartBindingModel model = new ChartBindingModel();
      ChartBindingMutator.setShelf(model, "y", List.of(measure("Sales", "Sum")));
      ChartAestheticMutator.setField(model, "shape", dimension("Year(ORDER_DATE)"));
      model.getShapeField().setFrame(new CategoricalTextureModel());

      ChartAestheticMutator.setFrame(
         model, "texture", spec("type", "categorical", "textures", List.of(19, 12)));

      CategoricalTextureModel onShape = assertInstanceOf(
         CategoricalTextureModel.class, model.getShapeField().getFrame());
      assertArrayEquals(new int[]{ 19, 12 }, onShape.getTextures());
   }

   /** The read has to come from the same slot, or it reports a value the chart never shows. */
   @Test
   void theLineChannelReportsTheShapeFieldsFrameAndField() {
      ChartBindingModel model = new ChartBindingModel();
      ChartBindingMutator.setShelf(model, "y", List.of(measure("Sales", "Sum")));
      ChartAestheticMutator.setField(model, "shape", dimension("Year(ORDER_DATE)"));
      CategoricalLineModel onShape = new CategoricalLineModel();
      onShape.setLines(new int[]{ 4097, 4113 });
      model.getShapeField().setFrame(onShape);

      @SuppressWarnings("unchecked")
      Map<String, Object> line =
         (Map<String, Object>) ChartAestheticMutator.describe(model).get("line");
      @SuppressWarnings("unchecked")
      Map<String, Object> frame = (Map<String, Object>) line.get("frame");

      assertEquals("Year(ORDER_DATE)", line.get("field"));
      assertEquals(List.of(4097, 4113), frame.get("lines"));
      assertEquals(false, line.get("acceptsField"),
                   "the field goes on shape, so line still refuses set_aesthetic_field");
   }

   /**
    * The gate is the strategy's own frame-family test, not "is anything bound to shape". A point
    * chart's shape field holds a ShapeFrame, and there line and texture really do render from
    * their field-less slots.
    */
   @Test
   void aShapeFieldHoldingAShapeFrameLeavesLineOnItsFieldLessSlot() {
      ChartBindingModel model = new ChartBindingModel();
      ChartBindingMutator.setShelf(model, "y", List.of(measure("Sales", "Sum")));
      ChartAestheticMutator.setField(model, "shape", dimension("Year(ORDER_DATE)"));
      model.getShapeField().setFrame(new CategoricalShapeModel());

      ChartAestheticMutator.setFrame(model, "line", spec("type", "static", "line", 4241));

      ChartAggregateRefModel agg = (ChartAggregateRefModel) model.getYFields().get(0);
      assertInstanceOf(StaticLineModel.class, agg.getLineFrame());
      assertInstanceOf(CategoricalShapeModel.class, model.getShapeField().getFrame(),
                       "the shape field's own frame is not line's to overwrite");
   }

   /** With nothing on shape, both keep their existing field-less behaviour. */
   @Test
   void anUnboundShapeChannelLeavesLineAndTextureOnTheMeasures() {
      ChartBindingModel model = new ChartBindingModel();
      ChartBindingMutator.setShelf(model, "y", List.of(measure("Sales", "Sum")));

      ChartAestheticMutator.setFrame(model, "line", spec("type", "static", "line", 4241));
      ChartAestheticMutator.setFrame(model, "texture", spec("type", "static", "texture", 5));

      ChartAggregateRefModel agg = (ChartAggregateRefModel) model.getYFields().get(0);
      assertInstanceOf(StaticLineModel.class, agg.getLineFrame());
      assertInstanceOf(StaticTextureModel.class, agg.getTextureFrame());
   }

   // ── a bound field decides which kind of frame can drive its channel ───────
   //
   // getEditPaneId() picks the editor from the bound field, not from the caller: a dimension (or a
   // discrete aggregate) opens the Categorical pane and nothing else, a measure opens the Linear
   // one, and Static is offered only when nothing is bound. Anything else is a state the Composer
   // cannot produce and the backend does not cope with.
   //
   // Live repro: line chart, dimension on shape, set_visual_frame channel="line"
   // {type: "static", line: "large dash"} answered ok, the image did not change, and the
   // categorical lines array came back with the static value sitting in one of its slots.
   // Control on another channel: {type: "static", color: "#FF0000"} on a colour channel with a
   // dimension bound rendered the ordinary categorical palette, no red anywhere.

   @Test
   void refusesAStaticFrameOnAChannelBoundToADimension() {
      ChartBindingModel model = new ChartBindingModel();
      ChartAestheticMutator.setField(model, "color", dimension("Region"));

      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> ChartAestheticMutator.setFrame(
            model, "color", spec("type", "static", "color", "#ff0000")));

      assertTrue(thrown.getMessage().contains("categorical"), thrown.getMessage());
      assertTrue(thrown.getMessage().contains("clear_aesthetic_field"),
                 "the refusal has to name the way to the fixed-value outcome, not just say no");
   }

   /** A ramp is as unrenderable there as a static value, and was equally silent. */
   @Test
   void refusesAGraduatedFrameOnAChannelBoundToADimension() {
      ChartBindingModel model = new ChartBindingModel();
      ChartAestheticMutator.setField(model, "color", dimension("Region"));

      assertThrows(IllegalArgumentException.class,
                   () -> ChartAestheticMutator.setFrame(
                      model, "color", spec("type", "palette", "palette", "Reds")));
   }

   @Test
   void refusesAStaticFrameOnAChannelBoundToAMeasure() {
      ChartBindingModel model = new ChartBindingModel();
      ChartAestheticMutator.setField(model, "color", measure("Sales", "Sum"));

      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> ChartAestheticMutator.setFrame(
            model, "color", spec("type", "static", "color", "#ff0000")));

      assertTrue(thrown.getMessage().contains("gradient"),
                 "the survivors differ per family, so the message names this channel's: " +
                 thrown.getMessage());
   }

   @Test
   void refusesACategoricalFrameOnAChannelBoundToAMeasure() {
      ChartBindingModel model = new ChartBindingModel();
      ChartAestheticMutator.setField(model, "size", measure("Sales", "Sum"));

      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> ChartAestheticMutator.setFrame(
            model, "size", spec("type", "categorical", "smallest", 2, "largest", 20)));

      assertTrue(thrown.getMessage().contains("linear"),
                 "size's only graduated frame is linear: " + thrown.getMessage());
   }

   @Test
   void acceptsACategoricalFrameOnAChannelBoundToADimension() {
      ChartBindingModel model = new ChartBindingModel();
      ChartAestheticMutator.setField(model, "color", dimension("Region"));

      ChartAestheticMutator.setFrame(
         model, "color", spec("type", "categorical", "colors", List.of("#111111")));

      assertInstanceOf(CategoricalColorModel.class, model.getColorField().getFrame());
   }

   @Test
   void acceptsAGraduatedFrameOnAChannelBoundToAMeasure() {
      ChartBindingModel model = new ChartBindingModel();
      ChartAestheticMutator.setField(model, "color", measure("Sales", "Sum"));

      ChartAestheticMutator.setFrame(
         model, "color", spec("type", "gradient", "from", "#eeeeff", "to", "#005599"));

      assertInstanceOf(GradientColorModel.class, model.getColorField().getFrame());
   }

   /** The rule follows the bound field to wherever the channel reads it — shape, for line. */
   @Test
   void theRuleFollowsLineAndTextureToTheShapeField() {
      ChartBindingModel model = new ChartBindingModel();
      ChartBindingMutator.setShelf(model, "y", List.of(measure("Sales", "Sum")));
      ChartAestheticMutator.setField(model, "shape", dimension("Year(ORDER_DATE)"));
      model.getShapeField().setFrame(new CategoricalLineModel());

      assertThrows(IllegalArgumentException.class,
                   () -> ChartAestheticMutator.setFrame(
                      model, "line", spec("type", "static", "line", 4241)));
   }

   /** With nothing bound the rule does not apply — static is exactly what that slot takes. */
   @Test
   void theRuleDoesNotApplyToAFieldLessChannel() {
      ChartBindingModel model = new ChartBindingModel();
      ChartBindingMutator.setShelf(model, "y", List.of(measure("Sales", "Sum")));

      ChartAestheticMutator.setFrame(model, "color", spec("type", "static", "color", "#123456"));

      ChartAggregateRefModel agg = (ChartAggregateRefModel) model.getYFields().get(0);
      assertInstanceOf(StaticColorModel.class, agg.getColorFrame());
   }

   // ── a field-less colour frame on the measures must be static ──────────────
   //
   // Live repro: on a bar chart with the colour channel unbound, set_visual_frame color
   // {type: "categorical", colors: [...]} answered ok and the chart stopped rendering entirely —
   // every later get_viewsheet_image, clear_aesthetic_field and set_aesthetic_field on it returned
   // a 500 too, because the bad frame was now sitting on the measures. The stack is
   // GraphUtil.fixDuplicateColor:1056 casting ChartAggregateRef.getColorFrameWrapper() to
   // StaticColorFrameWrapper. {type: "gradient"} did the same; {type: "static"} rendered fine and
   // restored the chart. The Composer cannot reach the state at all: with nothing on the colour
   // shelf, color-field-mc.getEditPaneId() only ever opens StaticColor or CombinedColor.

   @Test
   void refusesACategoricalColourFrameOnMeasuresWithNoColourField() {
      ChartBindingModel model = new ChartBindingModel();
      ChartBindingMutator.setShelf(model, "y", List.of(measure("Sales", "Sum")));

      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> ChartAestheticMutator.setFrame(
            model, "color", spec("type", "categorical", "colors", List.of("#111111"))));

      assertTrue(thrown.getMessage().contains("static"), thrown.getMessage());
      assertTrue(thrown.getMessage().contains("set_aesthetic_field"),
                 "the refusal has to name the way forward, not just the rule");
   }

   @Test
   void refusesAGradientColourFrameOnMeasuresWithNoColourField() {
      ChartBindingModel model = new ChartBindingModel();
      ChartBindingMutator.setShelf(model, "y", List.of(measure("Sales", "Sum")));

      assertThrows(IllegalArgumentException.class,
                   () -> ChartAestheticMutator.setFrame(
                      model, "color", spec("type", "gradient", "from", "#fff", "to", "#000")));
   }

   /** The one frame the measures can actually carry. */
   @Test
   void acceptsAStaticColourFrameOnMeasuresWithNoColourField() {
      ChartBindingModel model = new ChartBindingModel();
      ChartBindingMutator.setShelf(model, "y", List.of(measure("Sales", "Sum")));

      ChartAestheticMutator.setFrame(model, "color", spec("type", "static", "color", "#4e79a7"));

      ChartAggregateRefModel agg = (ChartAggregateRefModel) model.getYFields().get(0);
      assertEquals("#4E79A7",
                   assertInstanceOf(StaticColorModel.class, agg.getColorFrame()).getColor());
   }

   /** With a field bound the frame lands on the AestheticInfo, which the cast never sees. */
   @Test
   void aCategoricalColourFrameIsFineOnceAFieldIsBound() {
      ChartBindingModel model = new ChartBindingModel();
      ChartBindingMutator.setShelf(model, "y", List.of(measure("Sales", "Sum")));
      ChartAestheticMutator.setField(model, "color", dimension("Region"));

      ChartAestheticMutator.setFrame(
         model, "color", spec("type", "categorical", "colors", List.of("#111111")));

      assertInstanceOf(CategoricalColorModel.class, model.getColorField().getFrame());
   }

   /** The other channels have no such cast, and keep working field-less. */
   @Test
   void theStaticOnlyRuleIsColourOnly() {
      ChartBindingModel model = new ChartBindingModel();
      ChartBindingMutator.setShelf(model, "y", List.of(measure("Sales", "Sum")));

      ChartAestheticMutator.setFrame(
         model, "line", spec("type", "categorical", "lines", List.of(4097, 4113)));

      ChartAggregateRefModel agg = (ChartAggregateRefModel) model.getYFields().get(0);
      assertInstanceOf(CategoricalLineModel.class, agg.getLineFrame());
   }

   // ── Share Colors needs the field name and the viewsheet's existing pins ───
   //
   // Live repro: with a dimension on colour, set_visual_frame color
   // {type: "categorical", colors: [...], useGlobal: true} returned a 500 every time, while the
   // identical call with useGlobal false succeeded. VSChartBindingFactory.applyColorsToViewsheet
   // only runs when the flag is set, and hands frame.getField() to Viewsheet.setDimensionColors,
   // whose getAttribute does column.indexOf(":") -- NPE, because a frame built here is a bare
   // new CategoricalColorModel() and nothing ever set its field.

   @Test
   void aSharedColourFrameCarriesTheFieldNameThatViewsheetLevelPinsAreKeyedBy() {
      ChartBindingModel model = new ChartBindingModel();
      ChartAestheticMutator.setField(model, "color", dimension("Region"));

      ChartAestheticMutator.setFrame(
         model, "color",
         spec("type", "categorical", "colors", List.of("#111111"), "shareColors", true));

      CategoricalColorModel frame =
         assertInstanceOf(CategoricalColorModel.class, model.getColorField().getFrame());
      assertEquals("Region", frame.getField(),
                   "a null here is an NPE in Viewsheet.setDimensionColors, not a missing label");
   }

   /** Carried even when sharing is off, so turning it on later is not the call that breaks. */
   @Test
   void anUnsharedColourFrameCarriesTheFieldNameToo() {
      ChartBindingModel model = new ChartBindingModel();
      ChartAestheticMutator.setField(model, "color", dimension("Region"));

      ChartAestheticMutator.setFrame(
         model, "color", spec("type", "categorical", "colors", List.of("#111111")));

      assertEquals("Region",
                   ((CategoricalColorModel) model.getColorField().getFrame()).getField());
   }

   /**
    * applyColorsToViewsheet rewrites the column's whole entry from globalColorMaps, and
    * Viewsheet.setDimensionColors drops the column's existing keys first — so an empty array does
    * not leave the viewsheet's fixed pins alone, it deletes them. The agent cannot author them, so
    * carrying what the channel already has is the only way a frame write avoids destroying them.
    */
   @Test
   void aSharedColourFrameCarriesTheViewsheetLevelPinsForward() {
      ChartBindingModel model = new ChartBindingModel();
      ChartAestheticMutator.setField(model, "color", dimension("Region"));
      ChartAestheticMutator.setFrame(
         model, "color", spec("type", "categorical", "colors", List.of("#111111")));

      CategoricalColorModel existing = (CategoricalColorModel) model.getColorField().getFrame();
      existing.setGlobalColorMaps(new ColorMapModel[]{ new ColorMapModel("East", "#D64541") });

      ChartAestheticMutator.setFrame(
         model, "color",
         spec("type", "categorical", "colors", List.of("#222222"), "shareColors", true));

      CategoricalColorModel frame =
         assertInstanceOf(CategoricalColorModel.class, model.getColorField().getFrame());
      assertEquals(1, frame.getGlobalColorMaps().length,
                   "an empty array here wipes the column's pins off the viewsheet");
      assertEquals("East", frame.getGlobalColorMaps()[0].getOption());
   }

   /**
    * Turning sharing on is the transition categorical-color-pane.shareColorsChange handles by
    * seeding globalColorMaps from colorMaps — the frame's own pins become the viewsheet's. The
    * option moves where the pins live; it does not discard them.
    */
   @Test
   void turningSharingOnPromotesTheFramesOwnPinsToTheViewsheetLevelOnes() {
      ChartBindingModel model = new ChartBindingModel();
      ChartAestheticMutator.setField(model, "color", dimension("Region"));
      ChartAestheticMutator.setFrame(
         model, "color", spec("type", "categorical", "colors", List.of("#111111")));

      CategoricalColorModel existing = (CategoricalColorModel) model.getColorField().getFrame();
      existing.setColorMaps(new ColorMapModel[]{ new ColorMapModel("West", "#F28E2C") });

      ChartAestheticMutator.setFrame(
         model, "color",
         spec("type", "categorical", "colors", List.of("#222222"), "shareColors", true));

      CategoricalColorModel frame =
         assertInstanceOf(CategoricalColorModel.class, model.getColorField().getFrame());
      assertEquals(1, frame.getGlobalColorMaps().length);
      assertEquals("West", frame.getGlobalColorMaps()[0].getOption());
   }

   /**
    * Pinning a value while sharing is on is the "Assign Fixed Mapping" + "Share Colors" pairing,
    * and it must add to the column's pins rather than replace them: Viewsheet.setDimensionColors
    * drops the column's existing keys before putting the new ones, so a request naming one value
    * would otherwise unpin every other one. The unshared path already behaves that way, because
    * assignMappedColors only calls setColor for the values it was given.
    */
   @Test
   void aSharedMappingAddsToTheColumnsPinsInsteadOfReplacingThem() {
      ChartBindingModel model = new ChartBindingModel();
      ChartAestheticMutator.setField(model, "color", dimension("Region"));
      ChartAestheticMutator.setFrame(
         model, "color", spec("type", "categorical", "colors", List.of("#111111")));

      CategoricalColorModel existing = (CategoricalColorModel) model.getColorField().getFrame();
      existing.setGlobalColorMaps(new ColorMapModel[]{ new ColorMapModel("East", "#D64541") });

      ChartAestheticMutator.setFrame(
         model, "color",
         spec("type", "categorical", "shareColors", true, "mapping", Map.of("West", "#F28E2C")));

      CategoricalColorModel frame =
         assertInstanceOf(CategoricalColorModel.class, model.getColorField().getFrame());
      Map<String, String> pins = new LinkedHashMap<>();

      for(ColorMapModel pin : frame.getGlobalColorMaps()) {
         pins.put(pin.getOption(), pin.getColor());
      }

      assertEquals(Map.of("East", "#D64541", "West", "#F28E2C"), pins,
                   "naming one value must not unpin the others");
   }

   /** Naming a value that is already pinned re-colours it rather than duplicating it. */
   @Test
   void aSharedMappingOverwritesAPinItNames() {
      ChartBindingModel model = new ChartBindingModel();
      ChartAestheticMutator.setField(model, "color", dimension("Region"));
      ChartAestheticMutator.setFrame(
         model, "color", spec("type", "categorical", "colors", List.of("#111111")));

      CategoricalColorModel existing = (CategoricalColorModel) model.getColorField().getFrame();
      existing.setGlobalColorMaps(new ColorMapModel[]{ new ColorMapModel("East", "#D64541") });

      ChartAestheticMutator.setFrame(
         model, "color",
         spec("type", "categorical", "shareColors", true, "mapping", Map.of("East", "#000000")));

      CategoricalColorModel frame =
         assertInstanceOf(CategoricalColorModel.class, model.getColorField().getFrame());
      assertEquals(1, frame.getGlobalColorMaps().length);
      assertEquals("#000000", frame.getGlobalColorMaps()[0].getColor());
   }

   /**
    * A spec that does not mention shareColors is not asking about the checkbox. Forcing it off
    * meant every colour change silently switched sharing off for a chart that had it on, with
    * nothing in the request to say so. The Composer's Apply posts back whatever
    * CategoricalColorModel(wrapper) read off the frame, and this now does the same.
    */
   @Test
   void anOmittedShareColorsLeavesTheChannelsCheckboxAlone() {
      ChartBindingModel model = new ChartBindingModel();
      ChartAestheticMutator.setField(model, "color", dimension("Region"));
      ChartAestheticMutator.setFrame(
         model, "color",
         spec("type", "categorical", "colors", List.of("#111111"), "shareColors", true));

      ChartAestheticMutator.setFrame(
         model, "color", spec("type", "categorical", "colors", List.of("#222222")));

      CategoricalColorModel frame =
         assertInstanceOf(CategoricalColorModel.class, model.getColorField().getFrame());
      assertTrue(frame.isShareColors(), "changing a colour is not a request to stop sharing");
      assertTrue(frame.isUseGlobal());
      assertArrayEquals(new String[]{ "#222222" }, frame.getColors());
   }

   @Test
   void anExplicitShareColorsFalseStillTurnsSharingOff() {
      ChartBindingModel model = new ChartBindingModel();
      ChartAestheticMutator.setField(model, "color", dimension("Region"));
      ChartAestheticMutator.setFrame(
         model, "color",
         spec("type", "categorical", "colors", List.of("#111111"), "shareColors", true));

      ChartAestheticMutator.setFrame(
         model, "color",
         spec("type", "categorical", "colors", List.of("#222222"), "shareColors", false));

      CategoricalColorModel frame =
         assertInstanceOf(CategoricalColorModel.class, model.getColorField().getFrame());
      assertFalse(frame.isShareColors());
      assertFalse(frame.isUseGlobal());
   }

   /**
    * The two flags agree on every frame the Composer or this tool writes; a legacy frame where
    * they do not is one neither can repair, so a write that was not asked about them must not
    * normalise them either.
    */
   @Test
   void anOmittedShareColorsPreservesALegacyFramesDivergentPair() {
      ChartBindingModel model = new ChartBindingModel();
      ChartAestheticMutator.setField(model, "color", dimension("Region"));
      ChartAestheticMutator.setFrame(
         model, "color", spec("type", "categorical", "colors", List.of("#111111")));

      CategoricalColorModel existing = (CategoricalColorModel) model.getColorField().getFrame();
      existing.setUseGlobal(true);
      existing.setShareColors(false);

      ChartAestheticMutator.setFrame(
         model, "color", spec("type", "categorical", "colors", List.of("#222222")));

      CategoricalColorModel frame =
         assertInstanceOf(CategoricalColorModel.class, model.getColorField().getFrame());
      assertTrue(frame.isUseGlobal());
      assertFalse(frame.isShareColors());
   }

   /**
    * With sharing carried forward rather than restated, the pins still have to land in the array
    * the factory reads — VisualFrameAliases routed on the spec, which said nothing.
    */
   @Test
   void anOmittedShareColorsStillRoutesPinsToTheSharedArray() {
      ChartBindingModel model = new ChartBindingModel();
      ChartAestheticMutator.setField(model, "color", dimension("Region"));
      ChartAestheticMutator.setFrame(
         model, "color",
         spec("type", "categorical", "colors", List.of("#111111"), "shareColors", true));

      ChartAestheticMutator.setFrame(
         model, "color", spec("type", "categorical", "mapping", Map.of("2022", "#000000")));

      CategoricalColorModel frame =
         assertInstanceOf(CategoricalColorModel.class, model.getColorField().getFrame());
      assertEquals(1, frame.getGlobalColorMaps().length);
      assertEquals("2022", frame.getGlobalColorMaps()[0].getOption());
      assertEquals(0, frame.getColorMaps().length,
                   "a stale copy in the array the factory ignores is how a read starts lying");
   }

   /** Sharing off never reaches applyColorsToViewsheet, so nothing is promoted. */
   @Test
   void anUnsharedColourFrameDoesNotPromoteAnything() {
      ChartBindingModel model = new ChartBindingModel();
      ChartAestheticMutator.setField(model, "color", dimension("Region"));
      ChartAestheticMutator.setFrame(
         model, "color", spec("type", "categorical", "colors", List.of("#111111")));

      CategoricalColorModel existing = (CategoricalColorModel) model.getColorField().getFrame();
      existing.setColorMaps(new ColorMapModel[]{ new ColorMapModel("West", "#F28E2C") });

      ChartAestheticMutator.setFrame(
         model, "color", spec("type", "categorical", "colors", List.of("#222222")));

      CategoricalColorModel frame =
         assertInstanceOf(CategoricalColorModel.class, model.getColorField().getFrame());
      assertEquals(0, frame.getGlobalColorMaps().length);
   }

   // ── one measure at a time: the Composer's Combined pane ───────────────────
   //
   // getEditPaneId() opens CombinedColor/CombinedSize when the channel is empty and frames.length
   // > 1 -- one editor per measure, labelled with the measure's name. Without a way to name one,
   // every field-less write went to all of them, so "make Profit orange" had to mean "make
   // everything orange".

   @Test
   void aNamedMeasureGetsTheFrameAndTheOthersKeepTheirs() {
      ChartBindingModel model = new ChartBindingModel();
      ChartBindingMutator.setShelf(
         model, "y", List.of(measure("Sales", "Sum"), measure("Profit", "Sum")));
      ChartAestheticMutator.setFrame(model, "color", spec("type", "static", "color", "#111111"));

      ChartAestheticMutator.setFrame(
         model, "color", spec("type", "static", "color", "#F28E2C"), false,
         AestheticChannels.FRAME_CHANNELS, "Profit");

      assertEquals("#111111", colorOf(model, 0), "the measure that was not named keeps its own");
      assertEquals("#F28E2C", colorOf(model, 1));
   }

   @Test
   void anUnnamedMeasureStillBroadcasts() {
      ChartBindingModel model = new ChartBindingModel();
      ChartBindingMutator.setShelf(
         model, "y", List.of(measure("Sales", "Sum"), measure("Profit", "Sum")));

      ChartAestheticMutator.setFrame(model, "color", spec("type", "static", "color", "#111111"));

      assertEquals("#111111", colorOf(model, 0));
      assertEquals("#111111", colorOf(model, 1));
   }

   @Test
   void refusesAMeasureTheChartDoesNotHave() {
      ChartBindingModel model = new ChartBindingModel();
      ChartBindingMutator.setShelf(model, "y", List.of(measure("Sales", "Sum")));

      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> ChartAestheticMutator.setFrame(
            model, "color", spec("type", "static", "color", "#111111"), false,
            AestheticChannels.FRAME_CHANNELS, "Nope"));

      assertTrue(thrown.getMessage().contains("Sales"),
                 "naming the ones that exist is what turns a refusal into a next step: " +
                 thrown.getMessage());
   }

   /** With a field bound there is one frame for the channel, not one per measure. */
   @Test
   void refusesAMeasureWhenTheChannelHasAFieldBound() {
      ChartBindingModel model = new ChartBindingModel();
      ChartBindingMutator.setShelf(model, "y", List.of(measure("Sales", "Sum")));
      ChartAestheticMutator.setField(model, "color", dimension("Region"));

      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> ChartAestheticMutator.setFrame(
            model, "color", spec("type", "categorical", "colors", List.of("#111111")), false,
            AestheticChannels.FRAME_CHANNELS, "Sales"));

      assertTrue(thrown.getMessage().contains("clear_aesthetic_field"), thrown.getMessage());
   }

   /**
    * frameOf() reports the first measure's frame, which stops being the whole truth the moment a
    * measure-scoped write makes them differ. Reporting only the first would then describe a chart
    * that is visibly drawing something else beside it.
    */
   @Test
   void theReadReportsEveryMeasureOnceTheyDisagree() {
      ChartBindingModel model = new ChartBindingModel();
      ChartBindingMutator.setShelf(
         model, "y", List.of(measure("Sales", "Sum"), measure("Profit", "Sum")));
      ChartAestheticMutator.setFrame(model, "color", spec("type", "static", "color", "#111111"));
      ChartAestheticMutator.setFrame(
         model, "color", spec("type", "static", "color", "#F28E2C"), false,
         AestheticChannels.FRAME_CHANNELS, "Profit");

      @SuppressWarnings("unchecked")
      Map<String, Object> color =
         (Map<String, Object>) ChartAestheticMutator.describe(model).get("color");
      @SuppressWarnings("unchecked")
      Map<String, Object> byMeasure = (Map<String, Object>) color.get("framesByMeasure");

      assertNotNull(byMeasure, "a read that hid this would report a colour half the chart is not");
      assertEquals(Set.of("Sales", "Profit"), byMeasure.keySet());
   }

   /** Agreeing measures keep the common chart's read as short as it was. */
   @Test
   void theReadStaysShortWhileTheMeasuresAgree() {
      ChartBindingModel model = new ChartBindingModel();
      ChartBindingMutator.setShelf(
         model, "y", List.of(measure("Sales", "Sum"), measure("Profit", "Sum")));
      ChartAestheticMutator.setFrame(model, "color", spec("type", "static", "color", "#111111"));

      @SuppressWarnings("unchecked")
      Map<String, Object> color =
         (Map<String, Object>) ChartAestheticMutator.describe(model).get("color");

      assertFalse(color.containsKey("framesByMeasure"));
   }

   private static String colorOf(ChartBindingModel model, int index) {
      ChartAggregateRefModel agg = (ChartAggregateRefModel) model.getYFields().get(index);
      return assertInstanceOf(StaticColorModel.class, agg.getColorFrame()).getColor();
   }

   // ── "Reset to Default" ───────────────────────────────────────────────────
   //
   // Four panes carry the button and no two do the same thing; three of them are a model change
   // and are mirrored here. categorical-color restores cssColors ?? defaultColors; combined-color
   // restores each measure's palette entry for its own position; categorical-shape serves shapes,
   // lines and textures from one button. linear-color's resetEditors() only re-syncs widgets.

   @Test
   void resetRestoresACategoricalPaletteToItsDefaults() {
      ChartBindingModel model = new ChartBindingModel();
      ChartAestheticMutator.setField(model, "color", dimension("Region"));
      ChartAestheticMutator.setFrame(
         model, "color", spec("type", "categorical", "colors", List.of("#111111", "#222222")));

      CategoricalColorModel frame = (CategoricalColorModel) model.getColorField().getFrame();
      frame.setDefaultColors(new String[]{ "#4E79A7", "#F28E2C" });

      ChartAestheticMutator.resetFrame(
         model, "color", false, AestheticChannels.FRAME_CHANNELS, null);

      assertArrayEquals(
         new String[]{ "#4E79A7", "#F28E2C" },
         ((CategoricalColorModel) model.getColorField().getFrame()).getColors());
   }

   /** The CSS theme's entry wins over the built-in one, as it does in the pane. */
   @Test
   void resetPrefersTheCssPaletteOverTheBuiltInOne() {
      ChartBindingModel model = new ChartBindingModel();
      ChartAestheticMutator.setField(model, "color", dimension("Region"));
      ChartAestheticMutator.setFrame(
         model, "color", spec("type", "categorical", "colors", List.of("#111111")));

      CategoricalColorModel frame = (CategoricalColorModel) model.getColorField().getFrame();
      frame.setCssColors(new String[]{ "#00FF00" });
      frame.setDefaultColors(new String[]{ "#4E79A7" });

      ChartAestheticMutator.resetFrame(
         model, "color", false, AestheticChannels.FRAME_CHANNELS, null);

      assertArrayEquals(
         new String[]{ "#00FF00" },
         ((CategoricalColorModel) model.getColorField().getFrame()).getColors());
   }

   /** The button sits beside the palette editors, not beside "Assign Fixed Mapping". */
   @Test
   void resetLeavesThePinnedValuesPinned() {
      ChartBindingModel model = new ChartBindingModel();
      ChartAestheticMutator.setField(model, "color", dimension("Region"));
      ChartAestheticMutator.setFrame(
         model, "color",
         spec("type", "categorical", "colors", List.of("#111111"),
              "mapping", Map.of("East", "#d64541")));

      ((CategoricalColorModel) model.getColorField().getFrame())
         .setDefaultColors(new String[]{ "#4E79A7" });

      ChartAestheticMutator.resetFrame(
         model, "color", false, AestheticChannels.FRAME_CHANNELS, null);

      CategoricalColorModel frame = (CategoricalColorModel) model.getColorField().getFrame();
      assertEquals(1, frame.getColorMaps().length);
      assertEquals("East", frame.getColorMaps()[0].getOption());
   }

   @Test
   void resetGivesEachMeasureThePaletteEntryForItsOwnPosition() {
      ChartBindingModel model = new ChartBindingModel();
      ChartBindingMutator.setShelf(
         model, "y", List.of(measure("Sales", "Sum"), measure("Profit", "Sum")));
      ChartAestheticMutator.setFrame(model, "color", spec("type", "static", "color", "#111111"));

      ChartAestheticMutator.resetFrame(
         model, "color", false, AestheticChannels.FRAME_CHANNELS, null);

      assertNotEquals(colorOf(model, 0), colorOf(model, 1),
                      "both measures resetting to one colour is what the position exists to stop");
   }

   @Test
   void resetScopedToOneMeasureLeavesTheOthersAlone() {
      ChartBindingModel model = new ChartBindingModel();
      ChartBindingMutator.setShelf(
         model, "y", List.of(measure("Sales", "Sum"), measure("Profit", "Sum")));
      ChartAestheticMutator.setFrame(model, "color", spec("type", "static", "color", "#111111"));

      ChartAestheticMutator.resetFrame(
         model, "color", false, AestheticChannels.FRAME_CHANNELS, "Profit");

      assertEquals("#111111", colorOf(model, 0));
      assertNotEquals("#111111", colorOf(model, 1));
   }

   /** One button, three families -- categorical-shape-pane switches on the chart's own. */
   @Test
   void resetRestoresACategoricalLineSetToThePickersOwnList() {
      ChartBindingModel model = new ChartBindingModel();
      ChartBindingMutator.setShelf(model, "y", List.of(measure("Sales", "Sum")));
      ChartAestheticMutator.setField(model, "shape", dimension("Year(ORDER_DATE)"));
      CategoricalLineModel lines = new CategoricalLineModel();
      lines.setLines(new int[]{ 4241, 4241 });
      model.getShapeField().setFrame(lines);

      ChartAestheticMutator.resetFrame(
         model, "line", false, AestheticChannels.FRAME_CHANNELS, null);

      int[] reset = ((CategoricalLineModel) model.getShapeField().getFrame()).getLines();
      assertEquals(10, reset.length, "the five styles listed twice, as the pane's reset does");
      assertEquals(4097, reset[0]);
   }

   @Test
   void resetRestoresACategoricalTextureSetWithoutTheNoTextureEntry() {
      ChartBindingModel model = new ChartBindingModel();
      ChartBindingMutator.setShelf(model, "y", List.of(measure("Sales", "Sum")));
      ChartAestheticMutator.setField(model, "shape", dimension("Year(ORDER_DATE)"));
      CategoricalTextureModel textures = new CategoricalTextureModel();
      textures.setTextures(new int[]{ 19, 19 });
      model.getShapeField().setFrame(textures);

      ChartAestheticMutator.resetFrame(
         model, "texture", false, AestheticChannels.FRAME_CHANNELS, null);

      int[] reset = ((CategoricalTextureModel) model.getShapeField().getFrame()).getTextures();
      assertEquals(20, reset.length);
      assertEquals(0, reset[0], "PATTERN_NONE is left out -- an invisible category is not a reset");
   }

   @Test
   void resetRestoresACategoricalShapeSet() {
      ChartBindingModel model = new ChartBindingModel();
      ChartBindingMutator.setShelf(model, "y", List.of(measure("Sales", "Sum")));
      ChartAestheticMutator.setField(model, "shape", dimension("Region"));
      CategoricalShapeModel shapes = new CategoricalShapeModel();
      shapes.setShapes(new String[]{ "907", "907" });
      model.getShapeField().setFrame(shapes);

      ChartAestheticMutator.resetFrame(
         model, "shape", false, AestheticChannels.FRAME_CHANNELS, null);

      String[] reset = ((CategoricalShapeModel) model.getShapeField().getFrame()).getShapes();
      assertEquals(32, reset.length, "sixteen point shapes then the sixteen bundled images");
      assertEquals("900", reset[0]);
      assertEquals("100ArrowDown.svg", reset[16]);
   }

   /** A frame with no stored default is refused rather than given an invented one. */
   @Test
   void refusesToResetAFrameThatHasNoDefault() {
      ChartBindingModel model = new ChartBindingModel();
      ChartAestheticMutator.setField(model, "color", measure("Sales", "Sum"));
      ChartAestheticMutator.setFrame(
         model, "color", spec("type", "gradient", "from", "#eeeeff", "to", "#005599"));

      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> ChartAestheticMutator.resetFrame(
            model, "color", false, AestheticChannels.FRAME_CHANNELS, null));

      assertTrue(thrown.getMessage().contains("set_visual_frame"),
                 "the refusal has to name the way to get the value they want: " +
                 thrown.getMessage());
   }

   // ── acceptsField vs. set_aesthetic_field agreement (L3-Group4 finding G4-4) ───────────────
   //
   // get_chart_aesthetics' acceptsField used to answer only "does this channel have a field
   // slot at all" (AestheticChannels.FIELD_CHANNELS membership), independent of chart type --
   // disagreeing with set_aesthetic_field's own sizeSupported/colorShapeSupported-gated refusal.
   // Live-confirmed 2026-09-01: a mekko chart's size channel reported acceptsField:true here,
   // then set_aesthetic_field refused the identical write.

   @Test
   void acceptsFieldReportsFalseForSizeWhenTheChartTypeDoesNotSupportIt() {
      ChartBindingModel model = new ChartBindingModel();
      ChartBindingMutator.setShelf(model, "y", List.of(measure("Sales", "Sum")));

      @SuppressWarnings("unchecked")
      Map<String, Object> size = (Map<String, Object>) ChartAestheticMutator.describe(
         model, false, AestheticChannels.FRAME_CHANNELS, false, true).get("size");

      assertEquals(false, size.get("acceptsField"),
                   "must agree with set_aesthetic_field's own sizeSupported refusal");
   }

   @Test
   void acceptsFieldReportsFalseForColorAndShapeOnAContourChart() {
      ChartBindingModel model = new ChartBindingModel();
      ChartBindingMutator.setShelf(model, "y", List.of(measure("Sales", "Sum")));

      Map<String, Object> described = ChartAestheticMutator.describe(
         model, false, AestheticChannels.FRAME_CHANNELS, true, false);
      @SuppressWarnings("unchecked")
      Map<String, Object> color = (Map<String, Object>) described.get("color");
      @SuppressWarnings("unchecked")
      Map<String, Object> shape = (Map<String, Object>) described.get("shape");

      assertEquals(false, color.get("acceptsField"),
                   "must agree with set_aesthetic_field's own colorShapeSupported refusal");
      assertEquals(false, shape.get("acceptsField"),
                   "must agree with set_aesthetic_field's own colorShapeSupported refusal");
   }

   @Test
   void acceptsFieldStaysTrueForTextRegardlessOfSizeOrColorShapeSupport() {
      ChartBindingModel model = new ChartBindingModel();
      ChartBindingMutator.setShelf(model, "y", List.of(measure("Sales", "Sum")));

      @SuppressWarnings("unchecked")
      Map<String, Object> text = (Map<String, Object>) ChartAestheticMutator.describe(
         model, false, AestheticChannels.FRAME_CHANNELS, false, false).get("text");

      assertEquals(true, text.get("acceptsField"),
                   "text isn't gated by either sizeSupported or colorShapeSupported");
   }

   @Test
   void theThreeArgDescribeDefaultsToUnrestrictedAcceptsField() {
      ChartBindingModel model = new ChartBindingModel();
      ChartBindingMutator.setShelf(model, "y", List.of(measure("Sales", "Sum")));

      @SuppressWarnings("unchecked")
      Map<String, Object> size = (Map<String, Object>) ChartAestheticMutator.describe(
         model, false, AestheticChannels.FRAME_CHANNELS).get("size");

      assertEquals(true, size.get("acceptsField"),
                   "callers that don't pass chart-type context keep the old, unrestricted answer");
   }
}
