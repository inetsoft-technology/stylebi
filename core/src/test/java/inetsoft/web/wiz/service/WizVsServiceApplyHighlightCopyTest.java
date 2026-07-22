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
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.TextVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;
import inetsoft.web.wiz.model.ApplyHighlightModel;
import inetsoft.web.wiz.model.CreateViewsheetResult;
import inetsoft.web.wiz.model.VisualizationConditionModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * applyHighlight's copy-then-apply wiring (model.isCopy()) mirrors setChartFormat/setChartColors'
 * duplicate-before-apply + rollback-on-failure pattern exactly — see
 * {@link WizAutoBindingServiceSetChartColorsTest} for the equivalent coverage on that path, and
 * {@link WizVsServiceDuplicatePrimaryAssemblyTest} for duplicatePrimaryAssembly's own correctness
 * (not re-tested here).
 *
 * <p>executeAndExtract/collectFlatBinding are stubbed via a Mockito spy on a real WizVsService
 * instance (applyHighlight calls them on {@code this}, so an externally-injected mock collaborator
 * — the pattern WizAutoBindingServiceSetChartColorsTest uses for wizVsService.fetchAssemblyData —
 * isn't available here). This isolates the copy-then-apply wiring itself, the only new logic in
 * this class, from the pre-existing (and separately untested) sandbox-execution machinery.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class WizVsServiceApplyHighlightCopyTest {
   private WizVsService service;
   private RuntimeViewsheet rvs;
   private Viewsheet vs;
   private VSAssembly assembly;
   private TextVSAssemblyInfo assemblyInfo;

   @BeforeEach
   void setUp() throws Exception {
      ViewsheetService viewsheetService = mock(ViewsheetService.class);
      AssetRepository engine = mock(AssetRepository.class);
      SecurityEngine securityEngine = mock(SecurityEngine.class);

      WizVsService real = new WizVsService(viewsheetService, engine, securityEngine);
      service = spy(real);

      // A simple TEXT output assembly — the highlight branch with the fewest collaborators to satisfy,
      // since it doesn't need chart binding refs or a table lens like the chart/table branches do.
      // Which branch runs is irrelevant to what's under test (the copy-then-apply wiring around
      // whichever branch fires). getScalarBindingInfo() defaults to null, which the branch already
      // handles (falls back to XSchema.STRING).
      assemblyInfo = mock(TextVSAssemblyInfo.class);
      assembly = mock(VSAssembly.class);
      when(assembly.getName()).thenReturn("vs_1");
      when(assembly.getVSAssemblyInfo()).thenReturn(assemblyInfo);

      vs = mock(Viewsheet.class);
      when(vs.getAssembly("vs_1")).thenReturn(assembly);
      when(vs.getWizInfo()).thenReturn(new Viewsheet.WizInfo(true, null, null));
      inetsoft.uql.viewsheet.ViewsheetInfo vsInfo = mock(inetsoft.uql.viewsheet.ViewsheetInfo.class);
      when(vsInfo.isMetadata()).thenReturn(false);
      when(vs.getViewsheetInfo()).thenReturn(vsInfo);

      rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(rvs.getID()).thenReturn("rt-1");
      when(viewsheetService.getViewsheet(anyString(), any())).thenReturn(rvs);

      // Bypass the real sandbox-execution machinery (private/heavy; not what this test covers).
      doReturn(new CreateViewsheetResult()).when(service).executeAndExtract(any(), any(), anyInt());
      doReturn(null).when(service).collectFlatBinding(any());
   }

   /** A minimal but genuinely valid highlight rule — a flat "Category = 'A'" condition. */
   private static ApplyHighlightModel.Highlight simpleRule() {
      ApplyHighlightModel.Highlight rule = new ApplyHighlightModel.Highlight();
      rule.setName("r1");
      rule.setForeground("#ff0000");
      VisualizationConditionModel.ConditionSpec spec = new VisualizationConditionModel.ConditionSpec(
         "Category", null, null, null, null, false, null, null,
         List.of(new VisualizationConditionModel.ValueSpec("VALUE", "A", null)));
      rule.setConditions(List.of(new VisualizationConditionModel.ConditionLeaf(null, spec)));
      return rule;
   }

   private static ApplyHighlightModel request(boolean copy) {
      ApplyHighlightModel model = new ApplyHighlightModel();
      model.setRuntimeId("rt-1");
      model.setAssemblyName("vs_1");
      model.setCopy(copy);
      ApplyHighlightModel.HighlightModel hm = new ApplyHighlightModel.HighlightModel();
      hm.setHighlights(List.of(simpleRule()));
      model.setHighlightModel(hm);
      return model;
   }

   @Test
   void copyFalseNeverCallsDuplicatePrimaryAssembly() throws Exception {
      service.applyHighlight(request(false), null);

      verify(service, never()).duplicatePrimaryAssembly(any(), any());
      verify(assemblyInfo).setHighlightGroup(any());
   }

   @Test
   void copyTrueDuplicatesBeforeApplyingAndTargetsTheCopy() throws Exception {
      VSAssembly copy = mock(VSAssembly.class);
      TextVSAssemblyInfo copyInfo = mock(TextVSAssemblyInfo.class);
      when(copy.getName()).thenReturn("vs_1_copy1");
      when(copy.getVSAssemblyInfo()).thenReturn(copyInfo);

      doReturn(copy).when(service).duplicatePrimaryAssembly(rvs, assembly);

      CreateViewsheetResult result = service.applyHighlight(request(true), null);

      // Applied to the COPY's info, never the original's.
      verify(copyInfo).setHighlightGroup(any());
      verify(assemblyInfo, never()).setHighlightGroup(any());
      assertEquals("vs_1_copy1", result.getAssemblyName());
      assertNull(result.getNote());
   }

   @Test
   void copyTrueButDuplicationFailsFallsBackToInPlaceWithANote() throws Exception {
      doReturn(null).when(service).duplicatePrimaryAssembly(rvs, assembly);

      CreateViewsheetResult result = service.applyHighlight(request(true), null);

      // Falls back to the ORIGINAL assembly rather than failing the whole request.
      verify(assemblyInfo).setHighlightGroup(any());
      assertEquals("vs_1", result.getAssemblyName());
      assertEquals("Copy requested but could not be created; highlight applied in place.", result.getNote());
   }

   @Test
   void copySucceedsThenApplyThrowsRollsBackTheDuplicateAndRestoresTheOriginalAsPrimary() throws Exception {
      // A copy whose info type matches NEITHER chart/output/table-data forces the "does not support
      // highlighting" throw AFTER duplicatePrimaryAssembly has already mutated the live runtime
      // (added the copy, demoted the original, promoted the copy) but before persistViewsheet ever
      // runs. That live-runtime mutation must be undone, not left dangling — mirrors
      // WizAutoBindingServiceSetChartColorsTest.copySucceedsThenApplyThrowsRollsBack...
      VSAssembly copy = mock(VSAssembly.class);
      VSAssemblyInfo unsupportedInfo = mock(VSAssemblyInfo.class);
      when(copy.getName()).thenReturn("vs_1_copy1");
      when(copy.getVSAssemblyInfo()).thenReturn(unsupportedInfo);

      doReturn(copy).when(service).duplicatePrimaryAssembly(rvs, assembly);

      assertThrows(IllegalArgumentException.class, () -> service.applyHighlight(request(true), null));

      // Rollback: the duplicated assembly is removed from the live viewsheet and the original is
      // re-promoted to primary.
      verify(vs).removeAssembly("vs_1_copy1");
      verify(assembly).setPrimary(true);
   }

   /** A successful copy whose highlight application also succeeds cleanly (TEXT branch). */
   private VSAssembly successfulCopy() {
      VSAssembly copy = mock(VSAssembly.class);
      TextVSAssemblyInfo copyInfo = mock(TextVSAssemblyInfo.class);
      when(copy.getName()).thenReturn("vs_1_copy1");
      when(copy.getVSAssemblyInfo()).thenReturn(copyInfo);
      return copy;
   }

   @Test
   void copySucceedsButExecuteAndExtractThrowsRollsBackTheDuplicate() throws Exception {
      // executeAndExtract runs BEFORE persistViewsheet specifically so that, at the point this throws,
      // nothing has been durably committed yet — the rollback below is always safe to perform. Mirrors
      // WizAutoBindingServiceSetChartColorsTest.copySucceedsButFetchAssemblyDataThrowsRollsBackTheDuplicate.
      VSAssembly copy = successfulCopy();
      doReturn(copy).when(service).duplicatePrimaryAssembly(rvs, assembly);
      doThrow(new RuntimeException("sandbox execution failed"))
         .when(service).executeAndExtract(any(), eq(copy), anyInt());

      assertThrows(RuntimeException.class, () -> service.applyHighlight(request(true), null));

      verify(vs).removeAssembly("vs_1_copy1");
      verify(assembly).setPrimary(true);
      verify(service, never()).persistViewsheet(any(), any(), any());
   }

   @Test
   void copySucceedsButPersistViewsheetThrowsRollsBackTheDuplicate() throws Exception {
      // The scenario flagged in the PR #4334 re-review: a failure in persistViewsheet itself (bad
      // identifier / repository save failure) must roll back the same as a failure earlier in the
      // block — the copy was added and promoted live but never durably committed. Mirrors
      // WizAutoBindingServiceSetChartColorsTest.copySucceedsButPersistViewsheetThrowsRollsBackTheDuplicate.
      VSAssembly copy = successfulCopy();
      doReturn(copy).when(service).duplicatePrimaryAssembly(rvs, assembly);
      doThrow(new IllegalArgumentException("invalid identifier"))
         .when(service).persistViewsheet(any(), any(), any());

      ApplyHighlightModel request = request(true);
      request.setViewsheetIdentifier("visualizations-xyz");

      assertThrows(IllegalArgumentException.class, () -> service.applyHighlight(request, null));

      verify(vs).removeAssembly("vs_1_copy1");
      verify(assembly).setPrimary(true);
      // executeAndExtract already ran (it comes before persist) — the failure is specifically in persist.
      verify(service).executeAndExtract(any(), eq(copy), anyInt());
   }
}
