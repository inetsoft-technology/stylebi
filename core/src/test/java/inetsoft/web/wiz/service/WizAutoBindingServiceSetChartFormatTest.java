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
import inetsoft.report.composition.ExpiredSheetException;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.sree.security.SecurityException;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.uql.viewsheet.graph.ChartDescriptor;
import inetsoft.uql.viewsheet.graph.LegendsDescriptor;
import inetsoft.web.wiz.model.ChartFormatRequest;
import inetsoft.web.wiz.model.CreateViewsheetResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.security.Principal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * setChartFormat mutates the live runtime chart in place. save_viewsheet clones the assembly from
 * the PERSISTED viewsheet (assetRepository.getSheet), NOT this runtime — so a format change left
 * only on the runtime (the chart title above the plot in particular) is silently dropped on save.
 * The service MUST commit the mutation back to the backing asset via persistViewsheet.
 * Regression test for the live "title still shows 'Chart' after set + save" bug (2026-06-25).
 */
@Tag("core")
class WizAutoBindingServiceSetChartFormatTest {
   private WizAutoBindingService service;
   private ViewsheetService viewsheetService;
   private WizVsService wizVsService;
   private Viewsheet vs;
   private ChartVSAssemblyInfo info;
   private ViewsheetSandbox box;
   private SecurityEngine securityEngine;
   private RuntimeViewsheet rvs;
   private ChartVSAssembly chart;

   @BeforeEach
   void setUp() throws Exception {
      viewsheetService = mock(ViewsheetService.class);
      wizVsService = mock(WizVsService.class);
      // collaborators not used by setChartFormat; their classes can't be initialized in a plain
      // unit-test environment (no Spring context), so pass null instead of mocks.
      securityEngine = mock(SecurityEngine.class);
      when(securityEngine.checkPermission(any(), any(), anyString(), any())).thenReturn(true);
      service = new WizAutoBindingService(
         viewsheetService, null, null, null, null, wizVsService, securityEngine);

      // getChartInfo() and getVSAssemblyInfo() both return the ChartVSAssemblyInfo for a chart;
      // one mock backs both. A title-only request leaves the descriptor null, so every axis/
      // scale/legend/marker block is guarded out.
      info = mock(ChartVSAssemblyInfo.class);
      when(info.getChartDescriptor()).thenReturn(null);
      when(info.getVSChartInfo()).thenReturn(null);

      chart = mock(ChartVSAssembly.class);
      when(chart.getChartInfo()).thenReturn(info);
      when(chart.getVSAssemblyInfo()).thenReturn(info);

      vs = mock(Viewsheet.class);
      when(vs.getAssembly("vs_1")).thenReturn(chart);

      box = mock(ViewsheetSandbox.class);
      rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(box));
      when(rvs.getID()).thenReturn("rt-1");
      when(viewsheetService.getViewsheet(anyString(), any())).thenReturn(rvs);
      when(wizVsService.fetchAssemblyData(anyString(), anyString(), any()))
         .thenReturn(new CreateViewsheetResult());
   }

   private static ChartFormatRequest titleRequest(String identifier) {
      ChartFormatRequest request = new ChartFormatRequest();
      request.setWizRuntimeId("rt-1");
      request.setAssemblyName("vs_1");
      request.setChartTitle("Contacts per Account");
      request.setViewsheetIdentifier(identifier);
      return request;
   }

   @Test
   void chartTitleIsAppliedAndMadeVisible() throws Exception {
      service.setChartFormat(titleRequest("visualizations-xyz"), null);

      verify(info).setTitleValue("Contacts per Account");
      verify(info).setTitleVisible(true);
   }

   // When the runtime was reaped, WizUtil.getViewsheetOrRestore reopens it from the durable identifier
   // under a NEW id; setChartFormat must echo that live id in the result so the client adopts it next.
   @Test
   void echoesTheRestoredRuntimeIdWhenTheRuntimeWasReopened() throws Exception {
      // rt-1 was reaped: getViewsheet throws; the identifier is reopened as rt-restored.
      RuntimeViewsheet restored = mock(RuntimeViewsheet.class);
      when(restored.getViewsheet()).thenReturn(vs);
      when(restored.getViewsheetSandbox()).thenReturn(Optional.of(box));
      when(restored.getID()).thenReturn("rt-restored");
      when(viewsheetService.getViewsheet(eq("rt-1"), any()))
         .thenThrow(new ExpiredSheetException("rt-1", null));
      when(viewsheetService.openViewsheet(any(AssetEntry.class), any(), eq(false)))
         .thenReturn("rt-restored");
      when(viewsheetService.getViewsheet(eq("rt-restored"), any())).thenReturn(restored);
      when(wizVsService.fetchAssemblyData(eq("rt-restored"), anyString(), any()))
         .thenReturn(new CreateViewsheetResult());

      CreateViewsheetResult result =
         service.setChartFormat(titleRequest("1^128^__NULL__^visualizations-abc/chart1^host-org"), null);

      assertEquals("rt-restored", result.getRuntimeId());
   }

   @Test
   void chartTitleIsPersistedToTheBackingAssetSoSaveKeepsIt() throws Exception {
      // The crux: without this persist, save_viewsheet reads the stored asset (title "Chart") and
      // the runtime title is lost. Must write the runtime viewsheet back to its identifier.
      service.setChartFormat(titleRequest("visualizations-xyz"), null);

      verify(wizVsService).persistViewsheet(same(vs), eq("visualizations-xyz"), isNull());
   }

   @Test
   void noPersistWhenThereIsNoBackingIdentifier() throws Exception {
      // With no identifier there is no managed asset to update; persisting would mint a stray new
      // asset. The title still applies to the runtime; we just must not persist blindly.
      service.setChartFormat(titleRequest(null), null);

      verify(info).setTitleValue("Contacts per Account");
      verify(wizVsService, never()).persistViewsheet(any(), any(), any());
   }

   @Test
   void deniedPermissionThrowsAndSkipsRuntimeLookupAndMutation() throws Exception {
      // The action gate is the first statement in setChartFormat, before the runtime is even
      // resolved. A denied caller must throw and touch nothing.
      when(securityEngine.checkPermission(any(), any(), anyString(), any())).thenReturn(false);

      assertThrows(SecurityException.class,
         () -> service.setChartFormat(titleRequest("visualizations-xyz"), null));

      verify(viewsheetService, never()).getViewsheet(anyString(), any());
      verify(info, never()).setTitleValue(any());
      verify(wizVsService, never()).persistViewsheet(any(), any(), any());
   }

   // ── copy=true (copy-then-apply) ──────────────────────────────────────────────────────────

   @Test
   void copyTrueDuplicatesBeforeApplyingAndTargetsTheCopy() throws Exception {
      // duplicatePrimaryAssembly's real behavior (uniqueAssemblyName + rebind) is covered by
      // WizVsServiceDuplicatePrimaryAssemblyTest; wizVsService is mocked here, so this only needs to
      // prove setChartFormat WIRES the copy in and applies to it instead of the original.
      ChartVSAssemblyInfo copyInfo = mock(ChartVSAssemblyInfo.class);
      ChartVSAssembly copyChart = mock(ChartVSAssembly.class);
      when(copyChart.getChartInfo()).thenReturn(copyInfo);
      when(copyChart.getVSAssemblyInfo()).thenReturn(copyInfo);
      when(copyChart.getName()).thenReturn("vs_1_copy1");

      when(wizVsService.duplicatePrimaryAssembly(rvs, chart)).thenReturn(copyChart);
      when(wizVsService.fetchAssemblyData("rt-1", "vs_1_copy1", null))
         .thenReturn(new CreateViewsheetResult());

      ChartFormatRequest request = titleRequest("visualizations-xyz");
      request.setCopy(true);

      CreateViewsheetResult result = service.setChartFormat(request, null);

      // Applied to the COPY's info, never the original's.
      verify(copyInfo).setTitleValue("Contacts per Account");
      verify(info, never()).setTitleValue(any());
      // The cached graph cleared is the copy's, not the original's.
      verify(box).clearGraph("vs_1_copy1");
      verify(box, never()).clearGraph("vs_1");
      assertEquals("vs_1_copy1", result.getAssemblyName());
   }

   @Test
   void copyFalseNeverCallsDuplicatePrimaryAssembly() throws Exception {
      service.setChartFormat(titleRequest("visualizations-xyz"), null);

      verify(wizVsService, never()).duplicatePrimaryAssembly(any(), any());
   }

   @Test
   void copyTrueButDuplicationFailsFallsBackToInPlaceWithANote() throws Exception {
      when(wizVsService.duplicatePrimaryAssembly(rvs, chart)).thenReturn(null);

      ChartFormatRequest request = titleRequest("visualizations-xyz");
      request.setCopy(true);

      CreateViewsheetResult result = service.setChartFormat(request, null);

      // Falls back to the ORIGINAL assembly rather than failing the whole request.
      verify(info).setTitleValue("Contacts per Account");
      assertEquals("vs_1", result.getAssemblyName());
      assertEquals("Copy requested but could not be created; format applied in place.", result.getNote());
   }

   @Test
   void copyFailureNoteSurvivesAlongsideAnUnrelatedLegendNote() throws Exception {
      // Regression: the copy-fallback note used to be stored in the SAME `note` local later
      // reassigned unconditionally by the legend-validation logic, silently discarding the
      // copy-failure warning whenever the legend position was ALSO invalid. Both must now
      // survive, concatenated.
      LegendsDescriptor legends = mock(LegendsDescriptor.class);
      ChartDescriptor desc = mock(ChartDescriptor.class);
      when(desc.getLegendsDescriptor()).thenReturn(legends);
      when(info.getChartDescriptor()).thenReturn(desc);
      when(wizVsService.duplicatePrimaryAssembly(rvs, chart)).thenReturn(null);

      ChartFormatRequest request = new ChartFormatRequest();
      request.setWizRuntimeId("rt-1");
      request.setAssemblyName("vs_1");
      request.setLegendPosition("sideways");
      request.setCopy(true);

      CreateViewsheetResult result = service.setChartFormat(request, null);

      assertEquals(
         "Copy requested but could not be created; format applied in place. " +
         "Unknown legendPosition 'sideways'; valid: none, top, right, bottom, left, in_place. Legend left unchanged.",
         result.getNote());
   }

   @Test
   void copySucceedsThenApplyThrowsRollsBackTheDuplicateAndRestoresTheOriginalAsPrimary() throws Exception {
      // Any throw between the successful duplication and persistViewsheet must roll back the
      // live-runtime mutation duplicatePrimaryAssembly already made (copy added + promoted,
      // original demoted) rather than leave it dangling and unreported.
      ChartVSAssemblyInfo copyInfo = mock(ChartVSAssemblyInfo.class);
      ChartVSAssembly copyChart = mock(ChartVSAssembly.class);
      when(copyChart.getChartInfo()).thenReturn(copyInfo);
      when(copyChart.getVSAssemblyInfo()).thenReturn(copyInfo);
      when(copyChart.getName()).thenReturn("vs_1_copy1");
      doThrow(new IllegalStateException("boom")).when(copyInfo).setTitleValue(anyString());

      when(wizVsService.duplicatePrimaryAssembly(rvs, chart)).thenReturn(copyChart);

      ChartFormatRequest request = titleRequest("visualizations-xyz");
      request.setCopy(true);

      assertThrows(IllegalStateException.class, () -> service.setChartFormat(request, null));

      verify(vs).removeAssembly("vs_1_copy1");
      verify(chart).setPrimary(true);
      verify(wizVsService, never()).persistViewsheet(any(), any(), any());
      verify(wizVsService, never()).fetchAssemblyData(anyString(), anyString(), any());
   }

   /** A successful copy whose format application (title-only) also succeeds cleanly. */
   private ChartVSAssembly successfulCopyChart() {
      ChartVSAssemblyInfo copyInfo = mock(ChartVSAssemblyInfo.class);
      ChartVSAssembly copyChart = mock(ChartVSAssembly.class);
      when(copyChart.getChartInfo()).thenReturn(copyInfo);
      when(copyChart.getVSAssemblyInfo()).thenReturn(copyInfo);
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

      ChartFormatRequest request = titleRequest("visualizations-xyz");
      request.setCopy(true);

      assertThrows(RuntimeException.class, () -> service.setChartFormat(request, null));

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

      ChartFormatRequest request = titleRequest("visualizations-xyz");
      request.setCopy(true);

      assertThrows(IllegalArgumentException.class, () -> service.setChartFormat(request, null));

      verify(vs).removeAssembly("vs_1_copy1");
      verify(chart).setPrimary(true);
      // fetchAssemblyData already ran (it comes before persist) — the failure is specifically in persist.
      verify(wizVsService).fetchAssemblyData(eq("rt-1"), eq("vs_1_copy1"), any());
   }
}
