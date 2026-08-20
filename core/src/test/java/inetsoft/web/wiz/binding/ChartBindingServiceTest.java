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
         .setChartType("tok", principal(), "Chart1", 3, null, null, null, "");

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
