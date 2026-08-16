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
    * {@code width} lives directly on the immutable {@code ViewsheetPropertyDialogModel} root,
    * with only a wither and no nested pane to absorb the rebuild. If the service kept using the
    * model object it originally read instead of the (possibly rebuilt) one {@code PropertyPath}
    * hands back, this write would silently vanish -- the exact defect this whole layer exists
    * to avoid.
    */
   @Test
   void setRebuildsTheRootWhenTheWrittenFieldIsATopLevelImmutableScalar() throws Exception {
      ViewsheetPropertyDialogService dialog = mock(ViewsheetPropertyDialogService.class);
      ViewsheetPropertyDialogModel model = modelWith(20, "old");
      when(dialog.getViewsheetInfo(anyString(), any(Principal.class))).thenReturn(model);

      SheetPropertyService service = new SheetPropertyService(sessionsMock(), dialog);

      service.set("tok", principal(), Map.of("width", 800), "");

      ArgumentCaptor<ViewsheetPropertyDialogModel> captor =
         ArgumentCaptor.forClass(ViewsheetPropertyDialogModel.class);
      verify(dialog).setViewsheetInfo(eq("rt1"), captor.capture(), any(Principal.class), any(),
                                      anyString(), any());
      assertEquals(800, captor.getValue().width());
   }

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

   private static Principal principal() {
      return () -> "admin";
   }
}
