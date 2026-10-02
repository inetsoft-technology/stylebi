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

import inetsoft.graph.aesthetic.LineFrame;
import inetsoft.graph.aesthetic.ShapeFrame;
import inetsoft.graph.aesthetic.TextureFrame;
import inetsoft.graph.aesthetic.VisualFrame;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.web.binding.controller.ChangeChartAestheticService;
import inetsoft.web.binding.event.ChangeChartRefEvent;
import inetsoft.web.binding.model.ChartBindingModel;
import inetsoft.web.binding.model.graph.aesthetic.BluesColorModel;
import inetsoft.web.binding.model.graph.aesthetic.StaticColorModel;
import inetsoft.web.binding.service.VSBindingService;
import inetsoft.web.wiz.binding.model.FieldRef;
import inetsoft.web.wiz.viewsheet.ViewsheetSessionService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("core")
class ChartAestheticAgentServiceTest {
   private static Map<String, Object> spec(Object... pairs) {
      Map<String, Object> spec = new LinkedHashMap<>();

      for(int i = 0; i < pairs.length; i += 2) {
         spec.put((String) pairs[i], pairs[i + 1]);
      }

      return spec;
   }

   @Test
   void setFieldPostsTheModelItReadRatherThanAFreshOne() throws Exception {
      ChartBindingModel existing = new ChartBindingModel();
      // A value only the read model carries. If the service constructs a fresh model this is
      // lost — the same failure that would silently discard the user's shelves.
      existing.setChartType(42);
      ChangeChartAestheticService aesthetics = mock(ChangeChartAestheticService.class);

      harness(existing, aesthetics)
         .setField("tok", principal(), "Chart1", "color",
                   new FieldRef("Region", "dimension", null, null, null), null, "");

      ChangeChartRefEvent event = captureEvent(aesthetics);
      assertEquals(42, event.getModel().getChartType(),
                   "the posted model must be the one read, not a fresh construction");
      assertEquals("Region", event.getModel().getColorField().getFullName());
      assertEquals("Chart1", event.getName());
   }

   /**
    * {@code ChangeChartAestheticService} branches on the field type — colour and shape clear
    * the viewsheet's shared frames. Posting the wrong one leaves stale shared frames behind.
    */
   @Test
   void setFieldTellsTheBackendWhichChannelChanged() throws Exception {
      ChangeChartAestheticService aesthetics = mock(ChangeChartAestheticService.class);

      harness(new ChartBindingModel(), aesthetics)
         .setField("tok", principal(), "Chart1", "color",
                   new FieldRef("Region", "dimension", null, null, null), null, "");

      assertEquals("color", captureEvent(aesthetics).getFieldType());
   }

   @Test
   void setFieldLeavesTheShelvesUntouched() throws Exception {
      ChartBindingModel existing = new ChartBindingModel();
      ChartBindingMutator.setShelf(
         existing, "x", List.of(new FieldRef("Region", "dimension", null, null, null)));
      Object shelfBefore = existing.getXFields();
      ChangeChartAestheticService aesthetics = mock(ChangeChartAestheticService.class);

      harness(existing, aesthetics)
         .setField("tok", principal(), "Chart1", "color",
                   new FieldRef("Category", "dimension", null, null, null), null, "");

      assertSame(shelfBefore, captureEvent(aesthetics).getModel().getXFields(),
                 "an aesthetic write must not disturb the shelves spec 2b owns");
   }

   @Test
   void clearFieldUnbindsTheChannel() throws Exception {
      ChartBindingModel existing = new ChartBindingModel();
      ChartAestheticMutator.setField(existing, "color",
                                     new FieldRef("Region", "dimension", null, null, null));
      ChangeChartAestheticService aesthetics = mock(ChangeChartAestheticService.class);

      harness(existing, aesthetics).clearField("tok", principal(), "Chart1", "color", "");

      assertNull(captureEvent(aesthetics).getModel().getColorField());
   }

   @Test
   void setFrameAppliesAStaticColour() throws Exception {
      ChangeChartAestheticService aesthetics = mock(ChangeChartAestheticService.class);

      harness(new ChartBindingModel(), aesthetics)
         .setFrame("tok", principal(), "Chart1", "color",
                   spec("type", "static", "color", "#4e79a7"), "");

      StaticColorModel frame = assertInstanceOf(
         StaticColorModel.class, captureEvent(aesthetics).getModel().getColorFrame());
      assertEquals("#4E79A7", frame.getColor());
   }

   @Test
   void setFrameAppliesANamedPalette() throws Exception {
      ChangeChartAestheticService aesthetics = mock(ChangeChartAestheticService.class);

      harness(new ChartBindingModel(), aesthetics)
         .setFrame("tok", principal(), "Chart1", "color",
                   spec("type", "palette", "palette", "Blues"), "");

      assertInstanceOf(BluesColorModel.class,
                       captureEvent(aesthetics).getModel().getColorFrame());
   }

   @Test
   void eachMutationIsExactlyOneCheckpoint() throws Exception {
      ViewsheetSessionService sessions = sessionsFor(mock(ChartVSAssembly.class));
      ChartAestheticAgentService service = serviceWith(sessions, new ChartBindingModel(),
                                                  mock(ChangeChartAestheticService.class));

      service.setField("tok", principal(), "Chart1", "color",
                       new FieldRef("Region", "dimension", null, null, null), null, "");

      verify(sessions, times(1)).mutate(anyString(), any(Principal.class), any());
   }

   @Test
   void readDescribesTheChannelsWithoutMutating() throws Exception {
      ChartBindingModel existing = new ChartBindingModel();
      ChartAestheticMutator.setField(existing, "color",
                                     new FieldRef("Region", "dimension", null, null, null));
      ViewsheetSessionService sessions = sessionsFor(mock(ChartVSAssembly.class));
      ChartAestheticAgentService service = serviceWith(sessions, existing,
                                                  mock(ChangeChartAestheticService.class));

      Map<String, Object> read = service.read("tok", principal(), "Chart1");

      @SuppressWarnings("unchecked")
      Map<String, Object> color = (Map<String, Object>) read.get("color");
      assertEquals("Region", color.get("field"));
      verify(sessions, never()).mutate(anyString(), any(Principal.class), any());
   }

   @Test
   void refusesANonChartAssemblyNamingIt() {
      ChartAestheticAgentService service = serviceWith(
         sessionsFor(mock(TextVSAssembly.class)), new ChartBindingModel(),
         mock(ChangeChartAestheticService.class));

      Exception thrown = assertThrows(
         Exception.class,
         () -> service.setField("tok", principal(), "Text1", "color",
                                new FieldRef("Region", "dimension", null, null, null), null, ""));
      assertTrue(thrown.getMessage().contains("Text1"));
   }

   @Test
   void refusesAnUnknownChannelBeforeTouchingTheRuntime() {
      ViewsheetSessionService sessions = sessionsFor(mock(ChartVSAssembly.class));
      ChartAestheticAgentService service = serviceWith(sessions, new ChartBindingModel(),
                                                  mock(ChangeChartAestheticService.class));

      assertThrows(Exception.class,
                   () -> service.setField("tok", principal(), "Chart1", "colour",
                                          new FieldRef("Region", "dimension", null, null, null),
                                          null, ""));
   }

   // ── size gating on chart types that do not render it ───────────────────────

   @Test
   void setFieldRefusesSizeOnAChartTypeThatDoesNotSupportIt() {
      ChartAestheticAgentService service = serviceWith(
         sessionsFor(chartOfType(GraphTypes.CHART_MEKKO)), new ChartBindingModel(),
         mock(ChangeChartAestheticService.class));

      Exception thrown = assertThrows(
         Exception.class,
         () -> service.setField("tok", principal(), "Chart1", "size",
                                new FieldRef("Sales", "measure", null, null, null), null, ""));
      assertTrue(thrown.getMessage().contains("size"));
   }

   @Test
   void setFieldRefusesSizeOnStockToo() {
      ChartAestheticAgentService service = serviceWith(
         sessionsFor(chartOfType(GraphTypes.CHART_STOCK)), new ChartBindingModel(),
         mock(ChangeChartAestheticService.class));

      assertThrows(Exception.class,
                   () -> service.setField("tok", principal(), "Chart1", "size",
                                          new FieldRef("Sales", "measure", null, null, null),
                                          null, ""));
   }

   @Test
   void setFieldAcceptsSizeOnAnOrdinaryChartType() throws Exception {
      ChangeChartAestheticService aesthetics = mock(ChangeChartAestheticService.class);

      harness(new ChartBindingModel(), aesthetics)
         .setField("tok", principal(), "Chart1", "size",
                  new FieldRef("Sales", "measure", null, null, null), null, "");

      assertEquals("Sales", captureEvent(aesthetics).getModel().getSizeField().getFullName());
   }

   private static ChartVSAssembly chartOfType(int chartType) {
      VSChartInfo info = mock(VSChartInfo.class);
      when(info.getChartType()).thenReturn(chartType);
      ChartVSAssembly chart = mock(ChartVSAssembly.class);
      when(chart.getVSChartInfo()).thenReturn(info);
      return chart;
   }

   // ── node channels (spec 2c Phase 3) ───────────────────────────────────────

   @Test
   void refusesNodeColorOnAChartThatIsNotARelationChart() {
      ChartAestheticAgentService service = serviceWith(
         sessionsFor(mock(ChartVSAssembly.class)), new ChartBindingModel(),
         mock(ChangeChartAestheticService.class));

      Exception thrown = assertThrows(
         Exception.class,
         () -> service.setField("tok", principal(), "Chart1", "node-color",
                                new FieldRef("Region", "dimension", null, null, null), null, ""));
      assertTrue(thrown.getMessage().contains("relation"));
   }

   @Test
   void acceptsNodeColorOnARelationChart() throws Exception {
      ChartVSAssembly relationChart = relationChartAssembly();
      ChangeChartAestheticService aesthetics = mock(ChangeChartAestheticService.class);

      serviceWith(sessionsFor(relationChart), new ChartBindingModel(), aesthetics)
         .setField("tok", principal(), "Chart1", "node-color",
                  new FieldRef("Region", "dimension", null, null, null), null, "");

      assertEquals("Region", captureEvent(aesthetics).getModel().getNodeColorField().getFullName());
   }

   @Test
   void readReportsNodeChannelsOnlyForARelationChart() throws Exception {
      ChartBindingModel model = new ChartBindingModel();
      ChartAestheticMutator.setField(model, "node-color",
                                     new FieldRef("Region", "dimension", null, null, null), true);
      ChartAestheticAgentService onRelation = serviceWith(sessionsFor(relationChartAssembly()),
                                                     model, mock(ChangeChartAestheticService.class));
      ChartAestheticAgentService onBar = serviceWith(sessionsFor(mock(ChartVSAssembly.class)),
                                               model, mock(ChangeChartAestheticService.class));

      assertTrue(onRelation.read("tok", principal(), "Chart1").containsKey("node-color"));
      assertFalse(onBar.read("tok", principal(), "Chart1").containsKey("node-color"),
                 "a bar chart's read must not advertise a channel it cannot render");
   }

   @Test
   void optionsNamesTheNodeChannelsWithTheRelationChartCaveat() {
      ChartAestheticAgentService service = serviceWith(
         sessionsFor(mock(ChartVSAssembly.class)), new ChartBindingModel(),
         mock(ChangeChartAestheticService.class));

      Map<String, Object> options = service.options();

      assertEquals(List.of("node-color", "node-size"), options.get("nodeChannels"));
      assertTrue(((String) options.get("nodeChannelsNote")).contains("relation charts"));
   }

   // ── multi-style corruption guard (L3-Group2 finding G2-5) ─────────────────────────────────
   //
   // Live-confirmed 2026-09-01: writing a field while a chart is already multi-style corrupts
   // its runtime graph so get_viewsheet_image fails, and unlike every other guard in this class
   // the corruption survives undoing both the field write and the multi-style toggle that
   // preceded it. The docstring already told a caller to bind the field first, then turn multi-
   // style on -- this makes the unsafe order (multi-style already on, then bind) fail loud
   // instead of silently corrupting the chart.

   @Test
   void refusesSettingAFieldWhileTheChartIsAlreadyMultiStyle() {
      ChartAestheticAgentService service = serviceWith(
         sessionsFor(multiStyleChartAssembly()), new ChartBindingModel(),
         mock(ChangeChartAestheticService.class));

      Exception thrown = assertThrows(
         Exception.class,
         () -> service.setField("tok", principal(), "Chart1", "color",
                                new FieldRef("Region", "dimension", null, null, null), null, ""));
      assertTrue(thrown.getMessage().toLowerCase().contains("multi-style"), thrown.getMessage());
   }

   @Test
   void allowsSettingAFieldWhenTheChartIsNotMultiStyle() throws Exception {
      ChangeChartAestheticService aesthetics = mock(ChangeChartAestheticService.class);

      harness(new ChartBindingModel(), aesthetics)
         .setField("tok", principal(), "Chart1", "color",
                  new FieldRef("Region", "dimension", null, null, null), null, "");

      assertEquals("Region", captureEvent(aesthetics).getModel().getColorField().getFullName());
   }

   /**
    * PR #4921 round-1 finding 2: {@code clearField} calls the identical chart-level
    * {@code assign(model, name, null)} write {@code setField} does (see
    * {@code ChartAestheticMutator.clearField}), so it corrupts a multi-style chart the same way —
    * but the guard was only wired onto {@code setField}. This proves it is now wired onto
    * {@code clearField} too.
    */
   @Test
   void refusesClearingAFieldWhileTheChartIsAlreadyMultiStyle() {
      ChartAestheticAgentService service = serviceWith(
         sessionsFor(multiStyleChartAssembly()), new ChartBindingModel(),
         mock(ChangeChartAestheticService.class));

      Exception thrown = assertThrows(
         Exception.class,
         () -> service.clearField("tok", principal(), "Chart1", "color", ""));
      assertTrue(thrown.getMessage().toLowerCase().contains("multi-style"), thrown.getMessage());
   }

   @Test
   void allowsClearingAFieldWhenTheChartIsNotMultiStyle() throws Exception {
      ChartBindingModel existing = new ChartBindingModel();
      ChartAestheticMutator.setField(existing, "color",
                                     new FieldRef("Region", "dimension", null, null, null));
      ChangeChartAestheticService aesthetics = mock(ChangeChartAestheticService.class);

      harness(existing, aesthetics).clearField("tok", principal(), "Chart1", "color", "");

      assertNull(captureEvent(aesthetics).getModel().getColorField());
   }

   private static ChartVSAssembly multiStyleChartAssembly() {
      VSChartInfo info = mock(VSChartInfo.class);
      when(info.isMultiAesthetic()).thenReturn(true);
      ChartVSAssembly chart = mock(ChartVSAssembly.class);
      when(chart.getVSChartInfo()).thenReturn(info);
      return chart;
   }

   // ── DCG-013: date comparison forcing multi-style at runtime ──────────────
   //
   // isMultiAesthetic() delegates to VSChartInfo.isMultiStyles(), which a changeAndValue/
   // percentChangeAndValue date comparison forces to true regardless of the persisted design
   // flag (ChartDcProcessor.changeMultiStyle()). set_chart_type(multi:false), the ordinary
   // remediation this guard names below, only ever writes the design flag and cannot clear this —
   // so the DC-forced case needs its own message, pointing at clear_date_comparison instead.

   @Test
   void refusesSettingAFieldWhileADateComparisonForcesMultiStyleAndNamesTheRealCause() {
      ChartAestheticAgentService service = serviceWith(
         sessionsFor(dateComparisonForcedMultiStyleChartAssembly()), new ChartBindingModel(),
         mock(ChangeChartAestheticService.class));

      Exception thrown = assertThrows(
         Exception.class,
         () -> service.setField("tok", principal(), "Chart1", "color",
                                new FieldRef("Region", "dimension", null, null, null), null, ""));
      String message = thrown.getMessage().toLowerCase();
      assertTrue(message.contains("date comparison"), thrown.getMessage());
      assertTrue(message.contains("clear_date_comparison"), thrown.getMessage());
      assertFalse(message.contains("turn multi-style off"),
                 "the dead-end remediation must not be offered as the fix for the DC-forced " +
                    "case: " + thrown.getMessage());
   }

   /**
    * The ordinary user-toggled-{@code multi} case still gets its original message: only the
    * DC-forced branch (an active date comparison with the design flag off) should repoint the
    * caller at {@code clear_date_comparison}.
    */
   @Test
   void refusesSettingAFieldOnAnOrdinaryMultiStyleChartWithTheOriginalMessage() {
      ChartAestheticAgentService service = serviceWith(
         sessionsFor(multiStyleChartAssembly()), new ChartBindingModel(),
         mock(ChangeChartAestheticService.class));

      Exception thrown = assertThrows(
         Exception.class,
         () -> service.setField("tok", principal(), "Chart1", "color",
                                new FieldRef("Region", "dimension", null, null, null), null, ""));
      String message = thrown.getMessage().toLowerCase();
      assertTrue(message.contains("set_chart_type"), thrown.getMessage());
      assertFalse(message.contains("date comparison"), thrown.getMessage());
   }

   private static ChartVSAssembly dateComparisonForcedMultiStyleChartAssembly() {
      VSChartInfo info = mock(VSChartInfo.class);
      when(info.isMultiAesthetic()).thenReturn(true);
      when(info.isAppliedDateComparison()).thenReturn(true);
      when(info.isDesignMultiStyles()).thenReturn(false);
      ChartVSAssembly chart = mock(ChartVSAssembly.class);
      when(chart.getVSChartInfo()).thenReturn(info);
      return chart;
   }

   // ── refuses a write-then-revert onto a date-comparison-injected channel (bug #77014 VCA-004)
   //
   // clear_aesthetic_field and set_visual_frame both genuinely applied their write against the
   // live model, but the very refresh their own request triggered discarded it and reinstalled
   // date comparison's own default color-by-default injection in the same request -- the
   // response said the write succeeded; the very next read showed it never stuck. A single-style
   // comparison (value/change/percentChange) never turns isMultiAesthetic() true, so DCG-013's
   // own guard above never fired for it; this checks the channel's own current ref instead.

   @Test
   void refusesClearingADateComparisonInjectedColorField() {
      ChartAestheticAgentService service = serviceWith(
         sessionsFor(dateComparisonInjectedColorFieldChartAssembly()), new ChartBindingModel(),
         mock(ChangeChartAestheticService.class));

      Exception thrown = assertThrows(
         Exception.class,
         () -> service.clearField("tok", principal(), "Chart1", "color", ""));
      String message = thrown.getMessage().toLowerCase();
      assertTrue(message.contains("date comparison"), thrown.getMessage());
      assertTrue(message.contains("clear_date_comparison"), thrown.getMessage());
   }

   @Test
   void refusesSettingAFieldOntoADateComparisonInjectedColorChannel() {
      ChartAestheticAgentService service = serviceWith(
         sessionsFor(dateComparisonInjectedColorFieldChartAssembly()), new ChartBindingModel(),
         mock(ChangeChartAestheticService.class));

      Exception thrown = assertThrows(
         Exception.class,
         () -> service.setField("tok", principal(), "Chart1", "color",
                                new FieldRef("Region", "dimension", null, null, null), null, ""));
      assertTrue(thrown.getMessage().toLowerCase().contains("clear_date_comparison"),
                thrown.getMessage());
   }

   /**
    * {@code setFrame} had no such guard at all before this fix, regardless of single- or
    * multi-style -- confirmed by tracing that {@code requireNotMultiAesthetic} is never called
    * from it.
    */
   @Test
   void refusesSettingAFrameOntoADateComparisonInjectedColorChannel() {
      ChartAestheticAgentService service = serviceWith(
         sessionsFor(dateComparisonInjectedColorFieldChartAssembly()), new ChartBindingModel(),
         mock(ChangeChartAestheticService.class));

      Exception thrown = assertThrows(
         Exception.class,
         () -> service.setFrame("tok", principal(), "Chart1", "color",
                                spec("type", "static", "color", "#4e79a7"), ""));
      assertTrue(thrown.getMessage().toLowerCase().contains("clear_date_comparison"),
                thrown.getMessage());
   }

   /** A per-aggregate injection (a multi-style changeAndValue/percentChangeAndValue comparison). */
   @Test
   void refusesSettingAFrameOntoAMultiStyleDateComparisonInjectedColorChannel() {
      ChartAestheticAgentService service = serviceWith(
         sessionsFor(dateComparisonMultiStyleInjectedColorFieldChartAssembly()),
         new ChartBindingModel(), mock(ChangeChartAestheticService.class));

      Exception thrown = assertThrows(
         Exception.class,
         () -> service.setFrame("tok", principal(), "Chart1", "color",
                                spec("type", "static", "color", "#4e79a7"), ""));
      assertTrue(thrown.getMessage().toLowerCase().contains("clear_date_comparison"),
                thrown.getMessage());
   }

   /**
    * The refusal is scoped to the one channel date comparison actually populated -- an ordinary
    * explicit binding on a different channel of the same chart must keep working.
    */
   @Test
   void allowsAnOrdinaryChannelWriteWhileADateComparisonInjectsAnotherOne() throws Exception {
      ChangeChartAestheticService aesthetics = mock(ChangeChartAestheticService.class);

      serviceWith(sessionsFor(dateComparisonInjectedColorFieldChartAssembly()),
                 new ChartBindingModel(), aesthetics)
         .setField("tok", principal(), "Chart1", "shape",
                  new FieldRef("Region", "dimension", null, null, null), null, "");

      assertEquals("Region", captureEvent(aesthetics).getModel().getShapeField().getFullName());
   }

   /** No active comparison at all -- every existing write path must be unaffected. */
   @Test
   void allowsAWriteWhenNoDateComparisonIsApplied() throws Exception {
      ChangeChartAestheticService aesthetics = mock(ChangeChartAestheticService.class);

      harness(new ChartBindingModel(), aesthetics)
         .setFrame("tok", principal(), "Chart1", "color",
                  spec("type", "static", "color", "#4e79a7"), "");

      assertInstanceOf(StaticColorModel.class,
                       captureEvent(aesthetics).getModel().getColorFrame());
   }

   /**
    * reset_visual_frame writes {@code field.setFrame(...)} on the very same live ref, so it
    * reverts on the next refresh exactly like set_visual_frame (PR #5740 review).
    */
   @Test
   void refusesResettingAFrameOnADateComparisonInjectedColorChannel() {
      ChartAestheticAgentService service = serviceWith(
         sessionsFor(dateComparisonInjectedColorFieldChartAssembly()), new ChartBindingModel(),
         mock(ChangeChartAestheticService.class));

      Exception thrown = assertThrows(
         Exception.class,
         () -> service.resetFrame("tok", principal(), "Chart1", "color", null, ""));
      assertTrue(thrown.getMessage().toLowerCase().contains("clear_date_comparison"),
                thrown.getMessage());
   }

   /**
    * line/texture read their frame from the shape field when it carries their family, and date
    * comparison can put its own runtime ref there: {@code ChartDcProcessor.updateAestheticField}
    * moves an explicitly bound color field onto a new runtime shape ref when shape is empty.
    */
   @Test
   void refusesALineFrameWriteWhileADateComparisonShapeCarriesTheLineFrame() {
      ChartAestheticAgentService service = serviceWith(
         sessionsFor(dateComparisonInjectedShapeFieldChartAssembly(mock(LineFrame.class))),
         new ChartBindingModel(), mock(ChangeChartAestheticService.class));

      Exception thrown = assertThrows(
         Exception.class,
         () -> service.setFrame("tok", principal(), "Chart1", "line",
                                spec("type", "static", "line", 1), ""));
      assertTrue(thrown.getMessage().toLowerCase().contains("clear_date_comparison"),
                thrown.getMessage());
   }

   @Test
   void refusesATextureFrameResetWhileADateComparisonShapeCarriesTheTextureFrame() {
      ChartAestheticAgentService service = serviceWith(
         sessionsFor(dateComparisonInjectedShapeFieldChartAssembly(mock(TextureFrame.class))),
         new ChartBindingModel(), mock(ChangeChartAestheticService.class));

      Exception thrown = assertThrows(
         Exception.class,
         () -> service.resetFrame("tok", principal(), "Chart1", "texture", null, ""));
      assertTrue(thrown.getMessage().toLowerCase().contains("clear_date_comparison"),
                thrown.getMessage());
   }

   /**
    * A runtime shape field holding a point {@code ShapeFrame} does not drive line: that write goes
    * to the field-less slot, which the refresh leaves alone, so it must keep working.
    */
   @Test
   void allowsALineFrameWriteWhenTheInjectedShapeCarriesAPointShapeFrame() throws Exception {
      ChangeChartAestheticService aesthetics = mock(ChangeChartAestheticService.class);

      serviceWith(sessionsFor(dateComparisonInjectedShapeFieldChartAssembly(mock(ShapeFrame.class))),
                  new ChartBindingModel(), aesthetics)
         .setFrame("tok", principal(), "Chart1", "line", spec("type", "static", "line", 1), "");

      assertNotNull(captureEvent(aesthetics).getModel().getLineFrame());
   }

   /**
    * {@code ChartDcProcessor} injects into {@code getAestheticAggregateRefs(true)}, which on a
    * Gantt chart also carries the start/end/milestone refs {@code getAggregateRefs()} does not.
    */
   @Test
   void refusesAFrameWriteOnAnInjectedAestheticOnlyAggregate() {
      AestheticRef colorField = mock(AestheticRef.class);
      when(colorField.isRuntime()).thenReturn(true);
      ChartAggregateRef aggregate = mock(ChartAggregateRef.class);
      when(aggregate.getColorField()).thenReturn(colorField);
      VSChartInfo info = mock(VSChartInfo.class);
      when(info.isAppliedDateComparison()).thenReturn(true);
      when(info.getAestheticAggregateRefs(true)).thenReturn(new ArrayList<>(List.of(aggregate)));
      ChartVSAssembly chart = mock(ChartVSAssembly.class);
      when(chart.getVSChartInfo()).thenReturn(info);
      ChartAestheticAgentService service = serviceWith(
         sessionsFor(chart), new ChartBindingModel(), mock(ChangeChartAestheticService.class));

      Exception thrown = assertThrows(
         Exception.class,
         () -> service.setFrame("tok", principal(), "Chart1", "color",
                                spec("type", "static", "color", "#4e79a7"), ""));
      assertTrue(thrown.getMessage().toLowerCase().contains("clear_date_comparison"),
                thrown.getMessage());
   }

   private static ChartVSAssembly dateComparisonInjectedShapeFieldChartAssembly(
      VisualFrame shapeFrame)
   {
      AestheticRef shapeField = mock(AestheticRef.class);
      when(shapeField.isRuntime()).thenReturn(true);
      when(shapeField.getVisualFrame()).thenReturn(shapeFrame);
      VSChartInfo info = mock(VSChartInfo.class);
      when(info.isAppliedDateComparison()).thenReturn(true);
      when(info.getShapeField()).thenReturn(shapeField);
      ChartVSAssembly chart = mock(ChartVSAssembly.class);
      when(chart.getVSChartInfo()).thenReturn(info);
      return chart;
   }

   private static ChartVSAssembly dateComparisonInjectedColorFieldChartAssembly() {
      AestheticRef colorField = mock(AestheticRef.class);
      when(colorField.isRuntime()).thenReturn(true);
      VSChartInfo info = mock(VSChartInfo.class);
      when(info.isAppliedDateComparison()).thenReturn(true);
      when(info.getColorField()).thenReturn(colorField);
      ChartVSAssembly chart = mock(ChartVSAssembly.class);
      when(chart.getVSChartInfo()).thenReturn(info);
      return chart;
   }

   private static ChartVSAssembly dateComparisonMultiStyleInjectedColorFieldChartAssembly() {
      AestheticRef colorField = mock(AestheticRef.class);
      when(colorField.isRuntime()).thenReturn(true);
      ChartAggregateRef aggregate = mock(ChartAggregateRef.class);
      when(aggregate.getColorField()).thenReturn(colorField);
      VSChartInfo info = mock(VSChartInfo.class);
      when(info.isAppliedDateComparison()).thenReturn(true);
      when(info.getAggregateRefs()).thenReturn(new VSDataRef[]{ aggregate });
      ChartVSAssembly chart = mock(ChartVSAssembly.class);
      when(chart.getVSChartInfo()).thenReturn(info);
      return chart;
   }

   private static ChartVSAssembly relationChartAssembly() {
      VSChartInfo info = mock(VSChartInfo.class);
      when(info.getChartType()).thenReturn(GraphTypes.CHART_NETWORK);
      ChartVSAssembly chart = mock(ChartVSAssembly.class);
      when(chart.getVSChartInfo()).thenReturn(info);
      return chart;
   }

   // ── harness ───────────────────────────────────────────────────────────────

   private static ChangeChartRefEvent captureEvent(ChangeChartAestheticService aesthetics)
      throws Exception
   {
      ArgumentCaptor<ChangeChartRefEvent> captor =
         ArgumentCaptor.forClass(ChangeChartRefEvent.class);
      verify(aesthetics).changeChartAesthetic(eq("rt1"), captor.capture(), any(Principal.class),
                                              any(), anyString());
      return captor.getValue();
   }

   private static ChartAestheticAgentService harness(ChartBindingModel model,
                                                ChangeChartAestheticService aesthetics)
   {
      return serviceWith(sessionsFor(mock(ChartVSAssembly.class)), model, aesthetics);
   }

   /**
    * A session service whose mutate() runs the mutation immediately against runtime "rt1", so
    * these tests exercise the read-modify-write without a live runtime.
    */
   private static ViewsheetSessionService sessionsFor(VSAssembly assembly) {
      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getAssembly(anyString())).thenReturn(assembly);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);

      ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);

      try {
         doAnswer(invocation -> {
            ViewsheetSessionService.Mutation mutation = invocation.getArgument(2);
            mutation.run(rvs, "rt1", null);
            return null;
         }).when(sessions).mutate(anyString(), any(Principal.class), any());
         when(sessions.resolve(anyString(), any(Principal.class))).thenReturn(rvs);
      }
      catch(Exception e) {
         throw new IllegalStateException(e);
      }

      return sessions;
   }

   private static ChartAestheticAgentService serviceWith(ViewsheetSessionService sessions,
                                                    ChartBindingModel model,
                                                    ChangeChartAestheticService aesthetics)
   {
      VSBindingService binding = mock(VSBindingService.class);
      when(binding.createModel(any())).thenReturn(model);
      return new ChartAestheticAgentService(sessions, binding, aesthetics,
                                            mock(inetsoft.web.binding.service.DataRefModelFactoryService.class));
   }

   private static Principal principal() {
      return () -> "admin";
   }

   /**
    * frameTypes was a hard-coded four — static, categorical, gradient, palette — from the one
    * endpoint whose job is to stop an agent guessing. It named eleven fewer types than
    * VisualFrameAliases builds, and implied the answer does not depend on the channel when
    * gradient exists only for colour and linear only for size and line.
    */
   @Test
   void optionsReportsFrameTypesPerChannelFromTheBuilderItself() {
      ChartAestheticAgentService service = serviceWith(
         sessionsFor(mock(ChartVSAssembly.class)), new ChartBindingModel(),
         mock(ChangeChartAestheticService.class));

      Map<String, Object> options = service.options();
      @SuppressWarnings("unchecked")
      Map<String, Object> byChannel = (Map<String, Object>) options.get("frameTypesByChannel");

      for(String channel : AestheticChannels.SUPPORTED_FRAME_CHANNELS) {
         assertEquals(VisualFrameAliases.typeNames(channel), byChannel.get(channel),
                      "the reported types for " + channel + " must be the builder's own");
      }

      assertEquals(VisualFrameAliases.typeNames("color"), byChannel.get("node-color"));
      assertEquals(VisualFrameAliases.typeNames("size"), byChannel.get("node-size"));
   }

   @Test
   void optionsFrameTypesIsTheUnionAcrossChannels() {
      ChartAestheticAgentService service = serviceWith(
         sessionsFor(mock(ChartVSAssembly.class)), new ChartBindingModel(),
         mock(ChangeChartAestheticService.class));

      @SuppressWarnings("unchecked")
      List<String> types = (List<String>) service.options().get("frameTypes");

      assertTrue(types.containsAll(List.of("heat", "linear", "grid", "triangle", "rainbow")),
                 "types the builder accepts but the old hard-coded list refused: " + types);
      assertTrue(types.containsAll(List.of("static", "categorical", "gradient", "palette")),
                 "and the four it did report are still there: " + types);
   }
}
