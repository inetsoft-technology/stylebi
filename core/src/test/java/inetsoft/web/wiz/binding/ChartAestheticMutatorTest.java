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
      ChartAestheticMutator.setField(model, "color", dimension("Region"));
      ChartAestheticMutator.setFrame(model, "color", spec("type", "palette", "palette", "Reds"));

      Map<String, Object> described = ChartAestheticMutator.describe(model);
      @SuppressWarnings("unchecked")
      Map<String, Object> color = (Map<String, Object>) described.get("color");

      assertEquals("Region", color.get("field"));
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
}
