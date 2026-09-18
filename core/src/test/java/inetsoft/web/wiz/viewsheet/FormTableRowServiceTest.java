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
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.TableVSAssemblyInfo;
import inetsoft.web.viewsheet.command.LoadTableDataCommand;
import inetsoft.web.viewsheet.controller.table.VSFormTableService;
import inetsoft.web.viewsheet.event.table.*;
import inetsoft.web.viewsheet.model.table.BaseTableCellModel;
import inetsoft.web.wiz.dispatch.CapturingCommandDispatcher;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.security.Principal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@code FormTableRowService} is the wiz-agent bridge onto {@code VSFormTableService}'s
 * addRow/deleteRows/changeFormInput/applyChanges -- the same native methods
 * {@code VSFormTableController}'s STOMP endpoints call for a Form Table's Preview toolbar.
 */
@Tag("core")
class FormTableRowServiceTest {
   // ── shared refusals (requireForm) ────────────────────────────────────────

   @Test
   void refusesAnUnknownAssembly() {
      Harness h = harnessWith(null);

      Exception e = assertThrows(IllegalArgumentException.class,
         () -> h.service.insertRow("tok", principal(), "Nope", 0, false, ""));

      assertTrue(e.getMessage().contains("Nope"), e.getMessage());
      verifyNoInteractions(h.forms);
   }

   @Test
   void refusesANonTableAssembly() {
      Harness h = harnessWith(mock(ChartVSAssembly.class));

      Exception e = assertThrows(IllegalArgumentException.class,
         () -> h.service.insertRow("tok", principal(), "Chart1", 0, false, ""));

      assertTrue(e.getMessage().contains("not a Table assembly"), e.getMessage());
      verifyNoInteractions(h.forms);
   }

   @Test
   void refusesATableThatIsNotAFormTable() {
      Harness h = harnessWith(tableWith(false, false, false, false));

      Exception e = assertThrows(IllegalArgumentException.class,
         () -> h.service.insertRow("tok", principal(), "Table1", 0, false, ""));

      assertTrue(e.getMessage().contains("not a Form table"), e.getMessage());
      verifyNoInteractions(h.forms);
   }

   // ── insertRow ─────────────────────────────────────────────────────────────

   /**
    * The reason this class exists: {@code VSFormTableService.addRow} itself never checks
    * {@code isInsert()}, so a STOMP-bypassing caller could otherwise do what a hidden toolbar
    * button would have.
    */
   @Test
   void refusesInsertWhenInsertIsDisabled() {
      Harness h = harnessWith(tableWith(true, false, true, true));

      Exception e = assertThrows(IllegalArgumentException.class,
         () -> h.service.insertRow("tok", principal(), "Table1", 0, false, ""));

      assertTrue(e.getMessage().contains("Insert enabled"), e.getMessage());
      verifyNoInteractions(h.forms);
   }

   @Test
   void insertPassesInsertTrueWhenNotAppending() throws Exception {
      Harness h = harnessWith(tableWith(true, true, true, true));

      h.service.insertRow("tok", principal(), "Table1", 3, false, "");

      ArgumentCaptor<InsertTableRowEvent> captor = ArgumentCaptor.forClass(InsertTableRowEvent.class);
      verify(h.forms).addRow(eq("rt1"), captor.capture(), eq(""), any(), any());
      assertEquals("Table1", captor.getValue().getAssemblyName());
      assertTrue(captor.getValue().insert());
      assertEquals(3, captor.getValue().row());
   }

   @Test
   void appendPassesInsertFalse() throws Exception {
      Harness h = harnessWith(tableWith(true, true, true, true));

      h.service.insertRow("tok", principal(), "Table1", 3, true, "");

      ArgumentCaptor<InsertTableRowEvent> captor = ArgumentCaptor.forClass(InsertTableRowEvent.class);
      verify(h.forms).addRow(eq("rt1"), captor.capture(), eq(""), any(), any());
      assertFalse(captor.getValue().insert());
   }

   // ── deleteRows ────────────────────────────────────────────────────────────

   @Test
   void refusesDeleteWithNoRows() {
      Harness h = harnessWith(tableWith(true, true, true, true));

      Exception e = assertThrows(IllegalArgumentException.class,
         () -> h.service.deleteRows("tok", principal(), "Table1", List.of(), ""));

      assertTrue(e.getMessage().contains("rows"), e.getMessage());
      verifyNoInteractions(h.forms);
   }

   /** Same reasoning as insert: {@code deleteRows} itself never checks {@code isDel()}. */
   @Test
   void refusesDeleteWhenDeleteIsDisabled() {
      Harness h = harnessWith(tableWith(true, true, false, true));

      Exception e = assertThrows(IllegalArgumentException.class,
         () -> h.service.deleteRows("tok", principal(), "Table1", List.of(1), ""));

      assertTrue(e.getMessage().contains("Delete enabled"), e.getMessage());
      verifyNoInteractions(h.forms);
   }

   @Test
   void deleteRowsForwardsTheGivenIndices() throws Exception {
      Harness h = harnessWith(tableWith(true, true, true, true));

      h.service.deleteRows("tok", principal(), "Table1", List.of(2, 0), "");

      ArgumentCaptor<DeleteTableRowsEvent> captor = ArgumentCaptor.forClass(DeleteTableRowsEvent.class);
      verify(h.forms).deleteRows(eq("rt1"), captor.capture(), eq(""), any(), any());
      assertEquals(List.of(2, 0), captor.getValue().rows());
   }

   // ── setCell ───────────────────────────────────────────────────────────────

   /** setCell only requires isForm(), not isInsert()/isDel() -- editing a cell is neither. */
   @Test
   void setCellDoesNotRequireInsertOrDelete() throws Exception {
      Harness h = harnessWith(tableWith(true, false, false, true));

      h.service.setCell("tok", principal(), "Table1", 0, 1, "hello", "");

      ArgumentCaptor<ChangeFormTableCellInputEvent> captor =
         ArgumentCaptor.forClass(ChangeFormTableCellInputEvent.class);
      verify(h.forms).changeFormInput(eq("rt1"), captor.capture(), eq(""), any(), any());
      assertEquals(0, captor.getValue().row());
      assertEquals(1, captor.getValue().col());
      assertEquals("hello", captor.getValue().data());
   }

   // ── apply ─────────────────────────────────────────────────────────────────

   /**
    * {@code writeBackFormData} silently no-ops (rather than throwing) when write-back is
    * disabled -- so this must be a named refusal here, not left to the native method.
    */
   @Test
   void refusesApplyWhenWriteBackIsDisabled() {
      Harness h = harnessWith(tableWith(true, true, true, false));

      Exception e = assertThrows(IllegalArgumentException.class,
         () -> h.service.apply("tok", principal(), "Table1", ""));

      assertTrue(e.getMessage().contains("write-back enabled"), e.getMessage());
      verifyNoInteractions(h.forms);
   }

   /** writeBackFormData's own RuntimeException must surface named, not as a generic failure. */
   @Test
   void surfacesTheNativeApplyFailureMessage() throws Exception {
      Harness h = harnessWith(tableWith(true, true, true, true));
      doThrow(new RuntimeException("The binding source of Table1 is not an embedded table!"))
         .when(h.forms).applyChanges(eq("rt1"), any(), eq(""), any(), any());

      Exception e = assertThrows(IllegalArgumentException.class,
         () -> h.service.apply("tok", principal(), "Table1", ""));

      assertTrue(e.getMessage().contains("not an embedded table"), e.getMessage());
   }

   @Test
   void reportsAppliedOnSuccess() throws Exception {
      Harness h = harnessWith(tableWith(true, true, true, true));

      Map<String, Object> result = h.service.apply("tok", principal(), "Table1", "");

      assertEquals(true, result.get("applied"));
      assertEquals("Table1", result.get("assembly"));
   }

   // ── snapshot round-trip ───────────────────────────────────────────────────

   /**
    * Confirms the split-verb shape actually gives the caller a real inspection point: the
    * {@code LoadTableDataCommand} every {@code VSFormTableService} method dispatches is captured
    * and surfaced as row data, not discarded.
    */
   @Test
   void surfacesRowDataFromTheCapturedLoadCommand() throws Exception {
      Harness h = harnessWith(tableWith(true, true, true, true));

      BaseTableCellModel cellA = mock(BaseTableCellModel.class);
      when(cellA.getCellData()).thenReturn("A");
      BaseTableCellModel cellB = mock(BaseTableCellModel.class);
      when(cellB.getCellData()).thenReturn(42);

      LoadTableDataCommand load = mock(LoadTableDataCommand.class);
      when(load.runtimeDataRowCount()).thenReturn(1);
      when(load.tableCells()).thenReturn(new BaseTableCellModel[][]{ { cellA, cellB } });

      CapturingCommandDispatcher.Command captured =
         new CapturingCommandDispatcher.Command("Table1", "LoadTableDataCommand", load);
      when(h.dispatcher.getCapturedCommands()).thenReturn(List.of(captured));

      Map<String, Object> result = h.service.insertRow("tok", principal(), "Table1", 0, false, "");

      assertEquals(1, result.get("rowCount"));
      assertEquals(List.of(List.of("A", 42)), result.get("rows"));
   }

   @Test
   void reportsNoRowsWhenNothingWasCaptured() throws Exception {
      Harness h = harnessWith(tableWith(true, true, true, true));

      Map<String, Object> result = h.service.insertRow("tok", principal(), "Table1", 0, false, "");

      assertNull(result.get("rowCount"));
      assertEquals(List.of(), result.get("rows"));
   }

   // ── fixtures ──────────────────────────────────────────────────────────────

   private record Harness(FormTableRowService service, VSFormTableService forms,
                          CapturingCommandDispatcher dispatcher) {}

   private static TableVSAssembly tableWith(boolean form, boolean insert, boolean del,
                                            boolean writeBack)
   {
      TableVSAssembly assembly = mock(TableVSAssembly.class);
      TableVSAssemblyInfo info = mock(TableVSAssemblyInfo.class);
      when(assembly.getVSAssemblyInfo()).thenReturn(info);
      when(info.isForm()).thenReturn(form);
      when(info.isInsert()).thenReturn(insert);
      when(info.isDel()).thenReturn(del);
      when(info.isWriteBack()).thenReturn(writeBack);
      return assembly;
   }

   private static Harness harnessWith(VSAssembly assembly) {
      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getAssembly(anyString())).thenReturn(assembly);

      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);

      VSFormTableService forms = mock(VSFormTableService.class);
      CapturingCommandDispatcher dispatcher = mock(CapturingCommandDispatcher.class);
      ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);

      try {
         doAnswer(invocation -> {
            ViewsheetSessionService.Mutation mutation = invocation.getArgument(2);
            mutation.run(rvs, "rt1", dispatcher);
            return null;
         }).when(sessions).mutate(anyString(), any(Principal.class), any());
      }
      catch(Exception e) {
         throw new IllegalStateException(e);
      }

      return new Harness(new FormTableRowService(sessions, forms), forms, dispatcher);
   }

   private static Principal principal() {
      return mock(Principal.class);
   }
}
