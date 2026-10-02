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
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.web.composer.model.vs.ConvertToWorksheetResponseModel;
import inetsoft.web.composer.model.vs.SelectDataSourceDialogModel;
import inetsoft.web.composer.model.vs.ViewsheetParametersDialogModel;
import inetsoft.web.composer.model.vs.ViewsheetPropertyDialogModel;
import inetsoft.web.composer.model.vs.VSOptionsPaneModel;
import inetsoft.web.composer.vs.dialog.ViewsheetPropertyDialogService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("core")
class SheetPropertyServiceTest {
   @Test
   void listsTheViewsheetVocabularyWithNoScriptKey() throws Exception {
      SheetPropertyService service = serviceWith(modelWith(20, "old desc"));

      Map<String, Object> listed = service.list("tok", principal());

      @SuppressWarnings("unchecked")
      List<Map<String, Object>> properties = (List<Map<String, Object>>) listed.get("properties");
      assertNotNull(properties);

      Set<Object> names = new java.util.HashSet<>();

      for(Map<String, Object> property : properties) {
         names.add(property.get("name"));
      }

      assertTrue(names.contains("desc"));
      assertTrue(names.contains("maxRows"));
      assertTrue(names.contains("snapGrid"));

      // Not sheet state: the getter never populates them, so they read back as the Immutables
      // defaults, and the setter uses them only to size a one-off refresh. preview:true also
      // reached VSEventUtil.clearScale, discarding assembly scaling. onDemandMvEnabled is a
      // capability flag the setter never reads.
      for(String phantom : java.util.List.of("width", "height", "preview", "onDemandMvEnabled")) {
         assertFalse(names.contains(phantom), phantom + " is not a settable viewsheet property");
      }

      // filtersPane and localizationPane are deliberately absent: whole object graphs, read-only,
      // and aliasing them made every list/get carry the entire localization component tree.
      // Reading them is what raw:true is for.
      assertFalse(names.contains("filtersPane"));
      assertFalse(names.contains("localizationPane"));

      for(Object name : names) {
         assertFalse(String.valueOf(name).toLowerCase().contains("script"),
                     "no script-named key should appear in the vocabulary: " + name);
      }
   }

   @Test
   void getReturnsCurrentValuesByAlias() throws Exception {
      SheetPropertyService service = serviceWith(modelWith(30, "hello"));

      @SuppressWarnings("unchecked")
      Map<String, Object> values = (Map<String, Object>) service.get("tok", principal(), false);

      assertEquals("hello", values.get("desc"));
      assertEquals(30, values.get("maxRows"));
   }

   @Test
   void getRawReturnsTheWholeModel() throws Exception {
      ViewsheetPropertyDialogModel model = modelWith(30, "hello");
      SheetPropertyService service = serviceWith(model);

      assertSame(model, service.get("tok", principal(), true));
   }

   @Test
   void setWritesAScalarPropertyThroughOneCheckpoint() throws Exception {
      ViewsheetPropertyDialogService dialog = mock(ViewsheetPropertyDialogService.class);
      ViewsheetPropertyDialogModel model = modelWith(20, "old");
      when(dialog.getViewsheetInfo(anyString(), any(Principal.class))).thenReturn(model);
      ViewsheetSessionService sessions = sessionsMock();

      SheetPropertyService service = new SheetPropertyService(sessions, dialog);

      service.set("tok", principal(), Map.of("desc", "new description"), "");

      ArgumentCaptor<ViewsheetPropertyDialogModel> captor =
         ArgumentCaptor.forClass(ViewsheetPropertyDialogModel.class);
      verify(dialog).setViewsheetInfo(eq("rt1"), captor.capture(), any(Principal.class), any(),
                                      anyString(), any());
      assertEquals("new description", captor.getValue().vsOptionsPane().getDesc());

      // One mutate call -- the whole patch is one undo checkpoint, exactly as the assembly
      // path is.
      verify(sessions, times(1)).mutate(anyString(), any(Principal.class), any());
   }

   /**
    * Regression for bug #77041: {@code maxRows: 5000.7} used to be silently truncated onto
    * {@code 5000} through PropertyPath.coerce's int branch, reporting success for a value the
    * caller never asked for.
    */
   @Test
   void refusesANonIntegralMaxRowsRatherThanTruncating() throws Exception {
      ViewsheetPropertyDialogService dialog = mock(ViewsheetPropertyDialogService.class);
      when(dialog.getViewsheetInfo(anyString(), any(Principal.class))).thenReturn(modelWith(20, "old"));
      SheetPropertyService service = new SheetPropertyService(sessionsMock(), dialog);

      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> service.set("tok", principal(), Map.of("maxRows", 5000.7), ""));

      assertTrue(thrown.getMessage().contains("maxRows"));
      verify(dialog, never()).setViewsheetInfo(anyString(), any(), any(Principal.class), any(),
                                               anyString(), any());
   }

   /**
    * Regression for bug #77041: {@code maxRows: 1e12} used to be silently clamped onto
    * {@code Integer.MAX_VALUE} through PropertyPath.coerce's int branch.
    */
   @Test
   void refusesAnOutOfRangeMaxRowsRatherThanClamping() throws Exception {
      ViewsheetPropertyDialogService dialog = mock(ViewsheetPropertyDialogService.class);
      when(dialog.getViewsheetInfo(anyString(), any(Principal.class))).thenReturn(modelWith(20, "old"));
      SheetPropertyService service = new SheetPropertyService(sessionsMock(), dialog);

      assertThrows(
         IllegalArgumentException.class,
         () -> service.set("tok", principal(), Map.of("maxRows", 1e12), ""));
   }

   /*
    * There was a test here proving that set() rebuilds the root when the written field is a
    * top-level Immutables scalar, driven through the "width" alias.
    *
    * It is gone because width/height/preview are no longer in the vocabulary: they are not sheet
    * state, so writing one always was a silent no-op. No alias now targets a bare top-level field,
    * which makes that path unreachable from here.
    *
    * The behaviour it covered is NOT untested. PropertyPath.set still returns the (possibly
    * rebuilt) root, and PropertyPathTest exercises that directly against an Immutables scalar
    * root -- which is the honest place for it, since it is PropertyPath's contract rather than
    * this service's. Keeping the hardening without a vocabulary entry that needs it is
    * deliberate: the next alias that does target a top-level field would otherwise vanish
    * silently, with no compile error.
    */

   @Test
   void aliasIsWritable() throws Exception {
      ViewsheetPropertyDialogService dialog = mock(ViewsheetPropertyDialogService.class);
      ViewsheetPropertyDialogModel model = modelWith(20, "old");
      when(dialog.getViewsheetInfo(anyString(), any(Principal.class))).thenReturn(model);

      SheetPropertyService service = new SheetPropertyService(sessionsMock(), dialog);

      service.set("tok", principal(), Map.of("alias", "Q1 Sales"), "");

      ArgumentCaptor<ViewsheetPropertyDialogModel> captor =
         ArgumentCaptor.forClass(ViewsheetPropertyDialogModel.class);
      verify(dialog).setViewsheetInfo(eq("rt1"), captor.capture(), any(Principal.class), any(),
                                      anyString(), any());
      assertEquals("Q1 Sales", captor.getValue().vsOptionsPane().getAlias());
   }

   @Test
   void refusesToSetTheScriptPaneNamingUpdateScript() throws Exception {
      ViewsheetPropertyDialogService dialog = mock(ViewsheetPropertyDialogService.class);
      SheetPropertyService service = new SheetPropertyService(sessionsMock(), dialog);

      Exception thrown = assertThrows(Exception.class,
         () -> service.set("tok", principal(), Map.of("vsScriptPane", Map.of()), ""));

      assertTrue(thrown.getMessage().contains("update_script"));
      verify(dialog, never()).setViewsheetInfo(anyString(), any(), any(), any(), anyString(),
                                               any());
   }

   @Test
   void refusesAnEmptyPatchRatherThanOpeningACheckpointForNothing() {
      ViewsheetPropertyDialogService dialog = mock(ViewsheetPropertyDialogService.class);
      SheetPropertyService service = new SheetPropertyService(sessionsMock(), dialog);

      assertThrows(Exception.class, () -> service.set("tok", principal(), Map.of(), ""));
   }

   /** Redmine #76997: the base entry's createdDate is a java.util.Date, which has a public
    *  (String) constructor, but is not a type PropertyPath builds from a string. The write was
    *  accepted and mutated the live base entry in place. */
   @Test
   void refusesARawDateWriteOnTheBaseEntryRatherThanParsingIt() throws Exception {
      AssetEntry base = new AssetEntry();
      SelectDataSourceDialogModel dataSource = new SelectDataSourceDialogModel();
      dataSource.setDataSource(base);
      VSOptionsPaneModel options = new VSOptionsPaneModel();
      options.setSelectDataSourceDialogModel(dataSource);
      ViewsheetPropertyDialogService dialog = mock(ViewsheetPropertyDialogService.class);
      when(dialog.getViewsheetInfo(anyString(), any(Principal.class)))
         .thenReturn(ViewsheetPropertyDialogModel.builder().vsOptionsPane(options).build());
      SheetPropertyService service = new SheetPropertyService(sessionsMock(), dialog);
      String key = "vsOptionsPane.selectDataSourceDialogModel.dataSource.createdDate";

      for(Object value : List.of("Jan 2 2024", Map.of("time", 0),
                                 Map.of("value", "Jan 2 2024")))
      {
         Exception thrown = assertThrows(Exception.class,
            () -> service.set("tok", principal(), Map.of(key, value), ""), "refuse " + value);

         // Since #77077 the whole data source subtree is refused before coercion is reached.
         assertTrue(thrown.getMessage().contains("set_viewsheet_data_source"),
                    thrown.getMessage());
      }

      assertNull(base.getCreatedDate());
      verify(dialog, never()).setViewsheetInfo(anyString(), any(), any(), any(), anyString(),
                                               any());
   }

   /** Redmine #76739: the "Customize" parameter list is a plain String[] pair -- proves the
    *  JSON-array-to-String[] coercion PropertyPath already does for other array-typed leaves
    *  also works through this alias, once every named parameter is one the viewsheet's query
    *  actually declares (the pre-existing model already lists Region/Year/Quarter as known,
    *  simulating what a real getViewsheetInfo would report for a parameterized query). */
   @Test
   void enabledAndDisabledParametersAreWritableWhenAlreadyKnown() throws Exception {
      ViewsheetPropertyDialogService dialog = mock(ViewsheetPropertyDialogService.class);
      ViewsheetPropertyDialogModel model =
         modelWithKnownParameters(List.of("Region", "Year", "Quarter"), List.of());
      when(dialog.getViewsheetInfo(anyString(), any(Principal.class))).thenReturn(model);

      SheetPropertyService service = new SheetPropertyService(sessionsMock(), dialog);

      service.set("tok", principal(), Map.of(
         "enabledParameters", List.of("Region", "Year"),
         "disabledParameters", List.of("Quarter")), "");

      ArgumentCaptor<ViewsheetPropertyDialogModel> captor =
         ArgumentCaptor.forClass(ViewsheetPropertyDialogModel.class);
      verify(dialog).setViewsheetInfo(eq("rt1"), captor.capture(), any(Principal.class), any(),
                                      anyString(), any());
      assertArrayEquals(new String[] {"Region", "Year"},
         captor.getValue().vsOptionsPane().getViewsheetParametersDialogModel()
            .getEnabledParameters());
      assertArrayEquals(new String[] {"Quarter"},
         captor.getValue().vsOptionsPane().getViewsheetParametersDialogModel()
            .getDisabledParameters());
   }

   // ── enabledParameters/disabledParameters mutual exclusivity ────────────────

   /**
    * Redmine #76739 follow-up, found live 2026-09-17: patching only disabledParameters left the
    * moved name listed in BOTH arrays on the next read, since enabledParameters (untouched by
    * this patch) still carried it from the pre-patch read. The Composer's own "Customize" dialog
    * can never produce this -- it always submits both columns as one already-partitioned pair.
    * The untouched side must be pruned of whatever the touched side just claimed.
    */
   @Test
   void disablingAParameterRemovesItFromEnabledEvenWhenEnabledIsNotInThePatch() throws Exception {
      ViewsheetPropertyDialogService dialog = mock(ViewsheetPropertyDialogService.class);
      ViewsheetPropertyDialogModel model =
         modelWithKnownParameters(List.of("MinPopulation"), List.of());
      when(dialog.getViewsheetInfo(anyString(), any(Principal.class))).thenReturn(model);

      SheetPropertyService service = new SheetPropertyService(sessionsMock(), dialog);

      service.set("tok", principal(), Map.of("disabledParameters", List.of("MinPopulation")), "");

      ArgumentCaptor<ViewsheetPropertyDialogModel> captor =
         ArgumentCaptor.forClass(ViewsheetPropertyDialogModel.class);
      verify(dialog).setViewsheetInfo(eq("rt1"), captor.capture(), any(Principal.class), any(),
                                      anyString(), any());
      ViewsheetParametersDialogModel written =
         captor.getValue().vsOptionsPane().getViewsheetParametersDialogModel();
      assertArrayEquals(new String[] {"MinPopulation"}, written.getDisabledParameters());
      assertArrayEquals(new String[0], written.getEnabledParameters(),
         "the parameter just disabled must not remain in enabledParameters too");
   }

   /** Same gap, the other direction -- re-enabling a parameter must remove it from
    *  disabledParameters even when disabledParameters is not in the patch. */
   @Test
   void enablingAParameterRemovesItFromDisabledEvenWhenDisabledIsNotInThePatch() throws Exception {
      ViewsheetPropertyDialogService dialog = mock(ViewsheetPropertyDialogService.class);
      ViewsheetPropertyDialogModel model =
         modelWithKnownParameters(List.of(), List.of("MinPopulation"));
      when(dialog.getViewsheetInfo(anyString(), any(Principal.class))).thenReturn(model);

      SheetPropertyService service = new SheetPropertyService(sessionsMock(), dialog);

      service.set("tok", principal(), Map.of("enabledParameters", List.of("MinPopulation")), "");

      ArgumentCaptor<ViewsheetPropertyDialogModel> captor =
         ArgumentCaptor.forClass(ViewsheetPropertyDialogModel.class);
      verify(dialog).setViewsheetInfo(eq("rt1"), captor.capture(), any(Principal.class), any(),
                                      anyString(), any());
      ViewsheetParametersDialogModel written =
         captor.getValue().vsOptionsPane().getViewsheetParametersDialogModel();
      assertArrayEquals(new String[] {"MinPopulation"}, written.getEnabledParameters());
      assertArrayEquals(new String[0], written.getDisabledParameters(),
         "the parameter just enabled must not remain in disabledParameters too");
   }

   /** A patch naming other, unrelated parameters must not have its untouched entries pruned --
    *  only the actual overlap is removed. */
   @Test
   void reconciliationOnlyPrunesTheOverlapNotUnrelatedEntries() throws Exception {
      ViewsheetPropertyDialogService dialog = mock(ViewsheetPropertyDialogService.class);
      ViewsheetPropertyDialogModel model =
         modelWithKnownParameters(List.of("A", "B"), List.of());
      when(dialog.getViewsheetInfo(anyString(), any(Principal.class))).thenReturn(model);

      SheetPropertyService service = new SheetPropertyService(sessionsMock(), dialog);

      service.set("tok", principal(), Map.of("disabledParameters", List.of("A")), "");

      ArgumentCaptor<ViewsheetPropertyDialogModel> captor =
         ArgumentCaptor.forClass(ViewsheetPropertyDialogModel.class);
      verify(dialog).setViewsheetInfo(eq("rt1"), captor.capture(), any(Principal.class), any(),
                                      anyString(), any());
      ViewsheetParametersDialogModel written =
         captor.getValue().vsOptionsPane().getViewsheetParametersDialogModel();
      assertArrayEquals(new String[] {"A"}, written.getDisabledParameters());
      assertArrayEquals(new String[] {"B"}, written.getEnabledParameters(),
         "B was never mentioned and was not part of the overlap -- it must stay enabled");
   }

   /** Supplying both lists explicitly, still overlapping, is a self-contradictory patch --
    *  refused rather than resolved one way or the other. */
   @Test
   void refusesAPatchThatExplicitlyPutsTheSameNameInBothLists() throws Exception {
      ViewsheetPropertyDialogService dialog = mock(ViewsheetPropertyDialogService.class);
      ViewsheetPropertyDialogModel model =
         modelWithKnownParameters(List.of("MinPopulation"), List.of());
      when(dialog.getViewsheetInfo(anyString(), any(Principal.class))).thenReturn(model);

      SheetPropertyService service = new SheetPropertyService(sessionsMock(), dialog);

      Exception thrown = assertThrows(Exception.class, () -> service.set("tok", principal(),
         Map.of("enabledParameters", List.of("MinPopulation"),
                "disabledParameters", List.of("MinPopulation")), ""));

      assertTrue(thrown.getMessage().contains("MinPopulation"));
      verify(dialog, never()).setViewsheetInfo(anyString(), any(), any(), any(), anyString(),
                                               any());
   }

   /** Supplying both lists explicitly with no overlap is exactly the dialog's own submission
    *  shape and must pass through unchanged. */
   @Test
   void bothListsSuppliedTogetherWithNoOverlapPassThroughUnchanged() throws Exception {
      ViewsheetPropertyDialogService dialog = mock(ViewsheetPropertyDialogService.class);
      ViewsheetPropertyDialogModel model =
         modelWithKnownParameters(List.of("A"), List.of("B"));
      when(dialog.getViewsheetInfo(anyString(), any(Principal.class))).thenReturn(model);

      SheetPropertyService service = new SheetPropertyService(sessionsMock(), dialog);

      service.set("tok", principal(), Map.of(
         "enabledParameters", List.of("B"), "disabledParameters", List.of("A")), "");

      ArgumentCaptor<ViewsheetPropertyDialogModel> captor =
         ArgumentCaptor.forClass(ViewsheetPropertyDialogModel.class);
      verify(dialog).setViewsheetInfo(eq("rt1"), captor.capture(), any(Principal.class), any(),
                                      anyString(), any());
      ViewsheetParametersDialogModel written =
         captor.getValue().vsOptionsPane().getViewsheetParametersDialogModel();
      assertArrayEquals(new String[] {"B"}, written.getEnabledParameters());
      assertArrayEquals(new String[] {"A"}, written.getDisabledParameters());
   }

   /**
    * Redmine #76739 follow-up, found live: setViewsheetParameterInfo stores whatever the model
    * contains unconditionally, but the NEXT read re-derives these two arrays by filtering against
    * the viewsheet's actual query-declared variables -- so a name outside that set reports
    * ok:true and then silently vanishes on the very next get_viewsheet_properties. Confirmed live
    * 2026-09-16 against Examples/Census: setting enabledParameters:["Region"] (a selection-list
    * assembly name, not a real query parameter -- this viewsheet's query declares none) returned
    * ok:true, and the immediate readback showed an empty array. Refused here instead.
    */
   @Test
   void refusesAnEnabledParameterNameTheQueryDoesNotDeclare() throws Exception {
      ViewsheetPropertyDialogService dialog = mock(ViewsheetPropertyDialogService.class);
      ViewsheetPropertyDialogModel model = modelWithKnownParameters(List.of("Region"), List.of());
      when(dialog.getViewsheetInfo(anyString(), any(Principal.class))).thenReturn(model);

      SheetPropertyService service = new SheetPropertyService(sessionsMock(), dialog);

      Exception thrown = assertThrows(Exception.class, () -> service.set(
         "tok", principal(), Map.of("enabledParameters", List.of("State")), ""));

      assertTrue(thrown.getMessage().contains("State"));
      assertTrue(thrown.getMessage().contains("Region"));
      verify(dialog, never()).setViewsheetInfo(anyString(), any(), any(), any(), anyString(),
                                               any());
   }

   /** Same refusal, reached through disabledParameters instead of enabledParameters -- both
    *  resolved paths must be checked, not just one. */
   @Test
   void refusesADisabledParameterNameTheQueryDoesNotDeclare() throws Exception {
      ViewsheetPropertyDialogService dialog = mock(ViewsheetPropertyDialogService.class);
      ViewsheetPropertyDialogModel model = modelWithKnownParameters(List.of("Region"), List.of());
      when(dialog.getViewsheetInfo(anyString(), any(Principal.class))).thenReturn(model);

      SheetPropertyService service = new SheetPropertyService(sessionsMock(), dialog);

      assertThrows(Exception.class, () -> service.set(
         "tok", principal(), Map.of("disabledParameters", List.of("NotAParameter")), ""));
      verify(dialog, never()).setViewsheetInfo(anyString(), any(), any(), any(), anyString(),
                                               any());
   }

   /** A viewsheet whose query declares no variables at all gets a clearer message than an empty
    *  "Known parameters: []" -- there is nothing to enable or disable, not merely a typo. */
   @Test
   void refusesWithADedicatedMessageWhenTheQueryHasNoParametersAtAll() throws Exception {
      ViewsheetPropertyDialogService dialog = mock(ViewsheetPropertyDialogService.class);
      ViewsheetPropertyDialogModel model = modelWithKnownParameters(List.of(), List.of());
      when(dialog.getViewsheetInfo(anyString(), any(Principal.class))).thenReturn(model);

      SheetPropertyService service = new SheetPropertyService(sessionsMock(), dialog);

      Exception thrown = assertThrows(Exception.class, () -> service.set(
         "tok", principal(), Map.of("enabledParameters", List.of("Region")), ""));

      assertTrue(thrown.getMessage().contains("declares no variables"));
   }

   /** A patch that leaves both parameter lists alone is unaffected by this validation, even when
    *  the viewsheet has no query parameters at all. */
   @Test
   void parameterValidationDoesNotBlockAnUnrelatedPatch() throws Exception {
      ViewsheetPropertyDialogService dialog = mock(ViewsheetPropertyDialogService.class);
      ViewsheetPropertyDialogModel model = modelWithKnownParameters(List.of(), List.of());
      when(dialog.getViewsheetInfo(anyString(), any(Principal.class))).thenReturn(model);

      SheetPropertyService service = new SheetPropertyService(sessionsMock(), dialog);

      service.set("tok", principal(), Map.of("desc", "new description"), "");

      verify(dialog).setViewsheetInfo(eq("rt1"), any(), any(Principal.class), any(),
                                      anyString(), any());
   }

   // ── setDataSource (Redmine #76739) ──────────────────────────────────────────

   @Test
   void setDataSourceRebindsTheViewsheetThroughOneCheckpoint() throws Exception {
      ViewsheetPropertyDialogService dialog = mock(ViewsheetPropertyDialogService.class);
      ViewsheetPropertyDialogModel model = modelWith(20, "old");
      when(dialog.getViewsheetInfo(anyString(), any(Principal.class))).thenReturn(model);
      ViewsheetSessionService sessions = sessionsMock();

      SheetPropertyService service = new SheetPropertyService(sessions, dialog);
      AssetEntry entry = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET,
                                        "Sample Queries/customers", null);

      service.setDataSource("tok", principal(), entry, "");

      ArgumentCaptor<ViewsheetPropertyDialogModel> captor =
         ArgumentCaptor.forClass(ViewsheetPropertyDialogModel.class);
      verify(dialog).setViewsheetInfo(eq("rt1"), captor.capture(), any(Principal.class), any(),
                                      anyString(), any());
      assertSame(entry,
         captor.getValue().vsOptionsPane().getSelectDataSourceDialogModel().getDataSource());
      verify(sessions, times(1)).mutate(anyString(), any(Principal.class), any());
   }

   /** A null entry is how the dialog's "Clear" button is expressed -- it must reach
    *  setViewsheetInfo as null, not be skipped. */
   @Test
   void setDataSourceWithNullEntryClearsTheBinding() throws Exception {
      ViewsheetPropertyDialogService dialog = mock(ViewsheetPropertyDialogService.class);
      SelectDataSourceDialogModel dsModel = new SelectDataSourceDialogModel();
      dsModel.setDataSource(new AssetEntry(AssetRepository.GLOBAL_SCOPE,
         AssetEntry.Type.WORKSHEET, "Old Source", null));
      VSOptionsPaneModel options = new VSOptionsPaneModel();
      options.setSelectDataSourceDialogModel(dsModel);
      ViewsheetPropertyDialogModel model =
         ViewsheetPropertyDialogModel.builder().vsOptionsPane(options).build();
      when(dialog.getViewsheetInfo(anyString(), any(Principal.class))).thenReturn(model);

      SheetPropertyService service = new SheetPropertyService(sessionsMock(), dialog);

      service.setDataSource("tok", principal(), null, "");

      ArgumentCaptor<ViewsheetPropertyDialogModel> captor =
         ArgumentCaptor.forClass(ViewsheetPropertyDialogModel.class);
      verify(dialog).setViewsheetInfo(eq("rt1"), captor.capture(), any(Principal.class), any(),
                                      anyString(), any());
      assertNull(
         captor.getValue().vsOptionsPane().getSelectDataSourceDialogModel().getDataSource());
   }

   // ── live base entry (Redmine #77077) ────────────────────────────────────────

   /** A raw write into the data source subtree is refused before anything is written, and the
    *  live base entry is left exactly as it was. */
   @Test
   void refusesARawDataSourcePathWriteAndLeavesTheLiveEntryUntouched() throws Exception {
      AssetEntry live = liveBaseEntry();
      ViewsheetPropertyDialogService dialog = mock(ViewsheetPropertyDialogService.class);
      when(dialog.getViewsheetInfo(anyString(), any(Principal.class)))
         .thenReturn(modelWithBase(live));
      SheetPropertyService service = new SheetPropertyService(sessionsMock(), dialog);

      for(String key : List.of("vsOptionsPane.selectDataSourceDialogModel.dataSource.path",
                               "vsOptionsPane.SelectDataSourceDialogModel.DataSource.path",
                               "vsOptionsPane.selectDataSourceDialogModel.dataSource.Alias"))
      {
         Exception thrown = assertThrows(IllegalArgumentException.class,
            () -> service.set("tok", principal(), Map.of(key, "sakila/staff"), ""), key);
         assertTrue(thrown.getMessage().contains("set_viewsheet_data_source"),
                    thrown.getMessage());
      }

      assertEquals("sakila/TABLE/public/rental", live.getPath());
      assertNull(live.getAlias());
      verify(dialog, never()).setViewsheetInfo(anyString(), any(), any(), any(), anyString(),
                                               any());
   }

   /** An ordinary patch must still hand setViewsheetInfo the SAME live entry: its rebind block
    *  is guarded by equals(getBaseEntry()), and anything else would trigger a rebind. */
   @Test
   void anOrdinaryPatchStillPassesTheLiveBaseEntryThrough() throws Exception {
      AssetEntry live = liveBaseEntry();
      ViewsheetPropertyDialogService dialog = mock(ViewsheetPropertyDialogService.class);
      when(dialog.getViewsheetInfo(anyString(), any(Principal.class)))
         .thenReturn(modelWithBase(live));
      SheetPropertyService service = new SheetPropertyService(sessionsMock(), dialog);

      service.set("tok", principal(), Map.of("desc", "new"), "");

      ArgumentCaptor<ViewsheetPropertyDialogModel> captor =
         ArgumentCaptor.forClass(ViewsheetPropertyDialogModel.class);
      verify(dialog).setViewsheetInfo(eq("rt1"), captor.capture(), any(Principal.class), any(),
                                      anyString(), any());
      assertSame(live,
         captor.getValue().vsOptionsPane().getSelectDataSourceDialogModel().getDataSource());
      assertEquals("new", captor.getValue().vsOptionsPane().getDesc());
   }

   /** Defense in depth: whatever spelling a future refusal list misses, a PropertyPath write that
    *  reaches the base entry lands on a detached copy, and reattaching refuses it -- the live
    *  entry is never mutated, so nothing half-applied survives. */
   @Test
   void guardRefusesAnyWriteThatReachedTheDetachedBaseEntry() {
      for(String leaf : List.of("path", "alias", "createdUsername", "orgID")) {
         AssetEntry live = liveBaseEntry();
         ViewsheetPropertyDialogModel model = modelWithBase(live);
         SheetPropertyService.BaseEntryGuard guard = SheetPropertyService.BaseEntryGuard.detach(model);
         PropertyPath.set(model, "vsOptionsPane.selectDataSourceDialogModel.dataSource." + leaf,
                          "hacked");

         Exception thrown = assertThrows(IllegalArgumentException.class,
                                         () -> guard.reattach(model), leaf);
         assertTrue(thrown.getMessage().contains("set_viewsheet_data_source"),
                    thrown.getMessage());
         assertEquals("sakila/TABLE/public/rental", live.getPath(), leaf);
         assertNull(live.getAlias(), leaf);
         assertNull(live.getCreatedUsername(), leaf);
      }
   }

   @Test
   void guardRefusesAReplacedOrClearedBaseEntry() {
      AssetEntry live = liveBaseEntry();
      ViewsheetPropertyDialogModel model = modelWithBase(live);
      SheetPropertyService.BaseEntryGuard guard = SheetPropertyService.BaseEntryGuard.detach(model);
      model.vsOptionsPane().getSelectDataSourceDialogModel().setDataSource(null);

      assertThrows(IllegalArgumentException.class, () -> guard.reattach(model));
   }

   @Test
   void guardReattachesTheLiveEntryWhenNothingTouchedIt() {
      AssetEntry live = liveBaseEntry();
      ViewsheetPropertyDialogModel model = modelWithBase(live);
      SheetPropertyService.BaseEntryGuard guard = SheetPropertyService.BaseEntryGuard.detach(model);

      assertNotSame(live, model.vsOptionsPane().getSelectDataSourceDialogModel().getDataSource());
      guard.reattach(model);
      assertSame(live, model.vsOptionsPane().getSelectDataSourceDialogModel().getDataSource());
   }

   @Test
   void guardToleratesAModelWithNoBaseEntry() {
      ViewsheetPropertyDialogModel model = modelWith(20, "old");
      SheetPropertyService.BaseEntryGuard guard = SheetPropertyService.BaseEntryGuard.detach(model);
      guard.reattach(model);
      assertNull(model.vsOptionsPane().getSelectDataSourceDialogModel().getDataSource());
   }

   private static AssetEntry liveBaseEntry() {
      AssetEntry entry = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
         AssetEntry.Type.PHYSICAL_TABLE, "sakila/TABLE/public/rental", null);
      entry.setProperty("source", "public.rental");
      return entry;
   }

   private static ViewsheetPropertyDialogModel modelWithBase(AssetEntry base) {
      SelectDataSourceDialogModel dataSource = new SelectDataSourceDialogModel();
      dataSource.setDataSource(base);
      VSOptionsPaneModel options = new VSOptionsPaneModel();
      options.setDesc("old");
      options.setSelectDataSourceDialogModel(dataSource);
      return ViewsheetPropertyDialogModel.builder().vsOptionsPane(options).build();
   }

   // ── convertDataSourceToWorksheet (Redmine #76739) ───────────────────────────

   @Test
   void convertDataSourceToWorksheetReturnsTheNewPathAndMvFlag() throws Exception {
      ViewsheetPropertyDialogService dialog = mock(ViewsheetPropertyDialogService.class);
      AssetEntry newEntry = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
         AssetEntry.Type.WORKSHEET, "MyViewsheet Worksheet", null);
      SelectDataSourceDialogModel dsModel = new SelectDataSourceDialogModel();
      dsModel.setDataSource(newEntry);
      when(dialog.convertLogicModelToWorksheet(eq("rt1"), any(Principal.class)))
         .thenReturn(new ConvertToWorksheetResponseModel(dsModel, true));

      SheetPropertyService service = new SheetPropertyService(sessionsMock(), dialog);

      Map<String, Object> result = service.convertDataSourceToWorksheet("tok", principal());

      assertEquals("MyViewsheet Worksheet", result.get("path"));
      assertEquals(true, result.get("hasMaterializedViews"));
   }

   @Test
   void convertDataSourceToWorksheetPropagatesTheServicesOwnRefusal() throws Exception {
      ViewsheetPropertyDialogService dialog = mock(ViewsheetPropertyDialogService.class);
      when(dialog.convertLogicModelToWorksheet(eq("rt1"), any(Principal.class)))
         .thenThrow(new Exception("Invalid data source type. Data source needs to be a logic model"));

      SheetPropertyService service = new SheetPropertyService(sessionsMock(), dialog);

      Exception thrown = assertThrows(Exception.class,
         () -> service.convertDataSourceToWorksheet("tok", principal()));
      assertTrue(thrown.getMessage().contains("logic model"));
   }

   // ── harness ───────────────────────────────────────────────────────────────

   private static SheetPropertyService serviceWith(ViewsheetPropertyDialogModel model)
      throws Exception
   {
      ViewsheetPropertyDialogService dialog = mock(ViewsheetPropertyDialogService.class);
      when(dialog.getViewsheetInfo(anyString(), any(Principal.class))).thenReturn(model);
      return new SheetPropertyService(sessionsMock(), dialog);
   }

   private static ViewsheetSessionService sessionsMock() {
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getID()).thenReturn("rt1");

      ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);

      try {
         when(sessions.resolve(anyString(), any(Principal.class))).thenReturn(rvs);
         doAnswer(invocation -> {
            ViewsheetSessionService.Mutation mutation = invocation.getArgument(2);
            mutation.run(rvs, "rt1", null);
            return null;
         }).when(sessions).mutate(anyString(), any(Principal.class), any());
      }
      catch(Exception e) {
         throw new IllegalStateException(e);
      }

      return sessions;
   }

   private static ViewsheetPropertyDialogModel modelWith(int maxRows, String desc) {
      VSOptionsPaneModel options = new VSOptionsPaneModel();
      options.setMaxRows(maxRows);
      options.setDesc(desc);
      return ViewsheetPropertyDialogModel.builder().vsOptionsPane(options).build();
   }

   /** A model whose viewsheetParametersDialogModel already lists {@code enabled}/{@code disabled}
    *  as known query parameters -- what a real getViewsheetInfo would report for a viewsheet
    *  whose query declares exactly these variables. */
   private static ViewsheetPropertyDialogModel modelWithKnownParameters(
      List<String> enabled, List<String> disabled)
   {
      inetsoft.web.composer.model.vs.ViewsheetParametersDialogModel parameters =
         new inetsoft.web.composer.model.vs.ViewsheetParametersDialogModel();
      parameters.setEnabledParameters(enabled.toArray(new String[0]));
      parameters.setDisabledParameters(disabled.toArray(new String[0]));
      VSOptionsPaneModel options = new VSOptionsPaneModel();
      options.setViewsheetParametersDialogModel(parameters);
      return ViewsheetPropertyDialogModel.builder().vsOptionsPane(options).build();
   }

   private static Principal principal() {
      return () -> "admin";
   }
}
