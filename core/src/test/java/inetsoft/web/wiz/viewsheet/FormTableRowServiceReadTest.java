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

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.StyleConstants;
import inetsoft.report.composition.*;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.lens.AttributeTableLens;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.test.*;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.TableVSAssemblyInfo;
import inetsoft.web.viewsheet.command.LoadTableDataCommand;
import inetsoft.web.viewsheet.controller.table.VSFormTableService;
import inetsoft.web.viewsheet.service.CoreLifecycleService;
import inetsoft.web.wiz.dispatch.CapturingCommandDispatcher;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Dimension;
import java.awt.Font;
import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #80478: {@code FormTableRowService.read}, the checkpoint-free paged read of a Form table
 * with per-row state and the pending flag, and the {@code formChanged} pass-through on the
 * mutator snapshots. Real {@link FormTableRowService}, real {@code BaseTableService.loadTableData},
 * real {@link CapturingCommandDispatcher}, real {@link FormTableLens}/{@link VSTableLens} over a
 * header + 150 data row table (ID = 1..150, NAME = "N1".."N150"); only the sandbox and session
 * lookups are mocked.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  LibManagerTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class FormTableRowServiceReadTest {
   private static final int ROWS = 150;

   @BeforeEach
   void setUp() throws Exception {
      build(ROWS);
   }

   private void build(int dataRows) throws Exception {
      tallRows = false;
      Viewsheet vs = new Viewsheet();
      table = new TableVSAssembly(vs, "Table1");
      table.initDefaultFormat();
      vs.addAssembly(table);

      TableVSAssemblyInfo info = (TableVSAssemblyInfo) table.getVSAssemblyInfo();
      info.setForm(true);
      info.setInsert(true);
      info.setDel(true);
      info.setEdit(true);

      ColumnSelection cols = new ColumnSelection();
      cols.addAttribute(formRef("ID", XSchema.INTEGER));
      cols.addAttribute(formRef("NAME", XSchema.STRING));
      info.setColumnSelection(cols);

      Object[][] data = new Object[dataRows + 1][];
      data[0] = new Object[] { "ID", "NAME" };

      for(int i = 1; i <= dataRows; i++) {
         data[i] = new Object[] { i, "N" + i };
      }

      form = new FormTableLens(new DefaultTableLens(data));
      form.setEdit(true);
      form.setColumnSelection(cols);

      ViewsheetSandbox box = mock(ViewsheetSandbox.class);
      when(box.getFormTableLens("Table1")).thenReturn(form);
      when(box.getVSTableLens(eq("Table1"), anyBoolean())).thenAnswer(inv -> {
         AttributeTableLens formatted = new AttributeTableLens(form);
         formatted.setFont(new Font("Dialog", Font.PLAIN, 10));
         formatted.setLineWrap(false);
         VSTableLens lens = new VSTableLens(formatted);

         if(tallRows) {
            int[] heights = new int[dataRows + 1];
            Arrays.fill(heights, 20);
            lens.setRowHeights(heights);
         }

         return lens;
      });
      when(box.getID()).thenReturn("box1");

      rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(box));
      when(rvs.isRuntime()).thenReturn(true);

      ViewsheetService engine = mock(ViewsheetService.class);
      when(engine.getViewsheet(anyString(), any())).thenReturn(rvs);
      VSFormTableService forms = new VSFormTableService(engine, mock(CoreLifecycleService.class));

      sessions = mock(ViewsheetSessionService.class);
      when(sessions.resolve(anyString(), any(Principal.class))).thenReturn(rvs);
      doAnswer(inv -> {
         ViewsheetSessionService.Mutation mutation = inv.getArgument(2);
         return CapturingCommandDispatcher.withCapturingDispatcher(user, d -> {
            mutation.run(rvs, "rt1", d);
            return d.getWarnings();
         });
      }).when(sessions).mutate(anyString(), any(Principal.class), any());
      doAnswer(inv -> {
         ViewsheetSessionService.Read<?> read = inv.getArgument(2);
         return CapturingCommandDispatcher.withCapturingDispatcher(user,
            d -> read.run(rvs, "rt1", d));
      }).when(sessions).read(anyString(), any(Principal.class), any());

      service = new FormTableRowService(sessions, forms);
   }

   // ── windowing ─────────────────────────────────────────────────────────────

   @Test
   void readsAWindowPastTheFirstHundredRows() throws Exception {
      Map<String, Object> r = service.read("tok", user, "Table1", 100, 10, "");

      List<List<Object>> rows = rows(r);
      assertEquals(10, rows.size());
      assertEquals(List.of(101, "N101"), rows.get(0));
      assertEquals(List.of(110, "N110"), rows.get(9));
      assertEquals(ROWS, r.get("rowCount"));
      assertEquals(100, r.get("offset"));
      assertEquals(10, r.get("limit"));
      assertEquals(true, r.get("hasMore"));
      assertEquals(List.of("ID", "NAME"), r.get("columns"));
      assertEquals(Collections.nCopies(10, "old"), r.get("rowStates"));
      assertNull(r.get("warnings"));
   }

   @Test
   void lastWindowIsPartialAndHasNoMore() throws Exception {
      Map<String, Object> r = service.read("tok", user, "Table1", 145, 50, "");

      assertEquals(5, rows(r).size());
      assertEquals(List.of(150, "N150"), rows(r).get(4));
      assertEquals(false, r.get("hasMore"));
      assertEquals(5, ((List<?>) r.get("rowStates")).size());
   }

   @Test
   void offsetPastTheEndReturnsNoRowsWithAWarningNotAnError() throws Exception {
      for(int offset : new int[] { ROWS, 400 }) {
         Map<String, Object> r = service.read("tok", user, "Table1", offset, 10, "");

         assertEquals(List.of(), r.get("rows"));
         assertEquals(List.of(), r.get("rowStates"));
         assertEquals(ROWS, r.get("rowCount"));
         assertEquals(false, r.get("hasMore"));
         assertEquals(List.of("ID", "NAME"), r.get("columns"));
         assertTrue(r.get("warnings").toString().contains("offset " + offset), r.toString());
      }
   }

   @Test
   void aLimitAboveTheMaximumIsClampedWithAWarning() throws Exception {
      Map<String, Object> r = service.read("tok", user, "Table1", 0, 500, "");

      assertEquals(FormTableRowService.MAX_READ_LIMIT, r.get("limit"));
      assertEquals(ROWS, rows(r).size());
      assertTrue(r.get("warnings").toString().contains("500"), r.toString());

      Map<String, Object> small = service.read("tok", user, "Table1", 0, 120, "");
      assertEquals(120, rows(small).size());
      assertNull(small.get("warnings"));
   }

   /**
    * loadTableData fills the assembly's pixel height, so a tall table returns more lens rows than
    * the {@code limit} it was asked for; the read must still honor the limit.
    */
   @Test
   void aTallAssemblyStillReturnsNoMoreThanTheLimit() throws Exception {
      tallRows = true;
      table.setPixelSize(new Dimension(400, 20000));

      Map<String, Object> r = service.read("tok", user, "Table1", 10, 5, "");

      assertEquals(5, rows(r).size());
      assertEquals(5, ((List<?>) r.get("rowStates")).size());
      assertEquals(List.of(11, "N11"), rows(r).get(0));
      assertEquals(true, r.get("hasMore"));
   }

   @Test
   void anEmptyTableReadsAsZeroRowsWithoutAWarning() throws Exception {
      build(0);

      Map<String, Object> r = service.read("tok", user, "Table1", 0, 50, "");

      assertEquals(0, r.get("rowCount"));
      assertEquals(List.of(), r.get("rows"));
      assertEquals(false, r.get("hasMore"));
      assertEquals(false, r.get("formChanged"));
      assertNull(r.get("warnings"));
   }

   // ── row state and the pending flag ────────────────────────────────────────

   /**
    * {@code lens.rows()[r]} is the unsorted base order; under a column sort the displayed row
    * {@code r} is another base row, so reading the state from it reports the wrong row.
    */
   @Test
   void rowStateFollowsTheDisplayedRowUnderAColumnSort() throws Exception {
      form.sort(0, StyleConstants.SORT_DESC);
      // displayed data row 0 is ID 150
      form.setObject(form.getHeaderRowCount(), 1, "edited");

      Map<String, Object> r = service.read("tok", user, "Table1", 0, 3, "");

      assertEquals(150, rows(r).get(0).get(0));
      assertEquals(List.of("changed", "old", "old"), r.get("rowStates"));
   }

   @Test
   void reportsPendingCountsDeletedCountAndFormChanged() throws Exception {
      int header = form.getHeaderRowCount();
      // IDs 1-3 deleted, IDs 4 and 5 edited, one row appended: 3 deleted, 2 changed, 1 added
      form.deleteRow(header);
      form.deleteRow(header);
      form.deleteRow(header);
      form.setObject(header, 1, "edited");
      form.setObject(header + 1, 1, "edited");
      form.appendRow(header + ROWS - 4);

      Map<String, Object> r = service.read("tok", user, "Table1", 0, 50, "");

      assertEquals(true, r.get("formChanged"));
      assertEquals(Map.of("changed", 2, "added", 1, "deleted", 3), r.get("pendingCounts"));
      // three deleted, one added
      assertEquals(ROWS - 2, r.get("rowCount"));
      // the deleted rows are gone from the window: the first displayed row is ID 4
      assertEquals(4, rows(r).get(0).get(0));
      assertEquals(List.of("changed", "changed", "old"),
                   ((List<?>) r.get("rowStates")).subList(0, 3));

      Map<String, Object> tail = service.read("tok", user, "Table1", ROWS - 3, 10, "");
      assertEquals(List.of("added"), tail.get("rowStates"));
   }

   @Test
   void anUntouchedTableIsNotChanged() throws Exception {
      Map<String, Object> r = service.read("tok", user, "Table1", 0, 5, "");

      assertEquals(false, r.get("formChanged"));
      assertEquals(Map.of("changed", 0, "added", 0, "deleted", 0), r.get("pendingCounts"));
   }

   // ── guards ────────────────────────────────────────────────────────────────

   @Test
   void refusesABadOffsetOrLimitByName() {
      Exception e = assertThrows(IllegalArgumentException.class,
         () -> service.read("tok", user, "Table1", -1, 10, ""));
      assertTrue(e.getMessage().contains("'offset'"), e.getMessage());

      e = assertThrows(IllegalArgumentException.class,
         () -> service.read("tok", user, "Table1", 0, 0, ""));
      assertTrue(e.getMessage().contains("'limit'"), e.getMessage());
      verifyNoInteractions(sessions);
   }

   @Test
   void refusesAnUnknownAssemblyAndANonFormTable() throws Exception {
      Exception e = assertThrows(IllegalArgumentException.class,
         () -> service.read("tok", user, "Nope", 0, 10, ""));
      assertTrue(e.getMessage().contains("Nope"), e.getMessage());

      ((TableVSAssemblyInfo) table.getVSAssemblyInfo()).setForm(false);
      e = assertThrows(IllegalArgumentException.class,
         () -> service.read("tok", user, "Table1", 0, 10, ""));
      assertTrue(e.getMessage().contains("not a Form table"), e.getMessage());
   }

   @Test
   void refusesADesignModePairing() {
      when(rvs.isRuntime()).thenReturn(false);

      Exception e = assertThrows(IllegalArgumentException.class,
         () -> service.read("tok", user, "Table1", 0, 10, ""));
      assertTrue(e.getMessage().contains("Design tab"), e.getMessage());
   }

   @Test
   void aReadIsNotAMutationAndKeepsTheViewerScrollPosition() throws Exception {
      table.setLastStartRow(7);

      service.read("tok", user, "Table1", 100, 10, "");

      verify(sessions, never()).mutate(anyString(), any(Principal.class), any());
      assertEquals(7, table.getLastStartRow());
   }

   // ── formChanged on the mutator snapshots ──────────────────────────────────

   @Test
   void mutatorSnapshotsCarryFormChanged() throws Exception {
      Map<String, Object> afterEdit = service.setCell("tok", user, "Table1", 1, 1, "Bobby", "");
      assertEquals(true, afterEdit.get("formChanged"));

      Map<String, Object> afterInsert = service.insertRow("tok", user, "Table1", 0, false, "");
      assertEquals(true, afterInsert.get("formChanged"));
   }

   @Test
   void deleteSnapshotCarriesFormChanged() throws Exception {
      Map<String, Object> afterDelete = service.deleteRows("tok", user, "Table1", List.of(0), "");

      assertEquals(true, afterDelete.get("formChanged"));
   }

   @Test
   void applySnapshotCarriesFormChanged() throws Exception {
      ((TableVSAssemblyInfo) table.getVSAssemblyInfo()).setWriteBack(true);
      VSFormTableService applier = mock(VSFormTableService.class);
      doAnswer(inv -> {
         LoadTableDataCommand load = mock(LoadTableDataCommand.class);
         when(load.formChanged()).thenReturn(true);
         CapturingCommandDispatcher d = inv.getArgument(3);
         d.sendCommand("Table1", load);
         return null;
      }).when(applier).applyChanges(anyString(), any(), anyString(), any(), any());

      Map<String, Object> afterApply =
         new FormTableRowService(sessions, applier).apply("tok", user, "Table1", "");

      assertEquals(true, afterApply.get("formChanged"));
   }

   // ── a read that loaded nothing must not look like a table with more rows ──

   @Test
   void aTableThatWasNotRenderedFailsLoudInsteadOfAnEmptyWindowWithMoreRows() throws Exception {
      when(rvs.getViewsheetSandbox().orElseThrow().getVSTableLens(eq("Table1"), anyBoolean()))
         .thenReturn(null);

      IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
         () -> service.read("tok", user, "Table1", 0, 50, ""));

      assertTrue(e.getMessage().contains("'Table1'"), e.getMessage());
      assertTrue(e.getMessage().contains("150 data row(s)"), e.getMessage());
      assertTrue(e.getMessage().contains("not currently rendered"), e.getMessage());
   }

   @Test
   void anEmptyOrPastTheEndWindowOfAnUnrenderedTableStillAnswersNormally() throws Exception {
      when(rvs.getViewsheetSandbox().orElseThrow().getVSTableLens(eq("Table1"), anyBoolean()))
         .thenReturn(null);

      Map<String, Object> past = service.read("tok", user, "Table1", ROWS, 50, "");

      assertEquals(List.of(), past.get("rows"));
      assertEquals(false, past.get("hasMore"));
   }

   @Test
   void populateSnapshotReportsFormChangedFalseForACleanTable() {
      LoadTableDataCommand load = mock(LoadTableDataCommand.class);
      when(load.runtimeDataRowCount()).thenReturn(0);
      when(load.headerRowCount()).thenReturn(1);
      when(load.start()).thenReturn(0);
      when(load.formChanged()).thenReturn(false);
      when(load.tableCells()).thenReturn(new inetsoft.web.viewsheet.model.table.BaseTableCellModel[0][]);
      CapturingCommandDispatcher dispatcher = mock(CapturingCommandDispatcher.class);
      when(dispatcher.getCapturedCommands()).thenReturn(List.of(
         new CapturingCommandDispatcher.Command("Table1", "LoadTableDataCommand", load)));

      Map<String, Object> result = new LinkedHashMap<>();
      FormTableRowService.populateSnapshot(result, dispatcher, "Table1");

      assertEquals(false, result.get("formChanged"));
   }

   @SuppressWarnings("unchecked")
   private static List<List<Object>> rows(Map<String, Object> result) {
      return (List<List<Object>>) result.get("rows");
   }

   private static FormRef formRef(String name, String type) {
      ColumnRef column = new ColumnRef(new AttributeRef(null, name));
      column.setDataType(type);
      FormRef ref = FormRef.toFormRef(column);
      ref.getOption().setForm(true);
      return ref;
   }

   private boolean tallRows;
   private final Principal user = () -> "admin";
   private TableVSAssembly table;
   private FormTableLens form;
   private RuntimeViewsheet rvs;
   private ViewsheetSessionService sessions;
   private FormTableRowService service;
}
