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

import inetsoft.report.composition.FormTableLens;
import inetsoft.uql.ColumnSelection;
import inetsoft.report.composition.FormTableRow;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
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
import java.util.Optional;

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

   /**
    * Bug #77042 Problem 1: {@code FormTableLens.insertRow}/{@code appendRow} index from row 0 =
    * the header row, but this class's own contract is a 0-based DATA row -- so index 0 (first
    * data row) must become the lens's {@code getHeaderRowCount()} (1 here), not 0.
    */
   @Test
   void insertRowOffsetsIndexByTheLensOwnHeaderRowCount() throws Exception {
      Harness h = harnessWithLens(tableWith(true, true, true, true), lensWith(1, true, rowWith(FormTableRow.OLD)));

      h.service.insertRow("tok", principal(), "Table1", 0, false, "");

      ArgumentCaptor<InsertTableRowEvent> captor = ArgumentCaptor.forClass(InsertTableRowEvent.class);
      verify(h.forms).addRow(eq("rt1"), captor.capture(), eq(""), any(), any());
      assertEquals(1, captor.getValue().row());
   }

   /** Not hardcoded to 1 -- a lens reporting more header rows offsets by that many instead. */
   @Test
   void insertRowOffsetUsesTheLensOwnHeaderRowCountNotAConstant() throws Exception {
      Harness h = harnessWithLens(tableWith(true, true, true, true), lensWith(3, true, rowWith(0), rowWith(0), rowWith(0), rowWith(0), rowWith(0)));

      h.service.insertRow("tok", principal(), "Table1", 2, false, "");

      ArgumentCaptor<InsertTableRowEvent> captor = ArgumentCaptor.forClass(InsertTableRowEvent.class);
      verify(h.forms).addRow(eq("rt1"), captor.capture(), eq(""), any(), any());
      assertEquals(5, captor.getValue().row());
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

   /**
    * Bug #77042 Problem 1: {@code FormTableLens.deleteRow(0)} has NO header guard at all (unlike
    * {@code insertRow}) -- it silently deletes the header -- so every data index must be offset
    * by the header row count before reaching the native event, same as insert.
    */
   @Test
   void deleteRowsOffsetsEveryIndexByTheLensOwnHeaderRowCount() throws Exception {
      Harness h = harnessWithLens(tableWith(true, true, true, true),
         lensWith(1, true, rowWith(0), rowWith(0), rowWith(0), rowWith(0)));

      h.service.deleteRows("tok", principal(), "Table1", List.of(2, 0), "");

      ArgumentCaptor<DeleteTableRowsEvent> captor = ArgumentCaptor.forClass(DeleteTableRowsEvent.class);
      verify(h.forms).deleteRows(eq("rt1"), captor.capture(), eq(""), any(), any());
      assertEquals(List.of(3, 1), captor.getValue().rows());
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

   /** Bug #77042 Problem 1: same header-row offset as insert/delete, applied to setCell's row. */
   @Test
   void setCellOffsetsRowByTheLensOwnHeaderRowCount() throws Exception {
      FormTableLens lens = lensWith(1, true, rowWith(FormTableRow.OLD), rowWith(FormTableRow.OLD));
      ColumnOption option = optionWith(true);
      when(lens.getVisibleColumnOption(1)).thenReturn(option);
      Harness h = harnessWithLens(tableWith(true, true, true, true), lens);

      h.service.setCell("tok", principal(), "Table1", 0, 1, "hello", "");

      ArgumentCaptor<ChangeFormTableCellInputEvent> captor =
         ArgumentCaptor.forClass(ChangeFormTableCellInputEvent.class);
      verify(h.forms).changeFormInput(eq("rt1"), captor.capture(), eq(""), any(), any());
      assertEquals(1, captor.getValue().row());
   }

   /**
    * Bug #77042 Problem 3: the native {@code changeFormInput} enforces no editable-state check at
    * all -- this class must refuse a pre-existing (not {@code FormTableRow.ADDED}) row's cell
    * when the table's Edit switch is off, mirroring {@code BaseTableCellModel.createFormCell}'s
    * own display-time condition exactly.
    */
   @Test
   void setCellRefusesAPreExistingRowsCellWhenEditIsOff() {
      // index 0 is the header placeholder (never touched, nativeRow is 1); index 1 is the row
      // data index 0 resolves to once offset by headerRowCount.
      FormTableLens lens = lensWith(1, false, rowWith(FormTableRow.OLD), rowWith(FormTableRow.OLD));
      ColumnOption option = optionWith(true);
      when(lens.getVisibleColumnOption(1)).thenReturn(option);
      Harness h = harnessWithLens(tableWith(true, true, true, true), lens);

      Exception e = assertThrows(IllegalArgumentException.class,
         () -> h.service.setCell("tok", principal(), "Table1", 0, 1, "hello", ""));

      assertTrue(e.getMessage().contains("not editable"), e.getMessage());
      verifyNoInteractions(h.forms);
   }

   /**
    * The other half of the same condition: a column that is not form-optioned at all is refused
    * regardless of row state or Edit switch.
    */
   @Test
   void setCellRefusesAColumnThatIsNotFormOptioned() {
      FormTableLens lens = lensWith(1, true, rowWith(FormTableRow.ADDED), rowWith(FormTableRow.ADDED));
      ColumnOption option = optionWith(false);
      when(lens.getVisibleColumnOption(1)).thenReturn(option);
      Harness h = harnessWithLens(tableWith(true, true, true, true), lens);

      Exception e = assertThrows(IllegalArgumentException.class,
         () -> h.service.setCell("tok", principal(), "Table1", 0, 1, "hello", ""));

      assertTrue(e.getMessage().contains("not editable"), e.getMessage());
      verifyNoInteractions(h.forms);
   }

   /**
    * A row just added via {@code form_table_insert_row} ({@code FormTableRow.ADDED}) is editable
    * on its form-optioned columns even while the table's Edit switch is off -- exactly the
    * "new row" half of {@code BaseTableCellModel}'s own condition.
    */
   @Test
   void setCellAllowsANewlyAddedRowsCellEvenWhenEditIsOff() throws Exception {
      FormTableLens lens = lensWith(1, false, rowWith(FormTableRow.ADDED), rowWith(FormTableRow.ADDED));
      ColumnOption option = optionWith(true);
      when(lens.getVisibleColumnOption(1)).thenReturn(option);
      Harness h = harnessWithLens(tableWith(true, true, true, true), lens);

      h.service.setCell("tok", principal(), "Table1", 0, 1, "hello", "");

      verify(h.forms).changeFormInput(eq("rt1"), any(), eq(""), any(), any());
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
      when(load.headerRowCount()).thenReturn(0);
      when(load.start()).thenReturn(0);
      when(load.tableCells()).thenReturn(new BaseTableCellModel[][]{ { cellA, cellB } });

      CapturingCommandDispatcher.Command captured =
         new CapturingCommandDispatcher.Command("Table1", "LoadTableDataCommand", load);
      when(h.dispatcher.getCapturedCommands()).thenReturn(List.of(captured));

      Map<String, Object> result = h.service.insertRow("tok", principal(), "Table1", 0, false, "");

      assertEquals(1, result.get("rowCount"));
      assertEquals(List.of(List.of("A", 42)), result.get("rows"));
      assertEquals(List.of(), result.get("columns"));
   }

   /**
    * Bug #78067: {@code LoadTableDataCommand.tableCells} starts at absolute lens row
    * {@code start} (0), i.e. at the header row, while every input index is a 0-based data row.
    * {@code rows} must drop the header rows so {@code rows[k]} is data row {@code k}, and the
    * header is surfaced as {@code columns} instead. {@code rowCount} is passed through unchanged.
    */
   @Test
   void snapshotRowsSkipTheHeaderRowAndExposeItAsColumns() throws Exception {
      Harness h = harnessWith(tableWith(true, true, true, true));

      LoadTableDataCommand load = mock(LoadTableDataCommand.class);
      when(load.runtimeDataRowCount()).thenReturn(3);
      when(load.headerRowCount()).thenReturn(1);
      when(load.start()).thenReturn(0);
      // built before stubbing tableCells(): cell() stubs its own mock
      BaseTableCellModel[][] cells = {
         { cell("ID"), cell("NAME") },
         { cell(1), cell("Alice") },
         { cell(2), cell("Bobby") },
         { cell(3), cell("Carol") }
      };
      when(load.tableCells()).thenReturn(cells);

      CapturingCommandDispatcher.Command captured =
         new CapturingCommandDispatcher.Command("Table1", "LoadTableDataCommand", load);
      when(h.dispatcher.getCapturedCommands()).thenReturn(List.of(captured));

      Map<String, Object> result = h.service.setCell("tok", principal(), "Table1", 1, 1, "Bobby", "");

      assertEquals(3, result.get("rowCount"));
      assertEquals(List.of("ID", "NAME"), result.get("columns"));
      assertEquals(List.of(List.of(1, "Alice"), List.of(2, "Bobby"), List.of(3, "Carol")),
                   result.get("rows"));
      assertEquals(List.of(2, "Bobby"), ((List<?>) result.get("rows")).get(1));
   }

   private static BaseTableCellModel cell(Object data) {
      BaseTableCellModel cell = mock(BaseTableCellModel.class);
      when(cell.getCellData()).thenReturn(data);
      return cell;
   }

   @Test
   void reportsNoRowsWhenNothingWasCaptured() throws Exception {
      Harness h = harnessWith(tableWith(true, true, true, true));

      Map<String, Object> result = h.service.insertRow("tok", principal(), "Table1", 0, false, "");

      assertNull(result.get("rowCount"));
      assertEquals(List.of(), result.get("columns"));
      assertEquals(List.of(), result.get("rows"));
   }

   // ── warnings (Bug #77042 Problem 2) ─────────────────────────────────────

   /**
    * {@code ViewsheetSessionService.mutate} already extracts any {@code MessageCommand} WARNING
    * the mutation dispatched (via {@code CapturingCommandDispatcher.getWarnings()}) and returns
    * it -- e.g. the max-row-count insert warning ({@code VSFormTableService.addRow}) or the
    * oversized-cell truncation warning ({@code changeFormInput}). Before this fix,
    * {@code insertRow}/{@code deleteRows}/{@code setCell} called {@code mutate} as a bare
    * statement and threw that return value away, so the tool reported an unconditional success
    * with no way to tell the write was altered or skipped. (An ERROR-type MessageCommand -- e.g.
    * {@code option.validate()} rejection -- is a different case already handled: {@code mutate}
    * converts it to a thrown {@code CommandErrorException}, which a dedicated
    * {@code WizControllerErrorHandler} handler already maps to a named HTTP 409, so it was never
    * silently dropped the way a WARNING was.)
    */
   @Test
   void insertRowSurfacesWarningsMutateReturned() throws Exception {
      Harness h = harnessWithWarnings(tableWith(true, true, true, true),
                                      List.of("Reached the maximum number of rows allowed."));

      Map<String, Object> result = h.service.insertRow("tok", principal(), "Table1", 0, false, "");

      assertEquals(List.of("Reached the maximum number of rows allowed."), result.get("warnings"));
   }

   // ── refused insert at max.row.count (Bug #78211) ────────────────────────

   /** A lens whose header-inclusive row count only grows once {@code addRow} was invoked. */
   private static FormTableLens growingLens(int headerRows, int rowsBefore, boolean grows,
                                            java.util.concurrent.atomic.AtomicBoolean added)
   {
      FormTableLens lens = mock(FormTableLens.class);
      when(lens.getHeaderRowCount()).thenReturn(headerRows);
      when(lens.getRowCount()).thenAnswer(
         inv -> rowsBefore + (grows && added.get() ? 1 : 0));
      return lens;
   }

   private static Harness insertHarness(FormTableLens lens,
                                        java.util.concurrent.atomic.AtomicBoolean added,
                                        List<String> warnings) throws Exception
   {
      Harness h = harnessWithLens(tableWith(true, true, true, true), lens, warnings);
      doAnswer(inv -> {
         added.set(true);
         return null;
      }).when(h.forms).addRow(anyString(), any(), any(), any(), any());
      return h;
   }

   @Test
   void insertRowRefusedAtTheOrgLimitNamesTheLimitAndDataRowCapacity() throws Exception {
      var added = new java.util.concurrent.atomic.AtomicBoolean();
      // header + 2 data rows = 3 native rows, max.row.count 3, one header row => capacity 2
      Harness h = insertHarness(growingLens(1, 3, false, added), added, List.of());

      try(var util = mockStatic(inetsoft.report.internal.Util.class)) {
         util.when(inetsoft.report.internal.Util::getOrganizationMaxRow).thenReturn(3);
         IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> h.service.insertRow("tok", principal(), "Table1", 0, false, ""));
         assertTrue(e.getMessage().contains("max.row.count = 3"), e.getMessage());
         assertTrue(e.getMessage().contains("at most 2 data rows"), e.getMessage());
         assertTrue(e.getMessage().contains("already has 2"), e.getMessage());
      }
   }

   @Test
   void appendRowRefusedAtTheOrgLimitIsAlsoRejected() throws Exception {
      var added = new java.util.concurrent.atomic.AtomicBoolean();
      Harness h = insertHarness(growingLens(1, 3, false, added), added, List.of());

      try(var util = mockStatic(inetsoft.report.internal.Util.class)) {
         util.when(inetsoft.report.internal.Util::getOrganizationMaxRow).thenReturn(3);
         IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> h.service.insertRow("tok", principal(), "Table1", 0, true, ""));
         assertTrue(e.getMessage().contains("max.row.count = 3"), e.getMessage());
      }
   }

   @Test
   void noOpInsertWithUnlimitedRowCountGivesGenericMessageAndCapturedWarning() throws Exception {
      var added = new java.util.concurrent.atomic.AtomicBoolean();
      Harness h = insertHarness(growingLens(1, 3, false, added), added, List.of());
      when(h.dispatcher.getWarnings()).thenReturn(List.of("Something else."));

      try(var util = mockStatic(inetsoft.report.internal.Util.class)) {
         util.when(inetsoft.report.internal.Util::getOrganizationMaxRow).thenReturn(0);
         IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> h.service.insertRow("tok", principal(), "Table1", 0, false, ""));
         assertTrue(e.getMessage().startsWith("No row was inserted into 'Table1'."), e.getMessage());
         assertFalse(e.getMessage().contains("max.row.count"), e.getMessage());
         assertTrue(e.getMessage().contains("Something else."), e.getMessage());
      }
   }

   @Test
   void successfulInsertStillReturnsOkAndPassesWarningsThrough() throws Exception {
      var added = new java.util.concurrent.atomic.AtomicBoolean();
      Harness h = insertHarness(growingLens(1, 3, true, added), added, List.of("Heads up."));

      Map<String, Object> result = h.service.insertRow("tok", principal(), "Table1", 0, false, "");

      assertEquals(List.of("Heads up."), result.get("warnings"));
      assertEquals("Table1", result.get("assembly"));
   }

   @Test
   void setCellSurfacesWarningsMutateReturned() throws Exception {
      Harness h = harnessWithWarnings(tableWith(true, false, false, true),
                                      List.of("The value was truncated."));

      Map<String, Object> result =
         h.service.setCell("tok", principal(), "Table1", 0, 1, "a very long value", "");

      assertEquals(List.of("The value was truncated."), result.get("warnings"));
   }

   @Test
   void deleteRowsSurfacesWarningsMutateReturned() throws Exception {
      Harness h = harnessWithWarnings(tableWith(true, true, true, true), List.of("Some warning."));

      Map<String, Object> result = h.service.deleteRows("tok", principal(), "Table1", List.of(0), "");

      assertEquals(List.of("Some warning."), result.get("warnings"));
   }

   /** No `warnings` key at all when nothing warned -- not an empty list sitting in the response. */
   @Test
   void insertRowOmitsTheWarningsKeyWhenNothingWarned() throws Exception {
      Harness h = harnessWith(tableWith(true, true, true, true));

      Map<String, Object> result = h.service.insertRow("tok", principal(), "Table1", 0, false, "");

      assertFalse(result.containsKey("warnings"), result.toString());
   }

   // ── range validation (Bug #78152) ─────────────────────────────────────────

   /** header + 3 data rows, 2 visible columns. */
   private static Harness threeRowHarness() {
      return harnessWithLens(tableWith(true, true, true, true),
         lensWith(1, true, rowWith(FormTableRow.OLD), rowWith(FormTableRow.OLD),
                  rowWith(FormTableRow.OLD), rowWith(FormTableRow.OLD)));
   }

   private void assertRefused(Harness h, org.junit.jupiter.api.function.Executable call,
                              String... fragments)
   {
      Exception e = assertThrows(IllegalArgumentException.class, call);

      for(String f : fragments) {
         assertTrue(e.getMessage().contains(f), e.getMessage());
      }

      verifyNoInteractions(h.forms);
   }

   @Test
   void insertRefusesIndexPastTheEnd() {
      Harness h = threeRowHarness();
      assertRefused(h, () -> h.service.insertRow("tok", principal(), "Table1", 99, false, ""),
                    "'index' 99", "0..3");
      assertRefused(h, () -> h.service.insertRow("tok", principal(), "Table1", 4, false, ""),
                    "0..3");
      assertRefused(h, () -> h.service.insertRow("tok", principal(), "Table1", -1, false, ""),
                    "0..3");
   }

   @Test
   void insertAtDataRowCountIsAllowed() throws Exception {
      Harness h = threeRowHarness();
      h.service.insertRow("tok", principal(), "Table1", 3, false, "");
      verify(h.forms).addRow(eq("rt1"), any(), eq(""), any(), any());
   }

   @Test
   void appendRefusesIndexAtOrPastDataRowCount() {
      Harness h = threeRowHarness();
      assertRefused(h, () -> h.service.insertRow("tok", principal(), "Table1", 3, true, ""),
                    "append", "0..2");
      assertRefused(h, () -> h.service.insertRow("tok", principal(), "Table1", 99, true, ""),
                    "0..2");
   }

   @Test
   void appendAfterLastRowIsAllowed() throws Exception {
      Harness h = threeRowHarness();
      h.service.insertRow("tok", principal(), "Table1", 2, true, "");
      verify(h.forms).addRow(eq("rt1"), any(), eq(""), any(), any());
   }

   @Test
   void appendOnEmptyTablePointsAtInsert() {
      Harness h = harnessWithLens(tableWith(true, true, true, true),
         lensWith(1, true, rowWith(FormTableRow.OLD)));
      assertRefused(h, () -> h.service.insertRow("tok", principal(), "Table1", 0, true, ""),
                    "no data rows", "insert");
   }

   @Test
   void insertAtZeroOnEmptyTableIsAllowed() throws Exception {
      Harness h = harnessWithLens(tableWith(true, true, true, true),
         lensWith(1, true, rowWith(FormTableRow.OLD)));
      h.service.insertRow("tok", principal(), "Table1", 0, false, "");
      verify(h.forms).addRow(eq("rt1"), any(), eq(""), any(), any());
   }

   @Test
   void insertAtZeroRefusedWhenTableHasNoHeaderRow() {
      Harness h = harnessWithLens(tableWith(true, true, true, true),
         lensWith(0, true, rowWith(FormTableRow.OLD), rowWith(FormTableRow.OLD)));
      assertRefused(h, () -> h.service.insertRow("tok", principal(), "Table1", 0, false, ""),
                    "no header row");
   }

   @Test
   void deleteRefusesOutOfRangeRows() {
      Harness h = threeRowHarness();
      assertRefused(h, () -> h.service.deleteRows("tok", principal(), "Table1", List.of(99), ""),
                    "99", "0..2");
      assertRefused(h, () -> h.service.deleteRows("tok", principal(), "Table1", List.of(3), ""),
                    "0..2");
      assertRefused(h, () -> h.service.deleteRows("tok", principal(), "Table1", List.of(0, -1), ""),
                    "-1");
   }

   @Test
   void deleteRefusesDuplicateRows() {
      Harness h = threeRowHarness();
      assertRefused(h, () -> h.service.deleteRows("tok", principal(), "Table1", List.of(1, 1), ""),
                    "row 1", "more than once");
   }

   @Test
   void deleteLastRowIsAllowed() throws Exception {
      Harness h = threeRowHarness();
      h.service.deleteRows("tok", principal(), "Table1", List.of(2), "");
      verify(h.forms).deleteRows(eq("rt1"), any(), eq(""), any(), any());
   }

   @Test
   void setCellRefusesOutOfRangeRowOrColumn() {
      Harness h = threeRowHarness();
      assertRefused(h, () -> h.service.setCell("tok", principal(), "Table1", 99, 0, "x", ""),
                    "'row' 99", "0..2");
      assertRefused(h, () -> h.service.setCell("tok", principal(), "Table1", 3, 0, "x", ""),
                    "0..2");
      assertRefused(h, () -> h.service.setCell("tok", principal(), "Table1", 0, 9, "x", ""),
                    "'col' 9", "0..1");
      assertRefused(h, () -> h.service.setCell("tok", principal(), "Table1", 0, 2, "x", ""),
                    "0..1");
      assertRefused(h, () -> h.service.setCell("tok", principal(), "Table1", 0, -1, "x", ""),
                    "'col' -1");
   }

   @Test
   void setCellLastRowAndLastColumnAreAllowed() throws Exception {
      FormTableLens lens = lensWith(1, true, rowWith(FormTableRow.OLD), rowWith(FormTableRow.OLD),
         rowWith(FormTableRow.OLD), rowWith(FormTableRow.OLD));
      ColumnOption option = optionWith(true);
      when(lens.getVisibleColumnOption(1)).thenReturn(option);
      Harness h = harnessWithLens(tableWith(true, true, true, true), lens);
      h.service.setCell("tok", principal(), "Table1", 2, 1, "x", "");
      verify(h.forms).changeFormInput(eq("rt1"), any(), eq(""), any(), any());
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
            return List.of();
         }).when(sessions).mutate(anyString(), any(Principal.class), any());
      }
      catch(Exception e) {
         throw new IllegalStateException(e);
      }

      return new Harness(new FormTableRowService(sessions, forms), forms, dispatcher);
   }

   /**
    * Like {@link #harnessWith}, but {@code mutate}'s own mock also returns {@code warnings}, the
    * same {@code List<String>} {@code ViewsheetSessionService.mutate}'s real implementation
    * returns from {@code CapturingCommandDispatcher.getWarnings()}.
    */
   private static Harness harnessWithWarnings(VSAssembly assembly, List<String> warnings) {
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
            return warnings;
         }).when(sessions).mutate(anyString(), any(Principal.class), any());
      }
      catch(Exception e) {
         throw new IllegalStateException(e);
      }

      return new Harness(new FormTableRowService(sessions, forms), forms, dispatcher);
   }

   /**
    * Like {@link #harnessWith}, but {@code rvs.getViewsheetSandbox()} resolves to a lens that
    * reports {@code headerRowCount} -- for the row-index-offset and editable-cell tests, which
    * need a real {@link FormTableLens} to read {@code getHeaderRowCount()}/{@code rows()}/
    * {@code isEdit()}/{@code getVisibleColumnOption(col)} from.
    */
   private static Harness harnessWithLens(VSAssembly assembly, FormTableLens lens) {
      Harness h = harnessWithLens(assembly, lens, List.of());
      int rowsBefore = lens.getRowCount();
      // a real addRow grows the lens; without this the Bug #78211 no-op guard would reject it

      try {
         doAnswer(inv -> {
            when(lens.getRowCount()).thenReturn(rowsBefore + 1);
            return null;
         }).when(h.forms).addRow(anyString(), any(), any(), any(), any());
      }
      catch(Exception e) {
         throw new IllegalStateException(e);
      }

      return h;
   }

   private static Harness harnessWithLens(VSAssembly assembly, FormTableLens lens,
                                          List<String> warnings)
   {
      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getAssembly(anyString())).thenReturn(assembly);

      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);

      ViewsheetSandbox box = mock(ViewsheetSandbox.class);

      try {
         when(box.getFormTableLens(anyString())).thenReturn(lens);
      }
      catch(Exception e) {
         throw new IllegalStateException(e);
      }

      when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(box));

      VSFormTableService forms = mock(VSFormTableService.class);
      CapturingCommandDispatcher dispatcher = mock(CapturingCommandDispatcher.class);
      ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);

      try {
         doAnswer(invocation -> {
            ViewsheetSessionService.Mutation mutation = invocation.getArgument(2);
            mutation.run(rvs, "rt1", dispatcher);
            return warnings;
         }).when(sessions).mutate(anyString(), any(Principal.class), any());
      }
      catch(Exception e) {
         throw new IllegalStateException(e);
      }

      return new Harness(new FormTableRowService(sessions, forms), forms, dispatcher);
   }

   private static FormTableLens lensWith(int headerRowCount, boolean edit, FormTableRow... rows) {
      FormTableLens lens = mock(FormTableLens.class);
      when(lens.getHeaderRowCount()).thenReturn(headerRowCount);
      when(lens.isEdit()).thenReturn(edit);
      when(lens.rows()).thenReturn(rows);
      when(lens.getRowCount()).thenReturn(rows.length);
      ColumnSelection visible = mock(ColumnSelection.class);
      when(visible.getAttributeCount()).thenReturn(2);
      when(lens.getVisibleColumns()).thenReturn(visible);
      return lens;
   }

   private static FormTableRow rowWith(int state) {
      FormTableRow row = mock(FormTableRow.class);
      when(row.getRowState()).thenReturn(state);
      return row;
   }

   private static ColumnOption optionWith(boolean isForm) {
      ColumnOption option = mock(ColumnOption.class);
      when(option.isForm()).thenReturn(isForm);
      return option;
   }

   private static Principal principal() {
      return mock(Principal.class);
   }
}
