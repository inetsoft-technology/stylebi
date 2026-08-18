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
import inetsoft.uql.asset.SourceInfo;
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

   private static Harness harness(VSAssembly assembly, String name) {
      ViewsheetInfo vinfo = mock(ViewsheetInfo.class);
      when(vinfo.isMetadata()).thenReturn(false);

      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getAssembly(anyString())).thenReturn(assembly);
      when(vs.getViewsheetInfo()).thenReturn(vinfo);

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
