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
package inetsoft.web.wiz.viewsheet;

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.VSTableLens;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.SourceInfo;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.util.script.ScriptException;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.web.composer.vs.objects.controller.*;
import inetsoft.web.composer.vs.objects.event.ConvertToFreehandTableEvent;
import inetsoft.web.composer.vs.objects.event.ConvertToRangeSliderEvent;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Every refusal here covers a case the endpoint answers with silence or an unhandled exception, so
 * these are not defensive extras — they are the reason the service exists rather than the tool
 * calling the endpoints directly.
 */
@Tag("core")
class AssemblyConvertServiceTest {
   // ── the silent-no-op guards ───────────────────────────────────────────────

   /**
    * An unknown name is the case all three endpoints answer with success and no change.
    */
   @Test
   void refusesAnUnknownAssemblyInsteadOfSucceedingSilently() {
      Harness h = harness(null, "Nope");

      Exception e = assertThrows(IllegalArgumentException.class,
         () -> h.service.convert("tok", principal(), "Nope", "range_slider", ""));

      assertTrue(e.getMessage().contains("Nope"), "the message must name the assembly");
      verifyNoInteractions(h.selectionList, h.rangeSlider, h.table);
   }

   /**
    * Pointing a freehand convert at a chart is the canonical silent no-op: the service checks
    * {@code instanceof TableDataVSAssembly} and returns null, reporting success.
    */
   @Test
   void refusesConvertingAChartToFreehandNamingItsActualType() {
      Harness h = harness(plain(ChartVSAssembly.class), "Chart1");

      Exception e = assertThrows(IllegalArgumentException.class,
         () -> h.service.convert("tok", principal(), "Chart1", "freehand_table", ""));

      assertTrue(e.getMessage().contains("Chart1"));
      assertTrue(e.getMessage().contains("table or a crosstab"),
                 "the message must say what the conversion needs, got: " + e.getMessage());
      verifyNoInteractions(h.table);
   }

   /**
    * <b>The NPE guard.</b> A standalone selection list passes the endpoint's own
    * {@code &&} guard and then dereferences a null container. This is the case the Composer hides by
    * making the menu item invisible, so only a tool can reach it.
    */
   @Test
   void refusesAStandaloneSelectionListBecauseTheEndpointWouldCrash() {
      SelectionListVSAssembly list = selectionList(null, false);
      Harness h = harness(list, "Filter1");

      Exception e = assertThrows(IllegalArgumentException.class,
         () -> h.service.convert("tok", principal(), "Filter1", "range_slider", ""));

      assertTrue(e.getMessage().contains("selection container"),
                 "must explain the real precondition, got: " + e.getMessage());
      verifyNoInteractions(h.selectionList);
   }

   /** Same guard, the other direction — {@code convertCSComponent} has the identical defect. */
   @Test
   void refusesAStandaloneRangeSliderToo() {
      TimeSliderVSAssembly slider = rangeSlider(null);
      Harness h = harness(slider, "Slider1");

      assertThrows(IllegalArgumentException.class,
         () -> h.service.convert("tok", principal(), "Slider1", "selection_list", ""));
      verifyNoInteractions(h.rangeSlider);
   }

   /**
    * Embedded assemblies are refused by the endpoint with a bare {@code return null}.
    *
    * <p>{@code isEmbedded()} is derived from the owning viewsheet rather than settable, so the info
    * is mocked here — the point under test is that the service asks and refuses, not how the flag
    * comes to be true.
    */
   @Test
   void refusesAnEmbeddedAssemblyWithAReason() {
      SelectionListVSAssembly list = mock(SelectionListVSAssembly.class);
      SelectionListVSAssemblyInfo info = mock(SelectionListVSAssemblyInfo.class);
      when(info.isEmbedded()).thenReturn(true);
      doReturn(info).when(list).getInfo();

      Harness h = harness(list, "Filter1");

      Exception e = assertThrows(IllegalArgumentException.class,
         () -> h.service.convert("tok", principal(), "Filter1", "range_slider", ""));

      assertTrue(e.getMessage().contains("embedded"), e.getMessage());
   }

   // ── the ported UI preconditions ───────────────────────────────────────────

   /**
    * {@code visible: composer && !adhocFilter && inSelectionContainer} — the ad hoc half exists only
    * in Angular.
    */
   @Test
   void refusesAnAdhocFilter() {
      SelectionListVSAssembly list = selectionList(mock(CurrentSelectionVSAssembly.class), true);
      Harness h = harness(list, "Adhoc1");

      Exception e = assertThrows(IllegalArgumentException.class,
         () -> h.service.convert("tok", principal(), "Adhoc1", "range_slider", ""));

      assertTrue(e.getMessage().contains("ad hoc"), e.getMessage());
   }

   // ── the happy paths, and that the right endpoint is chosen ────────────────

   /**
    * Direction is encoded in the endpoint, never the payload — both directions send
    * {@code ConvertToRangeSliderEvent}. This asserts the mapping picks the right service, which is
    * the whole reason a caller must not infer direction from the event type.
    */
   @Test
   void routesEachDirectionToItsOwnEndpointDespiteTheSharedEventType() throws Exception {
      SelectionListVSAssembly list = selectionList(mock(CurrentSelectionVSAssembly.class), false);
      Harness toSlider = harness(list, "Filter1");

      toSlider.service.convert("tok", principal(), "Filter1", "range_slider", "");

      ArgumentCaptor<ConvertToRangeSliderEvent> sent =
         ArgumentCaptor.forClass(ConvertToRangeSliderEvent.class);
      verify(toSlider.selectionList).convertToRangeSlider(
         anyString(), sent.capture(), any(Principal.class), any(), anyString());
      verifyNoInteractions(toSlider.rangeSlider);
      assertEquals("Filter1", sent.getValue().getName());

      TimeSliderVSAssembly slider = rangeSlider(mock(CurrentSelectionVSAssembly.class));
      Harness toList = harness(slider, "Slider1");

      toList.service.convert("tok", principal(), "Slider1", "selection_list", "");

      verify(toList.rangeSlider).convertCSComponent(
         anyString(), any(ConvertToRangeSliderEvent.class), any(Principal.class), any(), anyString());
      verifyNoInteractions(toList.selectionList);
   }

   /**
    * <b>{@code confirmed} must stay false.</b> True would skip {@code fixAggregateInfo}, the repair
    * that populates an empty {@code AggregateInfo} before the freehand layout is generated. No caller
    * in the product sets it, and the default is what makes the conversion correct.
    */
   @Test
   void sendsConfirmedFalseSoTheAggregateInfoRepairStillRuns() throws Exception {
      Harness h = harness(plain(TableVSAssembly.class), "Table1");

      h.service.convert("tok", principal(), "Table1", "freehand_table", "");

      ArgumentCaptor<ConvertToFreehandTableEvent> sent =
         ArgumentCaptor.forClass(ConvertToFreehandTableEvent.class);
      verify(h.table).convertToFreehandTable(
         anyString(), sent.capture(), any(Principal.class), anyString(), any());

      assertFalse(sent.getValue().isConfirmed(),
                  "confirmed=true skips fixAggregateInfo, which the freehand cells depend on");
   }

   // ── disclosure ────────────────────────────────────────────────────────────

   /**
    * The conversion clears a date comparison, drill expansion and every non-percent calculator. The
    * server computes the calculator names and passes them to format syncing without telling anyone,
    * so the only place a caller can learn about the loss is this result.
    */
   @Test
   void disclosesEverythingAConvertedCrosstabLoses() throws Exception {
      CrosstabVSAssembly crosstab = mock(CrosstabVSAssembly.class);
      CrosstabVSAssemblyInfo info = mock(CrosstabVSAssemblyInfo.class);
      when(info.isEmbedded()).thenReturn(false);
      when(info.getDateComparisonInfo()).thenReturn(mock(DateComparisonInfo.class));
      doReturn(info).when(crosstab).getInfo();
      when(crosstab.getCrosstabInfo()).thenReturn(info);

      // getExpandedPaths() is Map<String,Set<String>>, not a flat collection.
      CrosstabTree tree = mock(CrosstabTree.class);
      when(tree.getExpandedPaths()).thenReturn(Map.of("Region", Set.of("East")));
      when(crosstab.getCrosstabTree()).thenReturn(tree);

      // A non-percent calculator is the case convertToFreehandTable strips and never reports.
      VSAggregateRef withCalc = mock(VSAggregateRef.class);
      when(withCalc.getFullName()).thenReturn("Sum(Sales)");
      when(withCalc.getCalculator())
         .thenReturn(mock(inetsoft.report.composition.graph.calc.RunningTotalCalc.class));
      VSCrosstabInfo crosstabInfo = mock(VSCrosstabInfo.class);
      when(crosstabInfo.getAggregates()).thenReturn(new inetsoft.uql.erm.DataRef[]{ withCalc });
      when(crosstab.getVSCrosstabInfo()).thenReturn(crosstabInfo);

      Harness h = harness(crosstab, "Crosstab1");

      Map<String, Object> result =
         h.service.convert("tok", principal(), "Crosstab1", "freehand_table", "");

      @SuppressWarnings("unchecked")
      List<String> cleared = (List<String>) result.get("cleared");

      assertTrue(cleared.contains("date comparison"), "cleared: " + cleared);
      assertTrue(cleared.contains("drill expansion"), "cleared: " + cleared);
      assertTrue(cleared.stream().anyMatch(c -> c.startsWith("calculator on")),
                 "the calculator loss is invisible everywhere else, cleared: " + cleared);
   }

   /** A plain table loses none of that, and must not be reported as if it had. */
   @Test
   void reportsNothingClearedForAPlainTable() throws Exception {
      Harness h = harness(plain(TableVSAssembly.class), "Table1");

      Map<String, Object> result =
         h.service.convert("tok", principal(), "Table1", "freehand_table", "");

      assertEquals(List.of(), result.get("cleared"));
      assertEquals("a table", result.get("from"));
      assertEquals("freehand_table", result.get("to"));
   }

   /** The assembly is replaced rather than edited, so anything holding its identity is stale. */
   @Test
   void alwaysReturnsTheStalenessNote() throws Exception {
      Harness h = harness(plain(TableVSAssembly.class), "Table1");

      String note = String.valueOf(
         h.service.convert("tok", principal(), "Table1", "freehand_table", "").get("note"));

      assertTrue(note.contains("re-read"), note);
      assertTrue(note.contains("binding-editor"), note);
   }

   // ── target normalisation ──────────────────────────────────────────────────

   /**
    * The menu says "Convert to Freehand Table"; the assembly created is a
    * {@code CalcTableVSAssembly}. Both names are natural, so both are accepted.
    */
   @Test
   void acceptsTheNaturalAliasesForFreehand() throws Exception {
      for(String alias : List.of("freehand_table", "freehand", "calc_table", "calcTable",
                                 "Freehand-Table")) {
         Harness h = harness(plain(TableVSAssembly.class), "Table1");
         Map<String, Object> result =
            h.service.convert("tok", principal(), "Table1", alias, "");

         assertEquals("freehand_table", result.get("to"), "alias '" + alias + "' should normalise");
      }
   }

   @Test
   void failsLoudlyOnAnUnknownTargetRatherThanGuessing() {
      Harness h = harness(plain(TableVSAssembly.class), "Table1");

      Exception e = assertThrows(IllegalArgumentException.class,
         () -> h.service.convert("tok", principal(), "Table1", "pivot_table", ""));

      assertTrue(e.getMessage().contains("freehand_table"),
                 "an unknown target must list the legal ones, got: " + e.getMessage());
   }

   @Test
   void requiresAnAssemblyName() {
      Harness h = harness(plain(TableVSAssembly.class), "Table1");

      assertThrows(IllegalArgumentException.class,
         () -> h.service.convert("tok", principal(), "  ", "freehand_table", ""));
   }

   // ── script-failure relay (bug #78406 round 5) ─────────────────────────────

   /** A table that re-ran during the conversion and failed its script is reported, not lost. */
   @Test
   void relaysTheWarningsTheMutationCaptured() throws Exception {
      SelectionListVSAssembly list = selectionList(mock(CurrentSelectionVSAssembly.class), false);
      Harness h = harness(list, "Filter1", null, List.of("Script execution error in Bad"));

      Map<String, Object> result =
         h.service.convert("tok", principal(), "Filter1", "range_slider", "");

      assertEquals(List.of("Script execution error in Bad"), result.get("warnings"));
      assertEquals("range_slider", result.get("to"));
   }

   /** A clean conversion keeps the response exactly as it was: no {@code warnings} key at all. */
   @Test
   void omitsTheWarningsKeyWhenNothingFailed() throws Exception {
      SelectionListVSAssembly list = selectionList(mock(CurrentSelectionVSAssembly.class), false);

      for(List<String> captured : Arrays.<List<String>>asList(List.of(), null)) {
         Harness h = harness(list, "Filter1", null, captured);

         assertFalse(h.service.convert("tok", principal(), "Filter1", "range_slider", "")
                        .containsKey("warnings"), "captured: " + captured);
      }
   }

   // ── dropped selection disclosure (bug #79054) ─────────────────────────────

   @SuppressWarnings("unchecked")
   private static List<String> clearedOf(VSAssembly assembly, String name, String to)
      throws Exception
   {
      Harness h = harness(assembly, name);
      return (List<String>) h.service.convert("tok", principal(), name, to, "").get("cleared");
   }

   @Test
   void namesASelectionListSelectionDroppedByTheRangeSlider() throws Exception {
      SelectionListVSAssembly list = selectionList(mock(CurrentSelectionVSAssembly.class), false);
      when(list.getSelectedObjects()).thenReturn(List.of("AZ", "CA"));

      assertEquals(List.of("selection (2 values)"), clearedOf(list, "Filter1", "range_slider"));
   }

   @Test
   void usesTheSingularForOneSelectedValue() throws Exception {
      SelectionListVSAssembly list = selectionList(mock(CurrentSelectionVSAssembly.class), false);
      when(list.getSelectedObjects()).thenReturn(List.of("AZ"));

      assertEquals(List.of("selection (1 value)"), clearedOf(list, "Filter1", "range_slider"));
   }

   @Test
   void namesNothingWhenTheListHasNoSelection() throws Exception {
      SelectionListVSAssembly list = selectionList(mock(CurrentSelectionVSAssembly.class), false);
      when(list.getSelectedObjects()).thenReturn(List.of());

      assertEquals(List.of(), clearedOf(list, "Filter1", "range_slider"));
   }

   @Test
   void aSelectionThatCannotBeReadDoesNotBlockTheConversion() throws Exception {
      SelectionListVSAssembly list = selectionList(mock(CurrentSelectionVSAssembly.class), false);
      when(list.getSelectedObjects()).thenThrow(new IllegalStateException("boom"));
      Harness h = harness(list, "Filter1");

      Map<String, Object> result =
         h.service.convert("tok", principal(), "Filter1", "range_slider", "");

      assertEquals(List.of(), result.get("cleared"));
      assertEquals("range_slider", result.get("to"));
      verify(h.selectionList).convertToRangeSlider(
         anyString(), any(ConvertToRangeSliderEvent.class), any(Principal.class), any(),
         anyString());
   }

   // ── hidden columns (bug #79015) ───────────────────────────────────────────

   private static TableVSAssembly tableWithHidden(String... hiddenNames) {
      TableVSAssembly table = mock(TableVSAssembly.class);
      TableVSAssemblyInfo info = mock(TableVSAssemblyInfo.class);
      ColumnSelection hidden = new ColumnSelection();

      for(String name : hiddenNames) {
         hidden.addAttribute(new ColumnRef(new AttributeRef(null, name)));
      }

      when(info.isEmbedded()).thenReturn(false);
      when(info.getHiddenColumns()).thenReturn(hidden);
      doReturn(info).when(table).getInfo();
      when(table.getTableDataVSAssemblyInfo()).thenReturn(info);
      return table;
   }

   @Test
   void namesATableHiddenColumnTheFreehandTableDrops() throws Exception {
      assertEquals(List.of("hidden column Total"),
                   clearedOf(tableWithHidden("Total"), "Table1", "freehand_table"));
   }

   @Test
   void namesEachHiddenColumnInOrder() throws Exception {
      assertEquals(List.of("hidden column Total", "hidden column Region"),
                   clearedOf(tableWithHidden("Total", "Region"), "Table1", "freehand_table"));
   }

   @Test
   void namesNoHiddenColumnWhenTheTableHasNone() throws Exception {
      assertEquals(List.of(), clearedOf(tableWithHidden(), "Table1", "freehand_table"));
   }

   /** A crosstab's hidden columns survive as width-0 columns, so they are not a loss. */
   @Test
   void doesNotNameACrosstabHiddenColumn() throws Exception {
      CrosstabVSAssembly crosstab = boundCrosstab("Xt1", true);

      List<String> cleared = clearedOf(crosstab, "Xt1", "freehand_table");

      assertTrue(cleared.stream().noneMatch(c -> c.startsWith("hidden column")),
                 "cleared: " + cleared);
   }

   // ── the unrendered-table refusal (bug #78406 round 3) ─────────────────────

   /**
    * A bound table whose script threw while it loaded has no lens, and converting it builds an empty
    * 0x0 layout while the endpoint answers success. The first read after the failure rethrows the
    * ScriptException; every later one returns null.
    */
   @Test
   void refusesATableWhoseScriptFailedOnTheFirstRead() throws Exception {
      ViewsheetSandbox box = mock(ViewsheetSandbox.class);
      when(box.getVSTableLens("Xt1", false))
         .thenThrow(new RuntimeException(new ScriptException("undefinedFn is not defined")));
      Harness h = harness(boundCrosstab("Xt1", false), "Xt1", box);

      IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
         () -> h.service.convert("tok", principal(), "Xt1", "freehand_table", ""));

      assertTrue(refused.getMessage().contains("did not render"), refused.getMessage());
      assertTrue(refused.getMessage().contains("undefinedFn"), refused.getMessage());
      verifyNoInteractions(h.table);
   }

   @Test
   void refusesATableWhoseFailedLoadIsCachedAsNull() throws Exception {
      ViewsheetSandbox box = mock(ViewsheetSandbox.class);
      when(box.getVSTableLens("Xt1", false)).thenReturn(null);
      Harness h = harness(boundCrosstab("Xt1", false), "Xt1", box);

      IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
         () -> h.service.convert("tok", principal(), "Xt1", "freehand_table", ""));

      assertTrue(refused.getMessage().contains("did not render"), refused.getMessage());
      verifyNoInteractions(h.table);
   }

   /** A table that rendered but holds no rows has a lens, so it still converts. */
   @Test
   void stillConvertsATableThatRenderedZeroRows() throws Exception {
      ViewsheetSandbox box = mock(ViewsheetSandbox.class);
      when(box.getVSTableLens("Xt1", false)).thenReturn(mock(VSTableLens.class));
      Harness h = harness(boundCrosstab("Xt1", false), "Xt1", box);

      h.service.convert("tok", principal(), "Xt1", "freehand_table", "");

      verify(h.table).convertToFreehandTable(
         anyString(), any(ConvertToFreehandTableEvent.class), any(Principal.class), anyString(),
         any());
   }

   /** A new crosstab with no binding has nothing to render; it converts as it did before. */
   @Test
   void stillConvertsAnUnboundCrosstabWithoutConsultingTheSandbox() throws Exception {
      Harness h = harness(boundCrosstab("Xt1", true), "Xt1");

      h.service.convert("tok", principal(), "Xt1", "freehand_table", "");

      verify(h.table).convertToFreehandTable(
         anyString(), any(ConvertToFreehandTableEvent.class), any(Principal.class), anyString(),
         any());
   }

   /** Only the freehand conversion builds a layout from the result; the selection ones do not. */
   @Test
   void doesNotRequireARenderedTableForTheSelectionConversions() throws Exception {
      Harness h = harness(selectionList(mock(CurrentSelectionVSAssembly.class), false), "Filter1");

      h.service.convert("tok", principal(), "Filter1", "range_slider", "");

      verify(h.selectionList).convertToRangeSlider(
         anyString(), any(ConvertToRangeSliderEvent.class), any(Principal.class), any(),
         anyString());
   }

   // ── harness ───────────────────────────────────────────────────────────────

   private record Harness(AssemblyConvertService service, ViewsheetSessionService sessions,
                          ComposerVSSelectionListService selectionList,
                          ComposerRangeSliderService rangeSlider,
                          ComposerVSTableService table) {}

   /**
    * <b>Every {@code *VSAssemblyInfo} here is mocked, never constructed.</b> Their constructors reach
    * into {@code SreeEnv} and the Spring context — a real {@code ViewsheetInfo} inside a
    * {@code when(...)} argument left Mockito with an unfinished stubbing that failed all 14 tests at
    * once, and real selection infos failed with "Spring application context is not available". The
    * service only ever asks these objects questions, so mocks are the honest fixture.
    */
   private static SelectionListVSAssembly selectionList(VSAssembly container, boolean adhoc) {
      SelectionListVSAssembly list = mock(SelectionListVSAssembly.class);
      SelectionListVSAssemblyInfo info = mock(SelectionListVSAssemblyInfo.class);
      when(info.isEmbedded()).thenReturn(false);
      when(info.isAdhocFilter()).thenReturn(adhoc);
      doReturn(info).when(list).getInfo();
      when(list.getContainer()).thenReturn(container);
      return list;
   }

   private static TimeSliderVSAssembly rangeSlider(VSAssembly container) {
      TimeSliderVSAssembly slider = mock(TimeSliderVSAssembly.class);
      TimeSliderVSAssemblyInfo info = mock(TimeSliderVSAssemblyInfo.class);
      when(info.isEmbedded()).thenReturn(false);
      doReturn(info).when(slider).getInfo();
      when(slider.getContainer()).thenReturn(container);
      return slider;
   }

   /** A table or chart needs only the embedded check answered. */
   private static <T extends VSAssembly> T plain(Class<T> type) {
      T assembly = mock(type);
      VSAssemblyInfo info = mock(VSAssemblyInfo.class);
      when(info.isEmbedded()).thenReturn(false);
      doReturn(info).when(assembly).getInfo();
      return assembly;
   }

   /** A crosstab with a source ({@code unbound} false) or without one. */
   private static CrosstabVSAssembly boundCrosstab(String name, boolean unbound) {
      CrosstabVSAssembly crosstab = mock(CrosstabVSAssembly.class);
      CrosstabVSAssemblyInfo info = mock(CrosstabVSAssemblyInfo.class);
      SourceInfo source = mock(SourceInfo.class);
      when(source.isEmpty()).thenReturn(unbound);
      when(info.isEmbedded()).thenReturn(false);
      when(info.getSourceInfo()).thenReturn(source);
      doReturn(info).when(crosstab).getInfo();
      when(crosstab.getCrosstabInfo()).thenReturn(info);
      when(crosstab.getAbsoluteName()).thenReturn(name);
      return crosstab;
   }

   private static Harness harness(VSAssembly assembly, String name) {
      return harness(assembly, name, null);
   }

   private static Harness harness(VSAssembly assembly, String name, ViewsheetSandbox box) {
      return harness(assembly, name, box, null);
   }

   /** {@code mutateResult} is what the mocked {@code mutate} returns, i.e. the captured warnings. */
   private static Harness harness(VSAssembly assembly, String name, ViewsheetSandbox box,
                                  List<String> mutateResult)
   {
      ViewsheetInfo vinfo = mock(ViewsheetInfo.class);
      when(vinfo.isMetadata()).thenReturn(false);

      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getAssembly(anyString())).thenReturn(assembly);
      when(vs.getViewsheetInfo()).thenReturn(vinfo);

      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.ofNullable(box));

      ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);

      try {
         doAnswer(invocation -> {
            ViewsheetSessionService.Mutation mutation = invocation.getArgument(2);
            mutation.run(rvs, "rt1", null);
            return mutateResult;
         }).when(sessions).mutate(anyString(), any(Principal.class), any());
      }
      catch(Exception e) {
         throw new IllegalStateException(e);
      }

      ComposerVSSelectionListService selectionList = mock(ComposerVSSelectionListService.class);
      ComposerRangeSliderService rangeSlider = mock(ComposerRangeSliderService.class);
      ComposerVSTableService table = mock(ComposerVSTableService.class);

      return new Harness(
         new AssemblyConvertService(sessions, selectionList, rangeSlider, table),
         sessions, selectionList, rangeSlider, table);
   }

   private static Principal principal() {
      return mock(Principal.class);
   }
}
