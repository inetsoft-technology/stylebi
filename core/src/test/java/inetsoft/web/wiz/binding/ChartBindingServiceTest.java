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
import inetsoft.uql.viewsheet.TextVSAssembly;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.graph.ChartDescriptor;
import inetsoft.uql.viewsheet.graph.GraphTypes;
import inetsoft.uql.viewsheet.graph.PlotDescriptor;
import inetsoft.uql.viewsheet.graph.VSChartAggregateRef;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import inetsoft.web.binding.controller.ChangeChartRefService;
import inetsoft.web.binding.controller.ChangeChartTypeService;
import inetsoft.web.binding.controller.ChangeSeparateStatusService;
import inetsoft.web.binding.controller.SwapXYBindingService;
import inetsoft.web.binding.event.ChangeChartRefEvent;
import inetsoft.web.binding.event.ChangeChartTypeEvent;
import inetsoft.web.binding.event.ChangeSeparateStatusEvent;
import inetsoft.web.binding.model.ChartBindingModel;
import inetsoft.web.binding.model.graph.ChartAggregateRefModel;
import inetsoft.web.binding.model.graph.ChartDimensionRefModel;
import inetsoft.web.binding.service.VSBindingService;
import inetsoft.web.wiz.binding.model.ChartTypeState;
import inetsoft.web.wiz.binding.model.FieldRef;
import inetsoft.web.wiz.viewsheet.ViewsheetSessionService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("core")
class ChartBindingServiceTest {
   @Test
   void setShelfPostsTheModelItReadRatherThanAFreshOne() throws Exception {
      ChartBindingModel existing = new ChartBindingModel();
      // A value only the read model carries. If the service constructs a fresh model, this
      // is lost — which is exactly how the thirteen aesthetic fields would be lost too.
      existing.setChartType(42);
      ChangeChartRefService refs = mock(ChangeChartRefService.class);

      harness(existing, refs, mock(ChangeChartTypeService.class), mock(SwapXYBindingService.class),
              mock(ChangeSeparateStatusService.class))
         .setShelf("tok", principal(), "Chart1", "x",
                   List.of(new FieldRef("Region", "dimension", null, null, null)), null, "");

      ArgumentCaptor<ChangeChartRefEvent> captor =
         ArgumentCaptor.forClass(ChangeChartRefEvent.class);
      verify(refs).changeChartRef(eq("rt1"), captor.capture(), any(Principal.class), any(),
                                  anyString());
      assertEquals(42, captor.getValue().getModel().getChartType(),
                   "the posted model must be the one read, not a fresh construction");
      assertEquals(1, captor.getValue().getModel().getXFields().size());
      assertEquals("Chart1", captor.getValue().getName());
   }

   @Test
   void setShelfLeavesTheAestheticFieldsUntouched() throws Exception {
      ChartBindingModel existing = new ChartBindingModel();
      Map<String, Object> before = ChartBindingFields.snapshotAesthetics(existing);
      ChangeChartRefService refs = mock(ChangeChartRefService.class);

      harness(existing, refs, mock(ChangeChartTypeService.class), mock(SwapXYBindingService.class),
              mock(ChangeSeparateStatusService.class))
         .setShelf("tok", principal(), "Chart1", "y",
                   List.of(new FieldRef("Sales", "measure", "Sum", null, null)), null, "");

      ArgumentCaptor<ChangeChartRefEvent> captor =
         ArgumentCaptor.forClass(ChangeChartRefEvent.class);
      verify(refs).changeChartRef(eq("rt1"), captor.capture(), any(Principal.class), any(),
                                  anyString());
      assertEquals(before, ChartBindingFields.snapshotAesthetics(captor.getValue().getModel()),
                   "a shelf write must not disturb the aesthetic fields spec 2c owns");
   }

   @Test
   void setChartTypeDelegatesTheType() throws Exception {
      ChangeChartTypeService types = mock(ChangeChartTypeService.class);

      harness(new ChartBindingModel(), mock(ChangeChartRefService.class), types,
              mock(SwapXYBindingService.class), mock(ChangeSeparateStatusService.class))
         .setChartType("tok", principal(), "Chart1", 3, null, null, null, null, "");

      ArgumentCaptor<ChangeChartTypeEvent> captor =
         ArgumentCaptor.forClass(ChangeChartTypeEvent.class);
      verify(types).changeChartType(eq("rt1"), captor.capture(), any(Principal.class), any(),
                                    anyString());
      assertEquals(3, captor.getValue().getType());
      assertEquals("Chart1", captor.getValue().getName());
   }

   @Test
   void swapAxesDelegatesTheAssemblyName() throws Exception {
      SwapXYBindingService swap = mock(SwapXYBindingService.class);

      harness(new ChartBindingModel(), mock(ChangeChartRefService.class),
              mock(ChangeChartTypeService.class), swap, mock(ChangeSeparateStatusService.class))
         .swapAxes("tok", principal(), "Chart1", "");

      ArgumentCaptor<ChangeSeparateStatusEvent> captor =
         ArgumentCaptor.forClass(ChangeSeparateStatusEvent.class);
      verify(swap).swapXYBinding(eq("rt1"), captor.capture(), any(Principal.class), any(),
                                 anyString());
      assertEquals("Chart1", captor.getValue().getName());
   }

   /**
    * set_chart_type's own separate parameter is a silent no-op unless multi also changes in the
    * same call (ChangeChartTypeService.handleMulti gates on omulti != nmulti). This tool calls
    * ChangeSeparateStatusService directly instead, and reads the chart's current multi state so
    * the caller does not have to restate it.
    */
   @Test
   void setSeparateStatusCallsTheDedicatedEndpointWithTheCurrentMultiState() throws Exception {
      ChangeSeparateStatusService separateStatus = mock(ChangeSeparateStatusService.class);
      ChartVSAssembly chart = mock(ChartVSAssembly.class);
      inetsoft.uql.viewsheet.graph.VSChartInfo chartInfo =
         mock(inetsoft.uql.viewsheet.graph.VSChartInfo.class);
      when(chart.getVSChartInfo()).thenReturn(chartInfo);
      when(chartInfo.isMultiStyles()).thenReturn(true);

      harnessWithAssembly(chart, new ChartBindingModel(), mock(ChangeChartRefService.class),
                          mock(ChangeChartTypeService.class), mock(SwapXYBindingService.class),
                          separateStatus)
         .setSeparateStatus("tok", principal(), "Chart1", true, "");

      ArgumentCaptor<ChangeSeparateStatusEvent> captor =
         ArgumentCaptor.forClass(ChangeSeparateStatusEvent.class);
      verify(separateStatus).changeSeparateStatus(eq("rt1"), captor.capture(),
                                                  any(Principal.class), any(), anyString());
      assertEquals("Chart1", captor.getValue().getName());
      assertTrue(captor.getValue().isMulti(),
                 "must read the chart's own current multi state, not invent one");
      assertTrue(captor.getValue().isSeparate());
   }

   /**
    * ChangeSeparateStatusService forces separated=true unconditionally for these types
    * (event.isSeparate() is OR'd with the type checks, so it can only push the result toward
    * true, never back to false). Reporting "merged" for a chart that stayed separated would be
    * the exact plausible-but-wrong-result shape set_chart_type's own 'separate' gap had —
    * refuse before calling the endpoint at all rather than let that happen.
    */
   @Test
   void refusesToMergeAChartTypeThatIsAlwaysSeparated() throws Exception {
      ChangeSeparateStatusService separateStatus = mock(ChangeSeparateStatusService.class);
      ChartVSAssembly chart = mock(ChartVSAssembly.class);
      inetsoft.uql.viewsheet.graph.VSChartInfo chartInfo =
         mock(inetsoft.uql.viewsheet.graph.VSChartInfo.class);
      when(chart.getVSChartInfo()).thenReturn(chartInfo);
      when(chartInfo.getChartType())
         .thenReturn(inetsoft.uql.viewsheet.graph.GraphTypes.CHART_TREEMAP);

      ChartBindingService service = harnessWithAssembly(chart, new ChartBindingModel(),
         mock(ChangeChartRefService.class), mock(ChangeChartTypeService.class),
         mock(SwapXYBindingService.class), separateStatus);

      Exception thrown = assertThrows(Exception.class,
         () -> service.setSeparateStatus("tok", principal(), "Chart1", false, ""));

      assertTrue(thrown.getMessage().contains("treemap"));
      verifyNoInteractions(separateStatus);
   }

   @Test
   void allowsRequestingSeparateOnAChartTypeThatIsAlwaysSeparated() throws Exception {
      ChangeSeparateStatusService separateStatus = mock(ChangeSeparateStatusService.class);
      ChartVSAssembly chart = mock(ChartVSAssembly.class);
      inetsoft.uql.viewsheet.graph.VSChartInfo chartInfo =
         mock(inetsoft.uql.viewsheet.graph.VSChartInfo.class);
      when(chart.getVSChartInfo()).thenReturn(chartInfo);
      when(chartInfo.getChartType())
         .thenReturn(inetsoft.uql.viewsheet.graph.GraphTypes.CHART_MEKKO);

      // separate: true agrees with what the server would force anyway, so this must succeed --
      // only the contradicting request (separate: false) is refused.
      harnessWithAssembly(chart, new ChartBindingModel(), mock(ChangeChartRefService.class),
         mock(ChangeChartTypeService.class), mock(SwapXYBindingService.class), separateStatus)
         .setSeparateStatus("tok", principal(), "Chart1", true, "");

      verify(separateStatus).changeSeparateStatus(eq("rt1"), any(), any(Principal.class), any(),
                                                  anyString());
   }

   @Test
   void refusesANonChartAssemblyNamingIt() {
      ChartBindingService service = harnessWithAssembly(
         mock(TextVSAssembly.class), new ChartBindingModel(),
         mock(ChangeChartRefService.class), mock(ChangeChartTypeService.class),
         mock(SwapXYBindingService.class), mock(ChangeSeparateStatusService.class));

      Exception thrown = assertThrows(
         Exception.class,
         () -> service.swapAxes("tok", principal(), "Text1", ""));
      assertTrue(thrown.getMessage().contains("Text1"));
   }

   // ── set_chart_source ──────────────────────────────────────────────────────
   //
   // A chart added in the Composer starts with no source. Its shelves can be populated —
   // set_chart_shelf reports success, get_binding reads the fields back correctly — and it renders
   // nothing at all, because shelves with no source have nothing to query. The Composer assigns one
   // as a side effect of the drag (VSChartDndService takes it from the drag event's table); the
   // agent's FieldRef carries no table, so nothing on this path could assign one.

   @Test
   void setSourceAssignsAnAssetSourceByName() throws Exception {
      ChartBindingModel existing = chartWithTables("ORDERS1", "ORDER_DETAILS1");
      ChangeChartRefService refs = mock(ChangeChartRefService.class);

      harness(existing, refs, mock(ChangeChartTypeService.class),
              mock(SwapXYBindingService.class), mock(ChangeSeparateStatusService.class))
         .setSource("tok", principal(), "Chart1", "ORDERS1", false, "");

      ArgumentCaptor<ChangeChartRefEvent> captor =
         ArgumentCaptor.forClass(ChangeChartRefEvent.class);
      verify(refs).changeChartRef(eq("rt1"), captor.capture(), any(Principal.class), any(),
                                  anyString());
      ChartBindingModel posted = captor.getValue().getModel();
      assertNotNull(posted.getSource(), "the model must carry a source after the write");
      assertEquals("ORDERS1", posted.getSource().getSource());
      assertEquals(inetsoft.uql.asset.SourceInfo.ASSET, posted.getSource().getType(),
                   "worksheet tables bind as ASSET — the form the DnD path uses too");
   }

   @Test
   void setSourceRefusesATableTheChartCannotSee() {
      ChartBindingModel existing = chartWithTables("ORDERS1", "ORDER_DETAILS1");

      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> harness(existing, mock(ChangeChartRefService.class),
                       mock(ChangeChartTypeService.class), mock(SwapXYBindingService.class),
                       mock(ChangeSeparateStatusService.class))
            .setSource("tok", principal(), "Chart1", "NOPE", false, ""));

      assertTrue(thrown.getMessage().contains("NOPE"));
      assertTrue(thrown.getMessage().contains("ORDERS1"), "list what it can bind to");
   }

   /**
    * Repointing discards the bound fields, because they belong to the old source — the Composer
    * does exactly that, via {@code validateChartColumns}. Doing it silently on one call is the
    * failure this plugin family exists to avoid, so it takes {@code force}.
    */
   @Test
   void setSourceRefusesToDiscardBoundFieldsUnlessForced() {
      ChartBindingModel existing = chartWithTables("ORDERS1", "ORDER_DETAILS1");
      existing.setSource(assetSource("ORDERS1"));
      existing.setXFields(List.of(new ChartDimensionRefModel()));

      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> harness(existing, mock(ChangeChartRefService.class),
                       mock(ChangeChartTypeService.class), mock(SwapXYBindingService.class),
                       mock(ChangeSeparateStatusService.class))
            .setSource("tok", principal(), "Chart1", "ORDER_DETAILS1", false, ""));

      assertTrue(thrown.getMessage().contains("force"), "name the way through");
   }

   /**
    * The chart-specific half of that check: fields live in <b>thirteen</b> places, not three.
    *
    * <p>A candlestick's whole binding is on the single-field shelves and its x/y are empty, so a
    * check that counted only x/y/group would report "nothing bound" and repoint without asking —
    * discarding the entire binding of exactly the charts whose binding is hardest to rebuild.
    */
   @Test
   void setSourceCountsTheSingleFieldShelvesNotJustXYAndGroup() {
      ChartBindingModel existing = chartWithTables("ORDERS1", "ORDER_DETAILS1");
      existing.setSource(assetSource("ORDERS1"));
      existing.setCloseField(new ChartAggregateRefModel());

      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> harness(existing, mock(ChangeChartRefService.class),
                       mock(ChangeChartTypeService.class), mock(SwapXYBindingService.class),
                       mock(ChangeSeparateStatusService.class))
            .setSource("tok", principal(), "Chart1", "ORDER_DETAILS1", false, ""));

      assertTrue(thrown.getMessage().contains("force"));
   }

   /**
    * The aesthetic half: a chart can be bound entirely through a channel, with every shelf empty.
    *
    * <p>A word cloud is nothing but a text channel. {@code validateAestheticFields} clears exactly
    * these on a repoint, so a check that read only the shelves would report "nothing bound" for a
    * fully bound chart and discard its one binding without asking.
    */
   @Test
   void setSourceCountsTheAestheticChannelsNotJustTheShelves() {
      ChartBindingModel existing = chartWithTables("ORDERS1", "ORDER_DETAILS1");
      existing.setSource(assetSource("ORDERS1"));
      ChartAestheticMutator.setField(existing, "text",
                                     new FieldRef("Product", "dimension", null, null, null));

      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> harness(existing, mock(ChangeChartRefService.class),
                       mock(ChangeChartTypeService.class), mock(SwapXYBindingService.class),
                       mock(ChangeSeparateStatusService.class))
            .setSource("tok", principal(), "Chart1", "ORDER_DETAILS1", false, ""));

      assertTrue(thrown.getMessage().contains("force"), "name the way through");
      assertTrue(thrown.getMessage().contains("text"), "name which channel would be lost");
   }

   /**
    * A relation chart's node channels are the same story on a second pair of properties, and
    * {@code validateAestheticFields} clears them too.
    */
   @Test
   void setSourceCountsARelationChartsNodeChannels() {
      ChartBindingModel existing = chartWithTables("ORDERS1", "ORDER_DETAILS1");
      existing.setSource(assetSource("ORDERS1"));
      ChartAestheticMutator.setField(existing, "node-color",
                                     new FieldRef("Region", "dimension", null, null, null), true);

      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> harness(existing, mock(ChangeChartRefService.class),
                       mock(ChangeChartTypeService.class), mock(SwapXYBindingService.class),
                       mock(ChangeSeparateStatusService.class))
            .setSource("tok", principal(), "Chart1", "ORDER_DETAILS1", false, ""));

      assertTrue(thrown.getMessage().contains("force"));
      assertTrue(thrown.getMessage().contains("node-color"));
   }

   /**
    * A map keeps its geography on {@code geoFields}, which is no shelf and no channel —
    * {@code validateChartColumns} removes those on a repoint alongside everything else.
    */
   @Test
   void setSourceCountsAMapsGeoFields() {
      ChartBindingModel existing = chartWithTables("ORDERS1", "ORDER_DETAILS1");
      existing.setSource(assetSource("ORDERS1"));
      existing.setGeoFields(List.of(new ChartDimensionRefModel()));

      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> harness(existing, mock(ChangeChartRefService.class),
                       mock(ChangeChartTypeService.class), mock(SwapXYBindingService.class),
                       mock(ChangeSeparateStatusService.class))
            .setSource("tok", principal(), "Chart1", "ORDER_DETAILS1", false, ""));

      assertTrue(thrown.getMessage().contains("force"));
      assertTrue(thrown.getMessage().contains("geo"));
   }

   /** An aesthetic-only binding is discardable on the same terms as a shelf one: with force. */
   @Test
   void setSourceProceedsWhenForcedOverAnAestheticOnlyBinding() throws Exception {
      ChartBindingModel existing = chartWithTables("ORDERS1", "ORDER_DETAILS1");
      existing.setSource(assetSource("ORDERS1"));
      ChartAestheticMutator.setField(existing, "text",
                                     new FieldRef("Product", "dimension", null, null, null));
      ChangeChartRefService refs = mock(ChangeChartRefService.class);

      harness(existing, refs, mock(ChangeChartTypeService.class),
              mock(SwapXYBindingService.class), mock(ChangeSeparateStatusService.class))
         .setSource("tok", principal(), "Chart1", "ORDER_DETAILS1", true, "");

      ArgumentCaptor<ChangeChartRefEvent> captor =
         ArgumentCaptor.forClass(ChangeChartRefEvent.class);
      verify(refs).changeChartRef(eq("rt1"), captor.capture(), any(Principal.class), any(),
                                  anyString());
      ChartBindingModel posted = captor.getValue().getModel();
      assertEquals("ORDER_DETAILS1", posted.getSource().getSource());
      assertNotNull(posted.getTextField(),
                    "the repoint must not clear the channel itself — validateChartColumns " +
                    "decides what survives, this write only moves the source");
   }

   /**
    * A chart with no binding at all still repoints without {@code force} — the check must not
    * become "anything non-null anywhere", or the very case {@code set_chart_source} exists for
    * (a fresh chart that has no source yet) would be refused.
    */
   @Test
   void setSourceOnAnUnboundChartNeedsNoForce() throws Exception {
      ChartBindingModel existing = chartWithTables("ORDERS1", "ORDER_DETAILS1");
      existing.setSource(assetSource("ORDERS1"));
      ChangeChartRefService refs = mock(ChangeChartRefService.class);

      harness(existing, refs, mock(ChangeChartTypeService.class),
              mock(SwapXYBindingService.class), mock(ChangeSeparateStatusService.class))
         .setSource("tok", principal(), "Chart1", "ORDER_DETAILS1", false, "");

      verify(refs).changeChartRef(eq("rt1"), any(), any(Principal.class), any(), anyString());
   }

   @Test
   void setSourceProceedsWhenForced() throws Exception {
      ChartBindingModel existing = chartWithTables("ORDERS1", "ORDER_DETAILS1");
      existing.setSource(assetSource("ORDERS1"));
      existing.setXFields(List.of(new ChartDimensionRefModel()));
      ChangeChartRefService refs = mock(ChangeChartRefService.class);

      harness(existing, refs, mock(ChangeChartTypeService.class),
              mock(SwapXYBindingService.class), mock(ChangeSeparateStatusService.class))
         .setSource("tok", principal(), "Chart1", "ORDER_DETAILS1", true, "");

      ArgumentCaptor<ChangeChartRefEvent> captor =
         ArgumentCaptor.forClass(ChangeChartRefEvent.class);
      verify(refs).changeChartRef(eq("rt1"), captor.capture(), any(Principal.class), any(),
                                  anyString());
      assertEquals("ORDER_DETAILS1", captor.getValue().getModel().getSource().getSource());
   }

   /**
    * Re-stating the source a chart already has discards nothing, so it must not demand
    * {@code force}. Without this an agent that re-reads and re-applies its own intended state —
    * the read-first habit this plugin asks for — gets refused for making no change.
    */
   @Test
   void setSourceToTheSameTableDoesNotDemandForce() throws Exception {
      ChartBindingModel existing = chartWithTables("ORDERS1", "ORDER_DETAILS1");
      existing.setSource(assetSource("ORDERS1"));
      existing.setXFields(List.of(new ChartDimensionRefModel()));
      ChangeChartRefService refs = mock(ChangeChartRefService.class);

      harness(existing, refs, mock(ChangeChartTypeService.class),
              mock(SwapXYBindingService.class), mock(ChangeSeparateStatusService.class))
         .setSource("tok", principal(), "Chart1", "ORDERS1", false, "");

      verify(refs).changeChartRef(eq("rt1"), any(), any(Principal.class), any(), anyString());
   }

   private static inetsoft.web.binding.model.SourceInfo assetSource(String table) {
      inetsoft.web.binding.model.SourceInfo source = new inetsoft.web.binding.model.SourceInfo();
      source.setType(inetsoft.uql.asset.SourceInfo.ASSET);
      source.setSource(table);
      source.setView(table);

      return source;
   }

   private static ChartBindingModel chartWithTables(String... names) {
      ChartBindingModel model = new ChartBindingModel();
      List<inetsoft.web.binding.model.BindingModel.SourceTable> tables = new ArrayList<>();

      for(String name : names) {
         inetsoft.web.binding.model.BindingModel.SourceTable table =
            new inetsoft.web.binding.model.BindingModel.SourceTable();
         table.setName(name);
         tables.add(table);
      }

      model.setTables(tables);

      return model;
   }

   /**
    * Multi Style's whole point is a per-measure type, and no tool could set one: set_chart_type was
    * assembly-scoped, so `multi: true` switched on a mode with nothing behind it.
    *
    * <p>The backend already carries it — {@code ChangeChartTypeEvent.ref} names an aggregate and
    * {@code ChangeChartTypeProcessor} sets that ref's type instead of the assembly's — and the agent
    * path simply never filled the field in. Same shape as the single-shelf read: the capability
    * exists and is exercised by the Composer, one tier just did not pass it along.
    */
   @Test
   void namesOneMeasureWhenAskedToTypeJustThatField() throws Exception {
      ChangeChartTypeService types = mock(ChangeChartTypeService.class);

      multiStyleChart(types)
         .setChartType("tok", principal(), "Chart1", GraphTypes.CHART_LINE, null, null, null,
                       "Sum(DISCOUNT)", "");

      ArgumentCaptor<ChangeChartTypeEvent> event =
         ArgumentCaptor.forClass(ChangeChartTypeEvent.class);
      verify(types).changeChartType(eq("rt1"), event.capture(), any(Principal.class), any(),
                                    anyString());
      assertEquals("Sum(DISCOUNT)", event.getValue().getRef(),
                   "the field the caller named must reach the event");
   }

   /** No field named means the whole chart, exactly as before. */
   @Test
   void leavesTheRefEmptyWhenNoFieldWasNamed() throws Exception {
      ChangeChartTypeService types = mock(ChangeChartTypeService.class);

      multiStyleChart(types)
         .setChartType("tok", principal(), "Chart1", GraphTypes.CHART_LINE, null, null, null, null,
                       "");

      ArgumentCaptor<ChangeChartTypeEvent> event =
         ArgumentCaptor.forClass(ChangeChartTypeEvent.class);
      verify(types).changeChartType(eq("rt1"), event.capture(), any(Principal.class), any(),
                                    anyString());
      assertNull(event.getValue().getRef());
   }

   /**
    * {@code ChangeChartTypeProcessor} searches x and y for the named ref and, finding nothing, sets
    * no type at all — it does not fall back to the assembly, because it is inside the
    * {@code ref != null} branch. So a misspelled column, or one that sits on `group` or a
    * single-field shelf, would report ok and change nothing. Refused by name instead, with the
    * fields that would have worked.
    */
   @Test
   void refusesAFieldThatIsNotOnXOrY() {
      ChangeChartTypeService types = mock(ChangeChartTypeService.class);
      ChartBindingService service = multiStyleChart(types);

      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> service.setChartType("tok", principal(), "Chart1", GraphTypes.CHART_LINE, null, null,
                                    null, "Sum(NOPE)", ""));

      assertTrue(thrown.getMessage().contains("Sum(NOPE)"), thrown.getMessage());
      assertTrue(thrown.getMessage().contains("Sum(PAID)"),
                 "must name the measures that would have worked, got: " + thrown.getMessage());
      verifyNoInteractions(types);
   }

   /**
    * A per-measure type only renders under Multi Style, so accepting one on a single-style chart
    * would store a setting nothing draws from — and the caller would have no way to tell, since the
    * write reports ok either way.
    */
   @Test
   void refusesAPerFieldTypeOnAChartThatIsNotMultiStyle() {
      ChangeChartTypeService types = mock(ChangeChartTypeService.class);
      ChartBindingService service = singleStyleChart(types);

      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> service.setChartType("tok", principal(), "Chart1", GraphTypes.CHART_LINE, null, null,
                                    null, "Sum(PAID)", ""));

      assertTrue(thrown.getMessage().contains("multi"), thrown.getMessage());
      verifyNoInteractions(types);
   }

   private static ChartBindingService multiStyleChart(ChangeChartTypeService types) {
      return chartWithFields(types, true);
   }

   private static ChartBindingService singleStyleChart(ChangeChartTypeService types) {
      return chartWithFields(types, false);
   }

   private static ChartBindingService chartWithFields(ChangeChartTypeService types, boolean multi) {
      // Mocked rather than built: getFullName() on a real ref depends on runtime state this test
      // has no reason to stand up, and the name is the only thing the lookup cares about.
      VSChartAggregateRef paid = mock(VSChartAggregateRef.class);
      when(paid.getFullName()).thenReturn("Sum(PAID)");
      VSChartAggregateRef discount = mock(VSChartAggregateRef.class);
      when(discount.getFullName()).thenReturn("Sum(DISCOUNT)");

      VSChartInfo info = mock(VSChartInfo.class);
      when(info.isMultiStyles()).thenReturn(multi);
      when(info.isSeparatedGraph()).thenReturn(true);
      when(info.getChartType()).thenReturn(GraphTypes.CHART_BAR);
      when(info.getXFieldCount()).thenReturn(1);
      when(info.getXField(0)).thenReturn(paid);
      when(info.getYFieldCount()).thenReturn(1);
      when(info.getYField(0)).thenReturn(discount);

      ChartVSAssembly chart = mock(ChartVSAssembly.class);
      when(chart.getVSChartInfo()).thenReturn(info);

      return harnessWithAssembly(chart, new ChartBindingModel(),
                                 mock(ChangeChartRefService.class), types,
                                 mock(SwapXYBindingService.class),
                                 mock(ChangeSeparateStatusService.class));
   }

   /**
    * An omitted flag means "leave it alone", and the shared event builder had no way to say that:
    * {@code Boolean.TRUE.equals(stackMeasures)} turns a null into false, which is then applied.
    *
    * <p>Confirmed live before this test existed: on a chart with stackMeasures on, one
    * {@code set_chart_type(bar)} that did not mention the flag read back with it **off**, same type
    * and nothing else changed. {@code multi} has the same hole and only escapes it by accident —
    * the coerced value travels through the WebSocket path that silently drops it — so repairing
    * that path would turn every plain retype into a multi-style teardown.
    */
   @Test
   void sendsTheChartsCurrentFlagsWhenTheCallerDidNotMentionThem() throws Exception {
      ChangeChartTypeService types = mock(ChangeChartTypeService.class);
      PlotDescriptor plot = mock(PlotDescriptor.class);
      when(plot.isStackMeasures()).thenReturn(true);
      ChartDescriptor descriptor = mock(ChartDescriptor.class);
      when(descriptor.getPlotDescriptor()).thenReturn(plot);
      VSChartInfo info = mock(VSChartInfo.class);
      when(info.isMultiStyles()).thenReturn(true);
      when(info.isSeparatedGraph()).thenReturn(false);
      when(info.getChartType()).thenReturn(GraphTypes.CHART_BAR);
      ChartVSAssembly chart = mock(ChartVSAssembly.class);
      when(chart.getVSChartInfo()).thenReturn(info);
      when(chart.getChartDescriptor()).thenReturn(descriptor);

      harnessWithAssembly(chart, new ChartBindingModel(), mock(ChangeChartRefService.class),
                          types, mock(SwapXYBindingService.class),
                          mock(ChangeSeparateStatusService.class))
         .setChartType("tok", principal(), "Chart1", GraphTypes.CHART_BAR, null, null, null, null, "");

      ArgumentCaptor<ChangeChartTypeEvent> event =
         ArgumentCaptor.forClass(ChangeChartTypeEvent.class);
      verify(types).changeChartType(eq("rt1"), event.capture(), any(Principal.class), any(),
                                    anyString());
      assertTrue(event.getValue().isStackMeasures(),
                 "an unmentioned stackMeasures must not be reported as off");
      assertTrue(event.getValue().isMulti(), "an unmentioned multi must not be reported as off");
      assertFalse(event.getValue().isSeparate(),
                  "an unmentioned separate must carry the chart's own state, not a forced true");
   }

   /** What the caller does state still wins, so the preserving default cannot swallow a real ask. */
   @Test
   void stillSendsTheFlagsTheCallerDidState() throws Exception {
      ChangeChartTypeService types = mock(ChangeChartTypeService.class);
      PlotDescriptor plot = mock(PlotDescriptor.class);
      when(plot.isStackMeasures()).thenReturn(true);
      ChartDescriptor descriptor = mock(ChartDescriptor.class);
      when(descriptor.getPlotDescriptor()).thenReturn(plot);
      VSChartInfo info = mock(VSChartInfo.class);
      when(info.isMultiStyles()).thenReturn(true);
      when(info.isSeparatedGraph()).thenReturn(true);
      when(info.getChartType()).thenReturn(GraphTypes.CHART_BAR);
      ChartVSAssembly chart = mock(ChartVSAssembly.class);
      when(chart.getVSChartInfo()).thenReturn(info);
      when(chart.getChartDescriptor()).thenReturn(descriptor);

      harnessWithAssembly(chart, new ChartBindingModel(), mock(ChangeChartRefService.class),
                          types, mock(SwapXYBindingService.class),
                          mock(ChangeSeparateStatusService.class))
         .setChartType("tok", principal(), "Chart1", GraphTypes.CHART_BAR, false, false, null, null, "");

      ArgumentCaptor<ChangeChartTypeEvent> event =
         ArgumentCaptor.forClass(ChangeChartTypeEvent.class);
      verify(types).changeChartType(eq("rt1"), event.capture(), any(Principal.class), any(),
                                    anyString());
      assertFalse(event.getValue().isStackMeasures(), "an explicit false must survive");
      assertFalse(event.getValue().isMulti(), "an explicit false must survive");
   }

   /**
    * set_chart_type's documented `multi` flag never reached the chart on this path.
    *
    * <p>{@code ChangeChartTypeService.handleMulti} — the only code that turns multiStyles on —
    * routes through {@code ChangeSeparateStatusController}, a
    * {@code @MessageMapping("/vs/chart/changeSeparateStatus")} handler that reads its runtime id
    * from {@code runtimeViewsheetRef}, documented in its own constructor as the runtime "associated
    * with the WebSocket session". An agent call is plain HTTP with a pairing token, so that id is
    * null and the handler returns silently. Three live attempts reported ok with multiStyles still
    * false, including the one that should have worked: type unchanged, two measures bound.
    *
    * <p>Applied here rather than repaired there, for two reasons: the shared path has no test
    * coverage at all and the defect is a missing WebSocket context rather than wrong logic, so no
    * mock test at that layer would have caught it; and this service already makes exactly this call
    * correctly in {@code setSeparateStatus}, with the runtime id it holds.
    */
   @Test
   void appliesMultiStyleItselfRatherThanLeavingItToTheWebSocketPath() throws Exception {
      ChangeSeparateStatusService separateStatus = mock(ChangeSeparateStatusService.class);
      VSChartInfo info = mock(VSChartInfo.class);
      when(info.isMultiStyles()).thenReturn(false);
      when(info.isSeparatedGraph()).thenReturn(true);
      when(info.getChartType()).thenReturn(GraphTypes.CHART_BAR);
      ChartVSAssembly chart = mock(ChartVSAssembly.class);
      when(chart.getVSChartInfo()).thenReturn(info);

      harnessWithAssembly(chart, new ChartBindingModel(), mock(ChangeChartRefService.class),
                          mock(ChangeChartTypeService.class), mock(SwapXYBindingService.class),
                          separateStatus)
         .setChartType("tok", principal(), "Chart1", GraphTypes.CHART_BAR, true, null, null, null, "");

      ArgumentCaptor<ChangeSeparateStatusEvent> event =
         ArgumentCaptor.forClass(ChangeSeparateStatusEvent.class);
      verify(separateStatus).changeSeparateStatus(eq("rt1"), event.capture(),
                                                 any(Principal.class), any(), anyString());
      assertTrue(event.getValue().isMulti(), "the multi the caller asked for must reach the chart");
      assertTrue(event.getValue().isSeparate(),
                 "separate was not given, so the chart's current separated state is preserved");
   }

   /**
    * Nothing to change, nothing to call. The multi transition is a second write with its own
    * undo step, so firing it when the state already matches would put a no-op into the user's
    * Composer history for every retype.
    */
   @Test
   void doesNotTouchMultiStyleWhenItAlreadyMatches() throws Exception {
      ChangeSeparateStatusService separateStatus = mock(ChangeSeparateStatusService.class);
      VSChartInfo info = mock(VSChartInfo.class);
      when(info.isMultiStyles()).thenReturn(true);
      when(info.getChartType()).thenReturn(GraphTypes.CHART_BAR);
      ChartVSAssembly chart = mock(ChartVSAssembly.class);
      when(chart.getVSChartInfo()).thenReturn(info);

      harnessWithAssembly(chart, new ChartBindingModel(), mock(ChangeChartRefService.class),
                          mock(ChangeChartTypeService.class), mock(SwapXYBindingService.class),
                          separateStatus)
         .setChartType("tok", principal(), "Chart1", GraphTypes.CHART_BAR, true, null, null, null, "");

      verify(separateStatus, never()).changeSeparateStatus(anyString(), any(), any(), any(), any());
   }

   /**
    * An omitted multi means "not asked for", not "off". Coercing it to false is what the shared
    * event builder does, and it is why a plain retype cannot leave the flag alone.
    */
   @Test
   void leavesMultiStyleAloneWhenTheCallerDidNotAskAboutIt() throws Exception {
      ChangeSeparateStatusService separateStatus = mock(ChangeSeparateStatusService.class);
      VSChartInfo info = mock(VSChartInfo.class);
      when(info.isMultiStyles()).thenReturn(true);
      when(info.getChartType()).thenReturn(GraphTypes.CHART_BAR);
      ChartVSAssembly chart = mock(ChartVSAssembly.class);
      when(chart.getVSChartInfo()).thenReturn(info);

      harnessWithAssembly(chart, new ChartBindingModel(), mock(ChangeChartRefService.class),
                          mock(ChangeChartTypeService.class), mock(SwapXYBindingService.class),
                          separateStatus)
         .setChartType("tok", principal(), "Chart1", GraphTypes.CHART_BAR, null, null, null, null, "");

      verify(separateStatus, never()).changeSeparateStatus(anyString(), any(), any(), any(), any());
   }

   /**
    * A chart's type had no reader anywhere in the agent surface — not get_binding, not
    * get_chart_aesthetics, not the property tools, not even get_assembly_properties(raw), which is
    * the documented escape hatch for a property with no short name. So set_chart_type's own advice
    * to "read the binding again afterwards" could not be acted on.
    *
    * <p>Reports the codes, not names: the code↔name vocabulary already exists in three copies here
    * (WizAutoBindingService twice, WizVsService once) plus the plugin's, and the plugin owns the
    * one that faces the agent — set_chart_type already echoes names from it.
    */
   @Test
   void readsTheAssemblyTypeAndTheThreeFlagsThatDecideWhatItMeans() throws Exception {
      ChartBindingModel model = new ChartBindingModel();
      model.setChartType(1);
      // Deliberately different from chartType: reporting only the stored value is how a read
      // becomes a "stored but inert" mirror of what the renderer actually used.
      model.setRTChartType(5);
      model.setMultiStyles(true);
      model.setSeparated(false);
      model.setStackMeasures(true);

      ChartTypeState state = harness(model, mock(ChangeChartRefService.class),
                                     mock(ChangeChartTypeService.class),
                                     mock(SwapXYBindingService.class),
                                     mock(ChangeSeparateStatusService.class))
         .readChartType("tok", principal(), "Chart1");

      assertEquals("Chart1", state.assembly());
      assertEquals(1, state.chartType());
      assertEquals(5, state.runtimeChartType());
      assertTrue(state.multiStyles(), "multiStyles decides whether the type is per-measure");
      assertFalse(state.separated());
      assertTrue(state.stackMeasures());
   }

   /**
    * A read must not open an undo checkpoint. The cheapest wrong implementation reuses
    * {@code sessions.mutate}, which would put a no-op step into the user's Composer history for
    * every read an agent makes.
    */
   @Test
   void readsTheTypeWithoutOpeningACheckpoint() throws Exception {
      ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);
      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getAssembly(anyString())).thenReturn(mock(ChartVSAssembly.class));
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(sessions.resolve(anyString(), any(Principal.class))).thenReturn(rvs);

      VSBindingService binding = mock(VSBindingService.class);
      when(binding.createModel(any())).thenReturn(new ChartBindingModel());

      new ChartBindingService(sessions, binding, mock(ChangeChartRefService.class),
                              mock(ChangeChartTypeService.class), mock(SwapXYBindingService.class),
                              mock(ChangeSeparateStatusService.class))
         .readChartType("tok", principal(), "Chart1");

      verify(sessions, never()).mutate(anyString(), any(Principal.class), any());
   }

   @Test
   void refusesToReadATypeOffSomethingThatIsNotAChart() {
      ChartBindingService service = harnessWithAssembly(
         mock(TextVSAssembly.class), new ChartBindingModel(),
         mock(ChangeChartRefService.class), mock(ChangeChartTypeService.class),
         mock(SwapXYBindingService.class), mock(ChangeSeparateStatusService.class));

      Exception thrown = assertThrows(IllegalArgumentException.class,
                                      () -> service.readChartType("tok", principal(), "Text1"));

      assertTrue(thrown.getMessage().contains("not a chart"),
                 "must say it is not a chart, got: " + thrown.getMessage());
   }

   private static ChartBindingService harness(ChartBindingModel model,
                                              ChangeChartRefService refs,
                                              ChangeChartTypeService types,
                                              SwapXYBindingService swap,
                                              ChangeSeparateStatusService separateStatus)
   {
      return harnessWithAssembly(mock(ChartVSAssembly.class), model, refs, types, swap,
                                 separateStatus);
   }

   /**
    * A session service whose mutate() runs the mutation immediately against runtime "rt1",
    * so these tests exercise the read-modify-write without a live runtime.
    */
   private static ChartBindingService harnessWithAssembly(VSAssembly assembly,
                                                          ChartBindingModel model,
                                                          ChangeChartRefService refs,
                                                          ChangeChartTypeService types,
                                                          SwapXYBindingService swap,
                                                          ChangeSeparateStatusService separateStatus)
   {
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
         // Reads take this path instead — no checkpoint.
         when(sessions.resolve(anyString(), any(Principal.class))).thenReturn(rvs);
      }
      catch(Exception e) {
         throw new IllegalStateException(e);
      }

      VSBindingService binding = mock(VSBindingService.class);
      when(binding.createModel(any())).thenReturn(model);

      return new ChartBindingService(sessions, binding, refs, types, swap, separateStatus);
   }

   private static Principal principal() {
      return () -> "admin";
   }
}
