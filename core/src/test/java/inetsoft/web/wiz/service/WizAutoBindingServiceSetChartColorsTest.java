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
package inetsoft.web.wiz.service;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.graph.aesthetic.ColorFrame;
import inetsoft.graph.aesthetic.StaticColorFrame;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.sree.security.SecurityException;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.graph.AestheticRef;
import inetsoft.uql.viewsheet.graph.ChartRef;
import inetsoft.uql.viewsheet.graph.VSChartAggregateRef;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.web.wiz.model.ChartAestheticModel;
import inetsoft.web.wiz.model.ChartAestheticModelRequest;
import inetsoft.web.wiz.model.ChartColorsRequest;
import inetsoft.web.wiz.model.CreateViewsheetResult;
import org.springframework.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.server.ResponseStatusException;

import java.awt.Color;
import java.security.Principal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * setChartColors mutates the runtime chart info in place. The sandbox's cached VGraphPair
 * holds a reference to that same VSChartInfo, so its staleness check (equalsContent against
 * itself) can never detect the mutation — the service MUST explicitly clear the cached graph
 * or every subsequent render (including brand-new embed connections) serves the stale colors.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class WizAutoBindingServiceSetChartColorsTest {
   private WizAutoBindingService service;
   private ViewsheetSandbox box;
   private VSChartAggregateRef yAgg;
   private VSChartAggregateRef rtYAgg;
   private SecurityEngine securityEngine;
   private ViewsheetService viewsheetService;
   private WizVsService wizVsService;
   private RuntimeViewsheet rvs;
   private Viewsheet vs;
   private ChartVSAssembly chart;

   @BeforeEach
   void setUp() throws Exception {
      viewsheetService = mock(ViewsheetService.class);
      wizVsService = mock(WizVsService.class);
      // collaborators not used by setChartColors; their classes can't be initialized in
      // a plain unit-test environment (no Spring context), so pass null instead of mocks.
      securityEngine = mock(SecurityEngine.class);
      when(securityEngine.checkPermission(any(), any(), anyString(), any())).thenReturn(true);
      service = new WizAutoBindingService(
         viewsheetService, null, null, null, null, wizVsService, securityEngine);

      // Real Viewsheet/assembly/info objects need SreeEnv (Spring context), so mock the
      // whole chain and hand the service a mock measure ref to capture the applied frame.
      yAgg = mock(VSChartAggregateRef.class);
      rtYAgg = mock(VSChartAggregateRef.class);
      VSChartInfo vsChartInfo = mock(VSChartInfo.class);
      when(vsChartInfo.getColorField()).thenReturn(null);
      when(vsChartInfo.getYFields()).thenReturn(new ChartRef[] { yAgg });
      when(vsChartInfo.getXFields()).thenReturn(new ChartRef[0]);
      when(vsChartInfo.getRTYFields()).thenReturn(new ChartRef[] { rtYAgg });
      when(vsChartInfo.getRTXFields()).thenReturn(new ChartRef[0]);

      ChartVSAssemblyInfo info = mock(ChartVSAssemblyInfo.class);
      when(info.getVSChartInfo()).thenReturn(vsChartInfo);

      chart = mock(ChartVSAssembly.class);
      when(chart.getChartInfo()).thenReturn(info);

      vs = mock(Viewsheet.class);
      when(vs.getAssembly("vs_1")).thenReturn(chart);

      box = mock(ViewsheetSandbox.class);
      rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(box));
      when(rvs.getID()).thenReturn("rt-1");
      when(viewsheetService.getViewsheet(anyString(), any())).thenReturn(rvs);
      when(wizVsService.fetchAssemblyData(anyString(), anyString(), any(Principal.class)))
         .thenReturn(new CreateViewsheetResult());
   }

   private static ChartColorsRequest staticRed() {
      ChartColorsRequest request = new ChartColorsRequest();
      request.setWizRuntimeId("rt-1");
      request.setAssemblyName("vs_1");
      request.setStaticColor("#d62728");
      return request;
   }

   @Test
   void staticColorIsAppliedToTheMeasureRef() throws Exception {
      service.setChartColors(staticRed(), null);

      ArgumentCaptor<ColorFrame> captor = ArgumentCaptor.forClass(ColorFrame.class);
      verify(yAgg).setColorFrame(captor.capture());
      StaticColorFrame frame = assertInstanceOf(StaticColorFrame.class, captor.getValue());
      assertEquals(new Color(0xd6, 0x27, 0x28), frame.getColor(),
         "staticColor must land on the bound measure's color frame");
   }

   @Test
   void staticColorIsAppliedToTheRuntimeMeasureRefTheRendererActuallyReads() throws Exception {
      // The renderer (VSFrameVisitor.getAggregates) reads getRTYFields()/getRTXFields() — the
      // runtime clones — not the design refs. Painting only the design refs leaves the next
      // render (even after a cache clear) on the old color.
      service.setChartColors(staticRed(), null);

      ArgumentCaptor<ColorFrame> captor = ArgumentCaptor.forClass(ColorFrame.class);
      verify(rtYAgg).setColorFrame(captor.capture());
      StaticColorFrame frame = assertInstanceOf(StaticColorFrame.class, captor.getValue());
      assertEquals(new Color(0xd6, 0x27, 0x28), frame.getColor(),
         "staticColor must also land on the runtime measure ref used for rendering");
   }

   @Test
   void staticColorClearsTheCachedGraphSoTheChangeActuallyRenders() throws Exception {
      service.setChartColors(staticRed(), null);

      verify(box).clearGraph("vs_1");
   }

   @Test
   void deniedPermissionThrowsAndSkipsRuntimeLookupAndMutation() throws Exception {
      // The action gate is the first statement in setChartColors, before the runtime is even
      // resolved. A denied caller must throw and touch nothing.
      when(securityEngine.checkPermission(any(), any(), anyString(), any())).thenReturn(false);

      assertThrows(SecurityException.class, () -> service.setChartColors(staticRed(), null));

      verify(viewsheetService, never()).getViewsheet(anyString(), any());
      verify(yAgg, never()).setColorFrame(any());
      verify(rtYAgg, never()).setColorFrame(any());
      verify(box, never()).clearGraph(anyString());
   }

   // ── copy=true (copy-then-apply) ──────────────────────────────────────────────────────────

   @Test
   void copyTrueDuplicatesBeforeApplyingAndTargetsTheCopy() throws Exception {
      // A separate mock chart/measure-ref pair standing in for the duplicated assembly —
      // duplicatePrimaryAssembly's real behavior (uniqueAssemblyName + rebind) is covered by
      // WizVsServiceDuplicatePrimaryAssemblyTest; here wizVsService is mocked, so we just need to
      // prove setChartColors WIRES the copy in and applies to it instead of the original.
      VSChartAggregateRef copyYAgg = mock(VSChartAggregateRef.class);
      VSChartInfo copyChartInfo = mock(VSChartInfo.class);
      when(copyChartInfo.getColorField()).thenReturn(null);
      when(copyChartInfo.getYFields()).thenReturn(new ChartRef[] { copyYAgg });
      when(copyChartInfo.getXFields()).thenReturn(new ChartRef[0]);
      when(copyChartInfo.getRTYFields()).thenReturn(new ChartRef[0]);
      when(copyChartInfo.getRTXFields()).thenReturn(new ChartRef[0]);
      ChartVSAssemblyInfo copyInfo = mock(ChartVSAssemblyInfo.class);
      when(copyInfo.getVSChartInfo()).thenReturn(copyChartInfo);
      ChartVSAssembly copyChart = mock(ChartVSAssembly.class);
      when(copyChart.getChartInfo()).thenReturn(copyInfo);
      when(copyChart.getName()).thenReturn("vs_1_copy1");

      when(wizVsService.duplicatePrimaryAssembly(rvs, chart)).thenReturn(copyChart);
      when(wizVsService.fetchAssemblyData("rt-1", "vs_1_copy1", null))
         .thenReturn(new CreateViewsheetResult());

      ChartColorsRequest request = staticRed();
      request.setCopy(true);

      CreateViewsheetResult result = service.setChartColors(request, null);

      // Applied to the COPY's measure ref, never the original's.
      verify(copyYAgg).setColorFrame(any());
      verify(yAgg, never()).setColorFrame(any());
      // The cached graph cleared is the copy's, not the original's.
      verify(box).clearGraph("vs_1_copy1");
      verify(box, never()).clearGraph("vs_1");
      assertEquals("vs_1_copy1", result.getAssemblyName());
   }

   @Test
   void copyFalseNeverCallsDuplicatePrimaryAssembly() throws Exception {
      service.setChartColors(staticRed(), null);

      verify(wizVsService, never()).duplicatePrimaryAssembly(any(), any());
   }

   @Test
   void copyTrueButDuplicationFailsFallsBackToInPlaceWithANote() throws Exception {
      when(wizVsService.duplicatePrimaryAssembly(rvs, chart)).thenReturn(null);

      ChartColorsRequest request = staticRed();
      request.setCopy(true);

      CreateViewsheetResult result = service.setChartColors(request, null);

      // Falls back to the ORIGINAL assembly rather than failing the whole request.
      verify(yAgg).setColorFrame(any());
      assertEquals("vs_1", result.getAssemblyName());
      assertEquals("Copy requested but could not be created; colors applied in place.", result.getNote());
   }

   @Test
   void copyFailureNoteIsTheOnlyThingNoteEverCarries() throws Exception {
      // Supersedes copyFailureNoteSurvivesAlongsideAnUnrelatedBindingNote. That test pinned the two
      // notes being concatenated, because the copy-fallback warning used to share the `note` local with
      // the color-binding logic and got silently discarded when both fired. It cannot be written any
      // more: a color that does not fit the binding is now rejected with a 400 before anything is
      // applied, so `note` has exactly one possible source. The clobber it guarded against is gone by
      // construction rather than by assertion, and what matters now is that the copy warning still
      // reaches the caller on a request that DOES fit.
      when(wizVsService.duplicatePrimaryAssembly(rvs, chart)).thenReturn(null);

      ChartColorsRequest request = staticRed();
      request.setCopy(true);

      CreateViewsheetResult result = service.setChartColors(request, null);

      assertEquals("Copy requested but could not be created; colors applied in place.", result.getNote());
   }

   @Test
   void aColorThatCannotApplyIsRejectedInsteadOfReportedInTheNote() throws Exception {
      // The other half of the change above: a palette on a chart with no color dimension used to apply
      // nothing and describe that in `note`, which a caller could not tell apart from the copy warning.
      ChartColorsRequest request = new ChartColorsRequest();
      request.setWizRuntimeId("rt-1");
      request.setAssemblyName("vs_1");
      request.setPaletteName("Blues");

      ResponseStatusException thrown =
         assertThrows(ResponseStatusException.class, () -> service.setChartColors(request, null));

      assertEquals(HttpStatus.BAD_REQUEST, thrown.getStatusCode());
      assertTrue(thrown.getReason().contains("no color dimension"),
         "the reason must name the actual binding so a caller can correct its request: " + thrown.getReason());
      // Nothing was touched: not the refs, not the graph cache, not the persisted asset.
      verify(yAgg, never()).setColorFrame(any());
      verify(box, never()).clearGraph(anyString());
      verify(wizVsService, never()).persistViewsheet(any(), any(), any());
   }

   @Test
   void anUnknownPaletteIsRejectedBeforeTheCopyIsEverMade() throws Exception {
      // Replaces copySucceedsThenApplyThrowsRollsBackTheDuplicateAndRestoresTheOriginalAsPrimary, which
      // used an unknown palette to make the apply throw AFTER duplicatePrimaryAssembly had mutated the
      // live runtime, then asserted the rollback. Validation now runs against the ORIGINAL chart before
      // the copy, so this request never reaches the duplication at all — a stronger guarantee than
      // rolling it back, and the reason a bad request no longer needs a rollback path.
      //
      // The rollback itself is still covered, by copySucceedsButFetchAssemblyDataThrowsRollsBackTheDuplicate:
      // fetchAssemblyData is the step that can genuinely fail after the copy, since nothing about the
      // payload can any more.
      AestheticRef colorField = mock(AestheticRef.class);
      DataRef dimensionRef = mock(DataRef.class);
      when(colorField.getDataRef()).thenReturn(dimensionRef);
      when(chart.getChartInfo().getVSChartInfo().getColorField()).thenReturn(colorField);

      ChartColorsRequest request = new ChartColorsRequest();
      request.setWizRuntimeId("rt-1");
      request.setAssemblyName("vs_1");
      request.setPaletteName("NotARealPalette");
      request.setCopy(true);

      ResponseStatusException thrown =
         assertThrows(ResponseStatusException.class, () -> service.setChartColors(request, null));

      assertEquals(HttpStatus.BAD_REQUEST, thrown.getStatusCode());
      assertTrue(thrown.getReason().contains("Unknown palette"), thrown.getReason());
      // No copy was made, so there is nothing to roll back and nothing to undo.
      verify(wizVsService, never()).duplicatePrimaryAssembly(any(), any());
      verify(vs, never()).removeAssembly(anyString());
      verify(wizVsService, never()).persistViewsheet(any(), any(), any());
      verify(wizVsService, never()).fetchAssemblyData(anyString(), anyString(), any());
   }

   @Test
   void aMalformedHexIsRejectedBeforeAnyColorIsPainted() throws Exception {
      // parseColor used to throw from inside the apply, part-way through the map — the entries before the
      // bad one were already painted, and an in-place apply has no copy to roll back, so they stayed.
      // Formats are now parsed up front, which is what makes "apply nothing on rejection" true rather
      // than true-for-most-inputs.
      AestheticRef colorField = mock(AestheticRef.class);
      when(colorField.getDataRef()).thenReturn(mock(DataRef.class));
      when(chart.getChartInfo().getVSChartInfo().getColorField()).thenReturn(colorField);

      // Insertion-ordered so the VALID entry is processed first: that is the one the old flow painted
      // and kept before throwing on the second.
      Map<String, String> categories = new LinkedHashMap<>();
      categories.put("CA", "#ff0000");
      categories.put("NY", "not-a-hex");

      ChartColorsRequest request = new ChartColorsRequest();
      request.setWizRuntimeId("rt-1");
      request.setAssemblyName("vs_1");
      request.setCategoryColors(categories);

      assertThrows(ResponseStatusException.class, () -> service.setChartColors(request, null));

      verify(colorField, never()).setVisualFrame(any());
      verify(box, never()).clearGraph(anyString());
   }

   @Test
   void measureColorsPaintsAnAestheticAggregateThatIsNotInXOrY() throws Exception {
      // The Gantt shape: GanttVSChartInfo adds its start/end/milestone fields to
      // getAestheticAggregateRefs, and none of them appear in the X or Y arrays. Validation reads the
      // former and the apply used to scan only the latter, so a key could pass every check and then paint
      // nothing — silently, which is the failure this endpoint exists to remove. The paint set is now the
      // union of both, so anything validateMeasureKeys accepts is something the apply can reach.
      VSChartAggregateRef aestheticOnly = mock(VSChartAggregateRef.class);
      when(aestheticOnly.getFullName()).thenReturn("Max(end_date)");
      VSChartInfo vsChartInfo = chart.getChartInfo().getVSChartInfo();
      when(vsChartInfo.getAestheticAggregateRefs(anyBoolean()))
         .thenReturn(new ArrayList<>(List.of(aestheticOnly)));

      ChartColorsRequest request = new ChartColorsRequest();
      request.setWizRuntimeId("rt-1");
      request.setAssemblyName("vs_1");
      request.setMeasureColors(Map.of("Max(end_date)", "#d62728"));

      service.setChartColors(request, null);

      ArgumentCaptor<ColorFrame> captor = ArgumentCaptor.forClass(ColorFrame.class);
      verify(aestheticOnly).setColorFrame(captor.capture());
      assertEquals(new Color(0xd6, 0x27, 0x28),
         assertInstanceOf(StaticColorFrame.class, captor.getValue()).getColor());
   }

   @Test
   void measureColorsNamingNoColorableMeasureIsRejectedWithTheValidNamesListed() throws Exception {
      VSChartAggregateRef colorable = mock(VSChartAggregateRef.class);
      when(colorable.getFullName()).thenReturn("Sum(sales)");
      when(chart.getChartInfo().getVSChartInfo().getAestheticAggregateRefs(anyBoolean()))
         .thenReturn(new ArrayList<>(List.of(colorable)));

      ChartColorsRequest request = new ChartColorsRequest();
      request.setWizRuntimeId("rt-1");
      request.setAssemblyName("vs_1");
      request.setMeasureColors(Map.of("Sum(revenue)", "#d62728"));

      ResponseStatusException thrown =
         assertThrows(ResponseStatusException.class, () -> service.setChartColors(request, null));

      assertEquals(HttpStatus.BAD_REQUEST, thrown.getStatusCode());
      // Both halves matter: an automated caller needs to know which key was wrong AND what it could
      // have said instead, or the retry is another guess.
      assertTrue(thrown.getReason().contains("Sum(revenue)"), thrown.getReason());
      assertTrue(thrown.getReason().contains("Sum(sales)"), thrown.getReason());
      verify(colorable, never()).setColorFrame(any());
   }

   @Test
   void paletteAndColorListTogetherAreRejectedRatherThanSilentlyDiscardingOne() throws Exception {
      AestheticRef colorField = mock(AestheticRef.class);
      when(colorField.getDataRef()).thenReturn(mock(DataRef.class));
      when(chart.getChartInfo().getVSChartInfo().getColorField()).thenReturn(colorField);

      ChartColorsRequest request = new ChartColorsRequest();
      request.setWizRuntimeId("rt-1");
      request.setAssemblyName("vs_1");
      request.setPaletteName("Blues");
      request.setColorList(List.of("#ff0000"));

      ResponseStatusException thrown =
         assertThrows(ResponseStatusException.class, () -> service.setChartColors(request, null));

      assertEquals(HttpStatus.BAD_REQUEST, thrown.getStatusCode());
      assertTrue(thrown.getReason().contains("mutually exclusive"), thrown.getReason());
      verify(colorField, never()).setVisualFrame(any());
   }

   @Test
   void anEmptyColorRequestIsRejected() {
      ChartColorsRequest request = new ChartColorsRequest();
      request.setWizRuntimeId("rt-1");
      request.setAssemblyName("vs_1");

      ResponseStatusException thrown =
         assertThrows(ResponseStatusException.class, () -> service.setChartColors(request, null));

      assertEquals(HttpStatus.BAD_REQUEST, thrown.getStatusCode());
   }

   /** A successful copy whose color application also succeeds cleanly (static color, no color field). */
   private ChartVSAssembly successfulCopyChart() {
      VSChartAggregateRef copyYAgg = mock(VSChartAggregateRef.class);
      VSChartInfo copyChartInfo = mock(VSChartInfo.class);
      when(copyChartInfo.getColorField()).thenReturn(null);
      when(copyChartInfo.getYFields()).thenReturn(new ChartRef[] { copyYAgg });
      when(copyChartInfo.getXFields()).thenReturn(new ChartRef[0]);
      when(copyChartInfo.getRTYFields()).thenReturn(new ChartRef[0]);
      when(copyChartInfo.getRTXFields()).thenReturn(new ChartRef[0]);
      ChartVSAssemblyInfo copyInfo = mock(ChartVSAssemblyInfo.class);
      when(copyInfo.getVSChartInfo()).thenReturn(copyChartInfo);
      ChartVSAssembly copyChart = mock(ChartVSAssembly.class);
      when(copyChart.getChartInfo()).thenReturn(copyInfo);
      when(copyChart.getName()).thenReturn("vs_1_copy1");
      return copyChart;
   }

   @Test
   void copySucceedsButFetchAssemblyDataThrowsRollsBackTheDuplicate() throws Exception {
      // fetchAssemblyData runs BEFORE persistViewsheet specifically so that, at the point this throws,
      // nothing has been durably committed yet — the rollback below is always safe to perform.
      ChartVSAssembly copy = successfulCopyChart();
      when(wizVsService.duplicatePrimaryAssembly(rvs, chart)).thenReturn(copy);
      when(wizVsService.fetchAssemblyData(eq("rt-1"), eq("vs_1_copy1"), any()))
         .thenThrow(new RuntimeException("sandbox execution failed"));

      ChartColorsRequest request = staticRed();
      request.setCopy(true);

      assertThrows(RuntimeException.class, () -> service.setChartColors(request, null));

      verify(vs).removeAssembly("vs_1_copy1");
      verify(chart).setPrimary(true);
      verify(wizVsService, never()).persistViewsheet(any(), any(), any());
   }

   @Test
   void copySucceedsButPersistViewsheetThrowsRollsBackTheDuplicate() throws Exception {
      // The scenario claude[bot] flagged in re-review: a failure in persistViewsheet itself (bad
      // identifier / repository save failure) must roll back the same as a failure earlier in the
      // block — the copy was added and promoted live but never durably committed.
      ChartVSAssembly copy = successfulCopyChart();
      when(wizVsService.duplicatePrimaryAssembly(rvs, chart)).thenReturn(copy);
      when(wizVsService.persistViewsheet(any(), any(), any()))
         .thenThrow(new IllegalArgumentException("invalid identifier"));

      ChartColorsRequest request = staticRed();
      request.setCopy(true);
      request.setViewsheetIdentifier("visualizations-xyz");

      assertThrows(IllegalArgumentException.class, () -> service.setChartColors(request, null));

      verify(vs).removeAssembly("vs_1_copy1");
      verify(chart).setPrimary(true);
      // fetchAssemblyData already ran (it comes before persist) — the failure is specifically in persist.
      verify(wizVsService).fetchAssemblyData(eq("rt-1"), eq("vs_1_copy1"), any());
   }
}
