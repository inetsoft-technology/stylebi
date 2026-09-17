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
