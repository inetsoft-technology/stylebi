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
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.ViewsheetInfo;
import inetsoft.web.wiz.model.CreateVisualizationModel;
import inetsoft.web.wiz.model.CreateViewsheetResult;
import inetsoft.web.wiz.model.VisualizationConditionModel;

import java.security.Principal;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
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
 * createViewsheetInternal's "modificationOnly" (in-place filter) path's copy-then-apply wiring
 * (model.isCopy()) mirrors setChartFormat/setChartColors/applyHighlight's duplicate-before-apply +
 * rollback-on-failure pattern exactly — see {@link WizAutoBindingServiceSetChartColorsTest} for the
 * equivalent coverage on that path, and {@link WizVsServiceDuplicatePrimaryAssemblyTest} /
 * {@link WizVsServiceDuplicateAssemblyTest} for the duplication helpers' own correctness (not
 * re-tested here). This path calls {@code duplicateAssembly} — not {@code duplicatePrimaryAssembly}
 * — because the chart it copies need not be the current primary: the caller may name an earlier
 * chart via {@code assemblyName}.
 *
 * <p>executeAndExtract/collectFlatBinding are stubbed via a Mockito spy on a real WizVsService
 * instance, same as {@link WizVsServiceApplyHighlightCopyTest} — this isolates the copy-then-apply
 * wiring itself, the only new logic in this path, from the pre-existing (and separately untested)
 * sandbox-execution machinery.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class WizVsServiceFilterCopyTest {
   private WizVsService service;
   private RuntimeViewsheet rvs;
   private Viewsheet vs;
   private ChartVSAssembly assembly;
   private Principal user;

   @BeforeEach
   void setUp() throws Exception {
      ViewsheetService viewsheetService = mock(ViewsheetService.class);
      AssetRepository engine = mock(AssetRepository.class);
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      when(securityEngine.checkPermission(any(), any(), anyString(), any())).thenReturn(true);
      user = mock(Principal.class);

      WizVsService real = new WizVsService(viewsheetService, engine, securityEngine, null, null);
      service = spy(real);

      // The current primary assembly (isPrimary()=true) that findPrimaryAssembly resolves in the
      // modificationOnly path. getViewsheet()=null makes VSUtil.getBaseColumns return an empty
      // ColumnSelection (its documented behavior with no base worksheet) rather than needing a full
      // binding setup — irrelevant to what this test covers (the copy-then-apply wiring, not
      // condition-to-column resolution).
      assembly = mock(ChartVSAssembly.class);
      when(assembly.getName()).thenReturn("vs_1");
      when(assembly.isPrimary()).thenReturn(true);

      vs = mock(Viewsheet.class);
      when(vs.getAssemblies()).thenReturn(new Assembly[] { assembly });
      when(vs.getWizInfo()).thenReturn(new Viewsheet.WizInfo(true, null, null));
      ViewsheetInfo vsInfo = mock(ViewsheetInfo.class);
      when(vsInfo.isMetadata()).thenReturn(false);
      when(vs.getViewsheetInfo()).thenReturn(vsInfo);

      rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(rvs.getID()).thenReturn("rt-1");
      when(viewsheetService.getViewsheet(anyString(), any())).thenReturn(rvs);

      // Bypass the real sandbox-execution machinery (private/heavy; not what this test covers).
      doReturn(new CreateViewsheetResult()).when(service).executeAndExtract(any(), any(), anyInt());
      doReturn(null).when(service).collectFlatBinding(any());
      // persistViewsheet is always called unconditionally by createViewsheetInternal (unlike
      // applyHighlight, which only persists when a viewsheetIdentifier is supplied); stub it out here
      // so the base success-path tests don't need real repository-save machinery. Tests that
      // specifically cover the persistViewsheet-throws rollback override this locally.
      doReturn("vs-identifier").when(service).persistViewsheet(any(), any(), any());
   }

   /** A minimal but genuinely valid base condition — a flat "Category = 'A'" leaf. */
   private static VisualizationConditionModel simpleCondition() {
      VisualizationConditionModel cm = new VisualizationConditionModel();
      VisualizationConditionModel.ConditionSpec spec = new VisualizationConditionModel.ConditionSpec(
         "Category", null, null, null, null, false, null, null,
         List.of(new VisualizationConditionModel.ValueSpec("VALUE", "A", null)));
      cm.setBaseConditions(List.of(new VisualizationConditionModel.ConditionLeaf(null, spec)));
      return cm;
   }

   private static CreateVisualizationModel request(boolean copy) {
      CreateVisualizationModel model = new CreateVisualizationModel();
      model.setRuntimeId("rt-1");
      model.setConditionModel(simpleCondition());
      model.setCopy(copy);
      return model;
   }

   @Test
   void noAssemblyNameResolvesThePrimaryAssembly() throws Exception {
      // Back-compat: every caller predating the assemblyName parameter omits it and must keep
      // targeting the primary — the chart created or copied most recently.
      service.createViewsheet(request(false), user);

      verify(assembly).setPreConditionList(any());
      verify(vs, never()).getAssembly(anyString());
   }

   @Test
   void anExplicitAssemblyNameTargetsThatChartInsteadOfThePrimary() throws Exception {
      // The reason this parameter exists: a wiz turn editing an EARLIER chart must filter THAT
      // chart, whose fields the conditionModel was built against — not whichever is primary now.
      ChartVSAssembly earlier = mock(ChartVSAssembly.class);
      when(earlier.getName()).thenReturn("vs_0");
      when(earlier.isPrimary()).thenReturn(false);
      when(vs.getAssembly("vs_0")).thenReturn(earlier);

      CreateVisualizationModel model = request(false);
      model.setAssemblyName("vs_0");
      CreateViewsheetResult result = service.createViewsheet(model, user);

      verify(earlier).setPreConditionList(any());
      verify(assembly, never()).setPreConditionList(any());
      assertEquals("vs_0", result.getAssemblyName());
   }

   @Test
   void anUnknownAssemblyNameFailsLoudlyRatherThanSilentlyUsingThePrimary() throws Exception {
      // Falling back to the primary here would apply the condition to a chart the caller did not
      // name — the exact failure this parameter was added to prevent — so it must throw instead.
      when(vs.getAssembly("nope")).thenReturn(null);

      CreateVisualizationModel model = request(false);
      model.setAssemblyName("nope");

      IllegalStateException ex =
         assertThrows(IllegalStateException.class, () -> service.createViewsheet(model, user));
      assertTrue(ex.getMessage().contains("nope"), "the message should name the missing assembly");
      verify(assembly, never()).setPreConditionList(any());
   }

   @Test
   void copyFalseNeverDuplicatesTheAssembly() throws Exception {
      service.createViewsheet(request(false), user);

      verify(service, never()).duplicateAssembly(any(), any());
      verify(assembly).setPreConditionList(any());
   }

   // ── Conditions the request asked for that did not make it into the applied filter ─────────────
   //
   // A dropped condition WIDENS the result instead of emptying it, so the chart renders normally and
   // answers a question nobody asked. The response is the only place that can say so: nothing about
   // the applied ConditionList records which nodes were meant to be in it.

   /** A leaf carrying no field name at all — the shape appendConditionNodes drops. */
   private static VisualizationConditionModel.ConditionLeaf fieldlessLeaf(String junction) {
      return new VisualizationConditionModel.ConditionLeaf(junction,
         new VisualizationConditionModel.ConditionSpec(
            null, null, null, null, null, false, "EQUAL_TO", null,
            List.of(new VisualizationConditionModel.ValueSpec("VALUE", "A", null))));
   }

   private static VisualizationConditionModel.ConditionLeaf validLeaf(String junction, String field) {
      return new VisualizationConditionModel.ConditionLeaf(junction,
         new VisualizationConditionModel.ConditionSpec(
            field, null, null, null, null, false, "EQUAL_TO", null,
            List.of(new VisualizationConditionModel.ValueSpec("VALUE", "A", null))));
   }

   private static CreateVisualizationModel conditionRequest(
      List<VisualizationConditionModel.ConditionNode> nodes)
   {
      VisualizationConditionModel cm = new VisualizationConditionModel();
      cm.setBaseConditions(nodes);

      CreateVisualizationModel model = new CreateVisualizationModel();
      model.setRuntimeId("rt-1");
      model.setConditionModel(cm);
      return model;
   }

   @Test
   void aPartiallyDroppedFilterStillAppliesAndReportsWhatWasDropped() throws Exception {
      // The most dangerous of the two: canApplyConditions passes (the list is non-empty), so the
      // partial filter is written as though it were the complete one and the call is an ordinary
      // success. Only the warning distinguishes it.
      CreateViewsheetResult result = service.createViewsheet(
         conditionRequest(List.of(validLeaf(null, "Category"), fieldlessLeaf("AND"))), user);

      verify(assembly).setPreConditionList(any());
      assertEquals(1, result.getWarnings().size());
      assertEquals("filter", result.getWarnings().get(0).getOption());
      assertTrue(result.getWarnings().get(0).getReason().contains("more data than asked for"),
                 "the reason must say the chart is WIDER than requested, which is the whole risk");
   }

   @Test
   void aFilterThatCouldNotBeBuiltAtAllIsReportedPerNodeAndAsAWhole() throws Exception {
      CreateViewsheetResult result = service.createViewsheet(
         conditionRequest(List.of(fieldlessLeaf(null), fieldlessLeaf("AND"))), user);

      // Behaviour is unchanged: an all-unusable list leaves the existing conditions alone rather than
      // clearing them. Reporting it is the only thing that is new.
      verify(assembly, never()).setPreConditionList(any());
      // Two per-node reasons plus the consequence no single node can state.
      assertEquals(3, result.getWarnings().size());
      assertTrue(result.getWarnings().get(2).getReason().contains("still shows all the data"));
   }

   @Test
   void anUnrecognizedAggregationInAFilterReportsWhatItComparedInstead() throws Exception {
      // The formula falls back to SUM rather than failing, which keeps the chart — but "regions where
      // the AVERAGE sale is over 100" then means "where the TOTAL is over 100", a different set of
      // rows with nothing on screen to give it away.
      CreateViewsheetResult result = service.createViewsheet(conditionRequest(List.of(
         new VisualizationConditionModel.ConditionLeaf(null,
            new VisualizationConditionModel.ConditionSpec(
               "amount", "avg_value", null, null, null, false, "GREATER_THAN", null,
               List.of(new VisualizationConditionModel.ValueSpec("VALUE", "100", null)))))), user);

      verify(assembly).setPreConditionList(any());
      assertEquals(1, result.getWarnings().size());
      assertEquals("filter:amount", result.getWarnings().get(0).getOption());
      assertTrue(result.getWarnings().get(0).getReason().contains("compared the total instead"));
   }

   @Test
   void aFilterThatFullyAppliesReportsNoWarnings() throws Exception {
      // Regression guard: the field must stay ABSENT on the ordinary path, so a caller can test it
      // rather than having to check an always-present empty list.
      assertNull(service.createViewsheet(request(false), user).getWarnings());
   }

   @Test
   void copyTrueDuplicatesBeforeApplyingAndTargetsTheCopy() throws Exception {
      ChartVSAssembly copy = mock(ChartVSAssembly.class);
      when(copy.getName()).thenReturn("vs_1_copy1");

      doReturn(new WizVsService.AssemblyDuplication(copy, assembly))
         .when(service).duplicateAssembly(rvs, assembly);

      CreateViewsheetResult result = service.createViewsheet(request(true), user);

      // Applied to the COPY, never the original.
      verify(copy).setPreConditionList(any());
      verify(assembly, never()).setPreConditionList(any());
      assertEquals("vs_1_copy1", result.getAssemblyName());
      assertNull(result.getNote());
   }

   @Test
   void copyTrueButDuplicationFailsFallsBackToInPlaceWithANote() throws Exception {
      doReturn(null).when(service).duplicateAssembly(rvs, assembly);

      CreateViewsheetResult result = service.createViewsheet(request(true), user);

      // Falls back to the ORIGINAL assembly rather than failing the whole request.
      verify(assembly).setPreConditionList(any());
      assertEquals("vs_1", result.getAssemblyName());
      assertEquals("Copy requested but could not be created; filter applied in place.", result.getNote());
   }

   @Test
   void copySucceedsThenApplyThrowsRollsBackTheDuplicateAndRestoresTheOriginalAsPrimary() throws Exception {
      // Force the condition-application step itself to throw AFTER duplicateAssembly has
      // already mutated the live runtime (added the copy, demoted the original, promoted the copy)
      // but before persistViewsheet ever runs. That live-runtime mutation must be undone, not left
      // dangling — mirrors WizAutoBindingServiceSetChartColorsTest.copySucceedsThenApplyThrowsRollsBack...
      ChartVSAssembly copy = mock(ChartVSAssembly.class);
      when(copy.getName()).thenReturn("vs_1_copy1");
      doThrow(new RuntimeException("boom")).when(copy).setPreConditionList(any());

      doReturn(new WizVsService.AssemblyDuplication(copy, assembly))
         .when(service).duplicateAssembly(rvs, assembly);

      assertThrows(RuntimeException.class, () -> service.createViewsheet(request(true), user));

      // Rollback: the duplicated assembly is removed from the live viewsheet and the original is
      // re-promoted to primary.
      verify(vs).removeAssembly("vs_1_copy1");
      verify(assembly).setPrimary(true);
   }

   @Test
   void copySucceedsButExecuteAndExtractThrowsRollsBackTheDuplicate() throws Exception {
      // executeAndExtract runs BEFORE persistViewsheet specifically so that, at the point this throws,
      // nothing has been durably committed yet — the rollback below is always safe to perform. Mirrors
      // WizVsServiceApplyHighlightCopyTest.copySucceedsButExecuteAndExtractThrowsRollsBackTheDuplicate.
      ChartVSAssembly copy = mock(ChartVSAssembly.class);
      when(copy.getName()).thenReturn("vs_1_copy1");

      doReturn(new WizVsService.AssemblyDuplication(copy, assembly))
         .when(service).duplicateAssembly(rvs, assembly);
      doThrow(new RuntimeException("sandbox execution failed"))
         .when(service).executeAndExtract(any(), eq(copy), anyInt());

      assertThrows(RuntimeException.class, () -> service.createViewsheet(request(true), user));

      verify(vs).removeAssembly("vs_1_copy1");
      verify(assembly).setPrimary(true);
      verify(service, never()).persistViewsheet(any(), any(), any());
   }

   @Test
   void copySucceedsButPersistViewsheetThrowsRollsBackTheDuplicate() throws Exception {
      // Mirrors the scenario flagged in the highlight re-review: a failure in persistViewsheet itself
      // must roll back the same as a failure earlier in the block.
      ChartVSAssembly copy = mock(ChartVSAssembly.class);
      when(copy.getName()).thenReturn("vs_1_copy1");

      doReturn(new WizVsService.AssemblyDuplication(copy, assembly))
         .when(service).duplicateAssembly(rvs, assembly);
      doThrow(new IllegalArgumentException("invalid identifier"))
         .when(service).persistViewsheet(any(), any(), any());

      CreateVisualizationModel request = request(true);
      request.setViewsheetIdentifier("visualizations-xyz");

      assertThrows(IllegalArgumentException.class, () -> service.createViewsheet(request, user));

      verify(vs).removeAssembly("vs_1_copy1");
      verify(assembly).setPrimary(true);
      // The condition was already applied to the copy (succeeded) — the failure is specifically in persist.
      verify(copy).setPreConditionList(any());
   }
}
