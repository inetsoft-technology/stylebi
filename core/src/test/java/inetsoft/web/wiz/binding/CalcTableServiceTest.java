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
package inetsoft.web.wiz.binding;

import inetsoft.report.CellBinding;
import inetsoft.report.GroupableCellBinding;
import inetsoft.report.TableLayout;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.CalcTableVSAssemblyInfo;
import inetsoft.web.binding.controller.VSTableLayoutService;
import inetsoft.web.binding.event.CopyCutCalcCellEvent;
import inetsoft.web.binding.event.ModifyTableLayoutEvent;
import inetsoft.web.binding.event.SetCellBindingEvent;
import inetsoft.web.binding.model.table.CellBindingInfo;
import inetsoft.web.wiz.viewsheet.ViewsheetSessionService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("core")
class CalcTableServiceTest {
   private static Map<String, Object> spec(Object... pairs) {
      Map<String, Object> spec = new LinkedHashMap<>();

      for(int i = 0; i < pairs.length; i += 2) {
         spec.put((String) pairs[i], pairs[i + 1]);
      }

      return spec;
   }

   private static Map<String, Object> columnCell(String column) {
      return spec("content", "column", "grouping", "group", "expand", "vertical",
                  "field", Map.of("column", column, "type", "dimension"));
   }

   @Test
   void readsTheGridDimensionsAndEveryCell() throws Exception {
      Harness h = harness(2, 3);

      Map<String, Object> layout = h.service.readLayout("tok", principal(), "Calc1");

      assertEquals(2, layout.get("rowCount"));
      assertEquals(3, layout.get("colCount"));
      @SuppressWarnings("unchecked")
      List<Object> cells = (List<Object>) layout.get("cells");
      assertEquals(6, cells.size());
      verify(h.sessions, never()).mutate(anyString(), any(Principal.class), any());
   }

   @Test
   void readsACellInTheTokenVocabulary() throws Exception {
      Harness h = harness(2, 2);
      CellBindingInfo info = new CellBindingInfo();
      info.setType(CellBinding.BIND_COLUMN);
      info.setBtype(CellBinding.GROUP);
      info.setExpansion(GroupableCellBinding.EXPAND_V);
      when(h.layoutService.getCellBindingInfo(any(), eq(1), eq(0))).thenReturn(info);

      Map<String, Object> read = h.service.readCell("tok", principal(), "Calc1", 1, 0);

      @SuppressWarnings("unchecked")
      Map<String, Object> binding = (Map<String, Object>) read.get("binding");
      assertEquals("column", binding.get("content"));
      assertEquals("group", binding.get("grouping"));
      assertEquals("vertical", binding.get("expand"));
   }

   @Test
   void setsACellBindingThroughTheCellAddressedEndpoint() throws Exception {
      Harness h = harness(3, 3);

      h.service.setCellBinding("tok", principal(), "Calc1", 2, 1, columnCell("Region"));

      ArgumentCaptor<SetCellBindingEvent> captor =
         ArgumentCaptor.forClass(SetCellBindingEvent.class);
      verify(h.layoutService).setCellBinding(eq("rt1"), captor.capture(), any(Principal.class),
                                             any());
      SetCellBindingEvent event = captor.getValue();
      assertEquals("Calc1", event.getName());
      assertEquals(2, event.getSelectCells()[0].getRow());
      assertEquals(1, event.getSelectCells()[0].getCol());
      assertEquals(CellBinding.BIND_COLUMN, event.getBinding().getType());
      assertEquals("Region", event.getBinding().getValue());
   }

   @Test
   void eachBindIsExactlyOneCheckpoint() throws Exception {
      Harness h = harness(3, 3);

      h.service.setCellBinding("tok", principal(), "Calc1", 0, 0, columnCell("Region"));

      verify(h.sessions, times(1)).mutate(anyString(), any(Principal.class), any());
   }

   /**
    * A coordinate outside the grid must be refused with the grid's real dimensions, since the
    * usual cause is a coordinate read before a layout change.
    */
   @Test
   void refusesACoordinateOutsideTheGridReportingItsSize() {
      Harness h = harness(2, 2);

      Exception thrown = assertThrows(
         Exception.class,
         () -> h.service.setCellBinding("tok", principal(), "Calc1", 5, 0,
                                        columnCell("Region")));

      assertTrue(thrown.getMessage().contains("2 row"));
      assertTrue(thrown.getMessage().contains("stale"));
   }

   @Test
   void validatesTheBindingBeforeTouchingTheRuntime() {
      Harness h = harness(3, 3);

      assertThrows(Exception.class,
                   () -> h.service.setCellBinding("tok", principal(), "Calc1", 0, 0,
                                                  spec("type", "column")));

      verifyNoInteractions(h.sessions);
   }

   @Test
   void refusesAColumnCellWhoseFieldHasNoType() {
      Harness h = harness(3, 3);

      assertThrows(Exception.class,
                   () -> h.service.setCellBinding(
                      "tok", principal(), "Calc1", 0, 0,
                      spec("content", "column", "field", Map.of("column", "Region"))));
   }

   @Test
   void bindsATextCellFromItsLiteralValue() throws Exception {
      Harness h = harness(2, 2);

      h.service.setCellBinding("tok", principal(), "Calc1", 0, 0,
                               spec("content", "text", "value", "Total"));

      ArgumentCaptor<SetCellBindingEvent> captor =
         ArgumentCaptor.forClass(SetCellBindingEvent.class);
      verify(h.layoutService).setCellBinding(eq("rt1"), captor.capture(), any(Principal.class),
                                             any());
      assertEquals(CellBinding.BIND_TEXT, captor.getValue().getBinding().getType());
      assertEquals("Total", captor.getValue().getBinding().getValue());
   }

   @Test
   void refusesANonCalcAssemblyPointingAtTheShelfTools() {
      Harness h = harnessFor(mock(CrosstabVSAssembly.class), 0, 0);

      Exception thrown = assertThrows(
         Exception.class, () -> h.service.readLayout("tok", principal(), "Crosstab1"));

      assertTrue(thrown.getMessage().contains("Crosstab1"));
      assertTrue(thrown.getMessage().contains("get_table_binding"));
   }

   @Test
   void vocabularyListsTheTokensRatherThanConstants() {
      Harness h = harness(1, 1);

      Map<String, Object> vocabulary = h.service.vocabulary();

      assertTrue(String.valueOf(vocabulary.get("content")).contains("formula"));
      assertTrue(String.valueOf(vocabulary.get("expand")).contains("vertical"));
   }

   // ── harness ───────────────────────────────────────────────────────────────

   private record Harness(CalcTableService service, ViewsheetSessionService sessions,
                          VSTableLayoutService layoutService) {}

   private static Harness harness(int rows, int cols) {
      CalcTableVSAssembly assembly = mock(CalcTableVSAssembly.class);
      CalcTableVSAssemblyInfo info = mock(CalcTableVSAssemblyInfo.class);
      TableLayout layout = mock(TableLayout.class);
      when(layout.getRowCount()).thenReturn(rows);
      when(layout.getColCount()).thenReturn(cols);
      when(info.getTableLayout()).thenReturn(layout);
      when(assembly.getInfo()).thenReturn(info);
      return harnessFor(assembly, rows, cols);
   }

   private static Harness harnessFor(VSAssembly assembly, int rows, int cols) {
      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getAssembly(anyString())).thenReturn(assembly);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);

      ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);

      try {
         doAnswer(invocation -> {
            ViewsheetSessionService.Mutation mutation = invocation.getArgument(2);
            mutation.run(rvs, "rt1", null);
            return null;
         }).when(sessions).mutate(anyString(), any(Principal.class), any());
         when(sessions.resolve(anyString(), any(Principal.class))).thenReturn(rvs);
      }
      catch(Exception e) {
         throw new IllegalStateException(e);
      }

      VSTableLayoutService layoutService = mock(VSTableLayoutService.class);
      return new Harness(new CalcTableService(sessions, layoutService), sessions, layoutService);
   }

   private static Principal principal() {
      return () -> "admin";
   }

   // ── layout operations (2e Phase 2) ────────────────────────────────────────

   @Test
   void insertsARowAndReturnsTheUpdatedLayout() throws Exception {
      Harness h = harness(3, 3);

      Map<String, Object> updated =
         h.service.modifyLayout("tok", principal(), "Calc1", "insertRow", 1, 0, null, null, 1);

      ArgumentCaptor<ModifyTableLayoutEvent> captor =
         ArgumentCaptor.forClass(ModifyTableLayoutEvent.class);
      verify(h.layoutService).modifyLayout(eq("rt1"), captor.capture(), any(Principal.class),
                                           any());
      assertEquals("insertRow", captor.getValue().getOp());
      assertEquals(1, captor.getValue().getNum());
      assertEquals(1, captor.getValue().getSelection().y);
      assertNotNull(updated.get("cells"), "the updated layout comes back with the response");
   }

   /**
    * Coordinates read before a layout op are stale afterwards, so the response says so rather
    * than leaving the caller to remember.
    */
   @Test
   void theResponseWarnsThatEarlierCoordinatesAreStale() throws Exception {
      Harness h = harness(3, 3);

      Map<String, Object> updated =
         h.service.modifyLayout("tok", principal(), "Calc1", "insertRow", 1, 0, null, null, 1);

      assertTrue(String.valueOf(updated.get("note")).contains("stale"));
   }

   @Test
   void acceptsTheOpNameSpellings() throws Exception {
      for(String op : List.of("insertRow", "insert_row", "insertrow")) {
         Harness h = harness(3, 3);
         h.service.modifyLayout("tok", principal(), "Calc1", op, 0, 0, null, null, 1);
         ArgumentCaptor<ModifyTableLayoutEvent> captor =
            ArgumentCaptor.forClass(ModifyTableLayoutEvent.class);
         verify(h.layoutService).modifyLayout(anyString(), captor.capture(), any(Principal.class),
                                              any());
         assertEquals("insertRow", captor.getValue().getOp(), "'" + op + "' should resolve");
      }
   }

   @Test
   void refusesAnUnknownLayoutOpListingTheValid() {
      Harness h = harness(3, 3);

      Exception thrown = assertThrows(
         Exception.class,
         () -> h.service.modifyLayout("tok", principal(), "Calc1", "explode", 0, 0, null, null,
                                      1));

      assertTrue(thrown.getMessage().contains("explode"));
      assertTrue(thrown.getMessage().contains("mergeCells"));
   }

   /** Merging one cell is a handler no-op that would otherwise report success. */
   @Test
   void refusesMergingASingleCell() {
      Harness h = harness(3, 3);

      Exception thrown = assertThrows(
         Exception.class,
         () -> h.service.modifyLayout("tok", principal(), "Calc1", "mergeCells", 0, 0, 1, 1, 1));

      assertTrue(thrown.getMessage().contains("report success"));
   }

   @Test
   void acceptsMergingASpanningSelection() throws Exception {
      Harness h = harness(3, 3);

      h.service.modifyLayout("tok", principal(), "Calc1", "mergeCells", 0, 0, 2, 2, 1);

      ArgumentCaptor<ModifyTableLayoutEvent> captor =
         ArgumentCaptor.forClass(ModifyTableLayoutEvent.class);
      verify(h.layoutService).modifyLayout(anyString(), captor.capture(), any(Principal.class),
                                           any());
      assertEquals(2, captor.getValue().getSelection().width);
      assertEquals(2, captor.getValue().getSelection().height);
   }

   /** Splitting an unmerged cell is the other handler no-op. */
   @Test
   void refusesSplittingAnUnmergedCell() {
      Harness h = harness(3, 3);

      Exception thrown = assertThrows(
         Exception.class,
         () -> h.service.modifyLayout("tok", principal(), "Calc1", "splitCells", 0, 0, null,
                                      null, 1));

      assertTrue(thrown.getMessage().contains("not merged"));
   }

   @Test
   void refusesAnNBelowOne() {
      Harness h = harness(3, 3);

      assertThrows(Exception.class,
                   () -> h.service.modifyLayout("tok", principal(), "Calc1", "insertRow", 0, 0,
                                                null, null, 0));
   }

   @Test
   void refusesALayoutOpOutsideTheGrid() {
      Harness h = harness(2, 2);

      Exception thrown = assertThrows(
         Exception.class,
         () -> h.service.modifyLayout("tok", principal(), "Calc1", "insertRow", 9, 0, null, null,
                                      1));

      assertTrue(thrown.getMessage().contains("stale"));
   }

   // ── copy / cut / remove (2e Phase 2) ──────────────────────────────────────

   @Test
   void copiesARangeToATarget() throws Exception {
      Harness h = harness(4, 4);

      h.service.copyCells("tok", principal(), "Calc1", "copy",
                          new java.awt.Rectangle(0, 0, 2, 1),
                          new java.awt.Rectangle(0, 2, 2, 1));

      ArgumentCaptor<CopyCutCalcCellEvent> captor =
         ArgumentCaptor.forClass(CopyCutCalcCellEvent.class);
      verify(h.layoutService).copyCut(eq("rt1"), captor.capture(), any(Principal.class), any());
      assertEquals("copy", captor.getValue().getOp());
      assertEquals(2, captor.getValue().getSelections().length);
   }

   @Test
   void removeNeedsNoTarget() throws Exception {
      Harness h = harness(4, 4);

      h.service.copyCells("tok", principal(), "Calc1", "remove",
                          new java.awt.Rectangle(0, 0, 1, 1), null);

      verify(h.layoutService).copyCut(anyString(), any(CopyCutCalcCellEvent.class),
                                      any(Principal.class), any());
   }

   /** Without a target there is nowhere to paste and the operation would do nothing. */
   @Test
   void refusesACopyWithNoTarget() {
      Harness h = harness(4, 4);

      Exception thrown = assertThrows(
         Exception.class,
         () -> h.service.copyCells("tok", principal(), "Calc1", "copy",
                                   new java.awt.Rectangle(0, 0, 1, 1), null));

      assertTrue(thrown.getMessage().contains("nowhere to paste"));
   }

   @Test
   void refusesAnUnknownCopyOp() {
      Harness h = harness(4, 4);

      assertThrows(Exception.class,
                   () -> h.service.copyCells("tok", principal(), "Calc1", "duplicate",
                                             new java.awt.Rectangle(0, 0, 1, 1), null));
   }

   @Test
   void vocabularyListsTheLayoutAndCopyOps() {
      Harness h = harness(1, 1);

      Map<String, Object> vocabulary = h.service.vocabulary();

      assertTrue(String.valueOf(vocabulary.get("layoutOps")).contains("appendCol"));
      assertTrue(String.valueOf(vocabulary.get("copyOps")).contains("remove"));
   }
}
