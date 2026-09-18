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
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.viewsheet.TableVSAssembly;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.TableVSAssemblyInfo;
import inetsoft.web.composer.model.vs.ColumnOptionDialogModel;
import inetsoft.web.composer.model.vs.TextEditorModel;
import inetsoft.web.viewsheet.service.VSInputService;
import inetsoft.web.wiz.dispatch.CapturingCommandDispatcher;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@code ColumnOptionService} is the wiz-agent bridge onto
 * {@code VSInputService.getColumnOptionDialogModel}/{@code setColumnOptionDialogModel} -- the
 * same native methods {@code ColumnOptionDialogController} calls for the Composer's own
 * column-header right-click "Column Options" dialog.
 */
@Tag("core")
class ColumnOptionServiceTest {
   // ── shared refusals ──────────────────────────────────────────────────────

   @Test
   void refusesAnUnknownAssembly() throws Exception {
      Harness h = harnessWith(null, columns("STATE", "REGION"));

      Exception e = assertThrows(IllegalArgumentException.class,
         () -> h.service.get("tok", principal(), "Nope", 0));

      assertTrue(e.getMessage().contains("Nope"), e.getMessage());
      verifyNoInteractions(h.inputs);
   }

   @Test
   void refusesANonTableAssembly() throws Exception {
      VSAssembly notATable = mock(VSAssembly.class);
      Harness h = harnessWith(notATable, columns("STATE"));

      Exception e = assertThrows(IllegalArgumentException.class,
         () -> h.service.get("tok", principal(), "Gauge1", 0));

      assertTrue(e.getMessage().contains("not a Table assembly"), e.getMessage());
      verifyNoInteractions(h.inputs);
   }

   // ── col resolution ────────────────────────────────────────────────────────

   @Test
   void resolvesColByVisibleIndex() throws Exception {
      Harness h = harnessWith(columns("STATE", "REGION"));

      h.service.get("tok", h.user, "Table1", 1);

      verify(h.inputs).getColumnOptionDialogModel("rt1", "Table1", 1, h.user);
   }

   @Test
   void resolvesColByColumnName() throws Exception {
      Harness h = harnessWith(columns("STATE", "REGION"));

      h.service.get("tok", h.user, "Table1", "REGION");

      verify(h.inputs).getColumnOptionDialogModel("rt1", "Table1", 1, h.user);
   }

   @Test
   void refusesAnUnknownColumnName() throws Exception {
      Harness h = harnessWith(columns("STATE", "REGION"));

      Exception e = assertThrows(IllegalArgumentException.class,
         () -> h.service.get("tok", principal(), "Table1", "NOPE"));

      assertTrue(e.getMessage().contains("NOPE"), e.getMessage());
      assertTrue(e.getMessage().contains("STATE"), e.getMessage());
      verifyNoInteractions(h.inputs);
   }

   @Test
   void refusesAnOutOfRangeIndex() throws Exception {
      Harness h = harnessWith(columns("STATE", "REGION"));

      Exception e = assertThrows(IllegalArgumentException.class,
         () -> h.service.get("tok", principal(), "Table1", 5));

      assertTrue(e.getMessage().contains("out of range"), e.getMessage());
      verifyNoInteractions(h.inputs);
   }

   /**
    * A numeric-looking string is treated as an index, not a name -- matches
    * {@code requireInBounds}'s digit-string branch rather than falling through to name lookup.
    */
   @Test
   void treatsADigitStringAsAnIndexNotAName() throws Exception {
      Harness h = harnessWith(columns("STATE", "REGION"));

      h.service.get("tok", h.user, "Table1", "1");

      verify(h.inputs).getColumnOptionDialogModel("rt1", "Table1", 1, h.user);
   }

   // ── set ───────────────────────────────────────────────────────────────────

   @Test
   void requiresInputControlWhenEnablingColumnEditing() throws Exception {
      Harness h = harnessWith(columns("STATE"));

      Exception e = assertThrows(IllegalArgumentException.class,
         () -> h.service.set("tok", principal(), "Table1", 0, true, null, null, ""));

      assertTrue(e.getMessage().contains("inputControl"), e.getMessage());
      verifyNoInteractions(h.inputs);
   }

   /** The bug's own repro: a Text editor with a pattern and error message. */
   @Test
   void roundTripsATextEditor() throws Exception {
      Harness h = harnessWith(columns("STATE", "REGION"));
      TextEditorModel editor = new TextEditorModel();
      editor.setPattern("^[A-Z]{2}$");
      editor.setErrorMessage("{0}");

      h.service.set("tok", h.user, "Table1", "STATE", true, "Text", editor, "");

      ArgumentCaptor<ColumnOptionDialogModel> captor =
         ArgumentCaptor.forClass(ColumnOptionDialogModel.class);
      verify(h.inputs).setColumnOptionDialogModel(eq("rt1"), eq("Table1"), eq(0),
         captor.capture(), eq(h.user), eq(h.dispatcher), eq(""));
      assertTrue(captor.getValue().isEnableColumnEditing());
      assertEquals("Text", captor.getValue().getInputControl());
      assertSame(editor, captor.getValue().getEditor());
   }

   @Test
   void disablingColumnEditingNeedsNoInputControlOrEditor() throws Exception {
      Harness h = harnessWith(columns("STATE"));

      h.service.set("tok", h.user, "Table1", 0, false, null, null, "");

      ArgumentCaptor<ColumnOptionDialogModel> captor =
         ArgumentCaptor.forClass(ColumnOptionDialogModel.class);
      verify(h.inputs).setColumnOptionDialogModel(eq("rt1"), eq("Table1"), eq(0),
         captor.capture(), eq(h.user), eq(h.dispatcher), eq(""));
      assertFalse(captor.getValue().isEnableColumnEditing());
   }

   // ── fixtures ──────────────────────────────────────────────────────────────

   private record Harness(ColumnOptionService service, VSInputService inputs,
                          CapturingCommandDispatcher dispatcher, Principal user) {}

   private static ColumnSelection columns(String... names) {
      ColumnSelection selection = new ColumnSelection();

      for(String name : names) {
         selection.addAttribute(new ColumnRef(new AttributeRef(null, name)));
      }

      return selection;
   }

   private static TableVSAssembly tableWith(ColumnSelection visible) {
      TableVSAssembly assembly = mock(TableVSAssembly.class);
      TableVSAssemblyInfo info = mock(TableVSAssemblyInfo.class);
      when(assembly.getVSAssemblyInfo()).thenReturn(info);
      when(info.getVisibleColumns()).thenReturn(visible);
      return assembly;
   }

   private static Harness harnessWith(ColumnSelection visible) throws Exception {
      return harnessWith(tableWith(visible), visible);
   }

   private static Harness harnessWith(VSAssembly assembly, ColumnSelection visible) throws Exception {
      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getAssembly(anyString())).thenReturn(assembly);

      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);

      VSInputService inputs = mock(VSInputService.class);
      CapturingCommandDispatcher dispatcher = mock(CapturingCommandDispatcher.class);
      ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);
      Principal user = principal();

      doAnswer(invocation -> {
         ViewsheetSessionService.Mutation mutation = invocation.getArgument(2);
         mutation.run(rvs, "rt1", dispatcher);
         return null;
      }).when(sessions).mutate(anyString(), any(Principal.class), any());

      doAnswer(invocation -> {
         ViewsheetSessionService.Read<?> read = invocation.getArgument(2);
         return read.run(rvs, "rt1", dispatcher);
      }).when(sessions).read(anyString(), any(Principal.class), any());

      return new Harness(new ColumnOptionService(sessions, inputs), inputs, dispatcher, user);
   }

   private static Principal principal() {
      return mock(Principal.class);
   }
}
