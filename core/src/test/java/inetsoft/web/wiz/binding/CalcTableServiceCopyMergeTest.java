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
import inetsoft.report.TableCellBinding;
import inetsoft.report.TableLayout;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.test.*;
import inetsoft.uql.erm.CalcAggregate;
import inetsoft.uql.viewsheet.CalcTableVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.CalcTableVSAssemblyInfo;
import inetsoft.web.binding.controller.VSTableLayoutService;
import inetsoft.web.binding.event.CopyCutCalcCellEvent;
import inetsoft.web.binding.handler.TableLayoutHandler;
import inetsoft.web.binding.handler.VSColumnHandler;
import inetsoft.web.binding.service.DataRefModelFactoryService;
import inetsoft.web.wiz.dispatch.CapturingCommandDispatcher;
import inetsoft.web.wiz.viewsheet.ViewsheetSessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Dimension;
import java.awt.Rectangle;
import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Regression tests for Redmine #78534: {@code copy_calc_cells} copy/cut returned ok while a
 * pasted cell landed under a surviving merge (stored on the hidden cell, overwritten from the
 * anchor on split; a cut had already cleared the source) or past the grid edge. These run the
 * real {@link TableLayoutHandler} against a real calc layout, because whether a merge survives
 * a paste depends on the paste itself.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class CalcTableServiceCopyMergeTest {
   private final TableLayoutHandler handler = new TableLayoutHandler();
   private CalcTableVSAssemblyInfo info;
   private VSTableLayoutService layoutService;
   private CalcTableService service;

   /** A 4x4 calc grid whose copyCut runs the real copy operation on the live info. */
   @BeforeEach
   void setUp() throws Exception {
      CalcTableVSAssembly assembly = new CalcTableVSAssembly(new Viewsheet(), "Calc1");
      info = (CalcTableVSAssemblyInfo) assembly.getInfo();
      handler.doOperation(info, "appendRow", new Rectangle(0, 0, 1, 2), 2);
      handler.doOperation(info, "appendCol", new Rectangle(0, 0, 2, 1), 2);
      assertEquals(4, layout().getRowCount());
      assertEquals(4, layout().getColCount());

      for(int r = 0; r < 4; r++) {
         for(int c = 0; c < 4; c++) {
            layout().setCellBinding(r, c, new TableCellBinding(CellBinding.BIND_TEXT, null));
         }
      }

      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getAssembly(anyString())).thenReturn(assembly);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);
      doAnswer(invocation -> {
         ViewsheetSessionService.Mutation mutation = invocation.getArgument(2);
         return CapturingCommandDispatcher.withCapturingDispatcher(
            principal(), dispatcher -> { mutation.run(rvs, "rt1", dispatcher); return null; });
      }).when(sessions).mutate(anyString(), any(Principal.class), any());
      when(sessions.resolve(anyString(), any(Principal.class))).thenReturn(rvs);

      layoutService = mock(VSTableLayoutService.class);
      doAnswer(invocation -> {
         CopyCutCalcCellEvent event = invocation.getArgument(1);
         Rectangle[] selections = event.getSelections();
         handler.copyOperation(info, event.getOp(), selections[0], selections[1]);
         return null;
      }).when(layoutService).copyCut(anyString(), any(), any(), any());

      TableLayoutHandler serviceHandler = spy(handler);
      doReturn(new CalcAggregate[0]).when(serviceHandler).getCalcAggregateFields(any(), any());
      service = new CalcTableService(sessions, layoutService,
                                     mock(DataRefModelFactoryService.class),
                                     mock(BindableFieldsService.class),
                                     mock(VSColumnHandler.class), serviceHandler);
   }

   // ── refused ───────────────────────────────────────────────────────────────

   @Test
   void refusesACopyOntoACellSwallowedByAMerge() {
      text(0, 0, "ANCHOR");
      text(1, 0, "SRC");
      merge(0, 0, 1, 2);

      IllegalArgumentException thrown = assertThrows(
         IllegalArgumentException.class, () -> copy("copy", 1, 0, 1, 1, 0, 1));

      assertTrue(thrown.getMessage().contains(
         "Pasting cell [1,0] would land on [0,1], which is merged into [0,0]"),
                 thrown.getMessage());
      assertTrue(thrown.getMessage().contains("Nothing was changed"));
      verifyNoInteractions(layoutService);
   }

   @Test
   void refusedCutKeepsItsSource() {
      text(0, 0, "ANCHOR");
      text(1, 0, "SRC");
      merge(0, 0, 1, 2);

      assertThrows(IllegalArgumentException.class, () -> copy("cut", 1, 0, 1, 1, 0, 1));

      assertEquals("SRC", value(1, 0));
      assertEquals("ANCHOR", value(0, 0));
      assertEquals(new Dimension(2, 1), layout().getSpan(0, 0));
   }

   /** R4: a cut from a cell below the hidden cell rather than beside it. */
   @Test
   void refusesACutOntoASwallowedCellFromAnotherColumn() {
      text(0, 0, "ANCHOR");
      text(1, 1, "SRC");
      merge(0, 0, 1, 2);

      assertThrows(IllegalArgumentException.class, () -> copy("cut", 1, 1, 1, 1, 0, 1));

      assertEquals("SRC", value(1, 1));
   }

   /** B: the target is the anchor, but the range's second cell lands on the hidden cell. */
   @Test
   void refusesARangeWhoseSecondCellLandsUnderTheMerge() {
      text(0, 0, "ANCHOR");
      text(1, 0, "S1");
      text(1, 1, "S2");
      merge(0, 0, 1, 2);

      IllegalArgumentException thrown = assertThrows(
         IllegalArgumentException.class, () -> copy("copy", 1, 0, 1, 2, 0, 0));

      assertTrue(thrown.getMessage().contains(
         "Pasting cell [1,1] would land on [0,1], which is merged into [0,0]"),
                 thrown.getMessage());
      assertEquals("ANCHOR", value(0, 0));
   }

   /** E2: a pasted merge that fits inside a bigger one leaves the bigger one in place. */
   @Test
   void refusesAMergedCellPastedInsideABiggerMerge() {
      text(0, 0, "BIG");
      merge(0, 0, 1, 3);
      text(2, 0, "Q");
      merge(2, 0, 1, 2);

      IllegalArgumentException thrown = assertThrows(
         IllegalArgumentException.class, () -> copy("copy", 2, 0, 1, 1, 0, 1));

      assertTrue(thrown.getMessage().contains(
         "Pasting cell [2,0] would land on [0,1], which is merged into [0,0]"),
                 thrown.getMessage());
   }

   /** E3: paste clips a range at the grid edge, and a cut has already cleared the source. */
   @Test
   void refusesACutWhoseContentFallsOffTheGrid() {
      text(1, 0, "X");
      text(1, 1, "Y");

      IllegalArgumentException thrown = assertThrows(
         IllegalArgumentException.class, () -> copy("cut", 1, 0, 1, 2, 3, 3));

      assertTrue(thrown.getMessage().contains(
         "Pasting cell [1,1] would land on [3,4], outside the grid"), thrown.getMessage());
      assertEquals("X", value(1, 0));
      assertEquals("Y", value(1, 1));
      verifyNoInteractions(layoutService);
   }

   // ── still allowed ─────────────────────────────────────────────────────────

   /** C: pasting onto the anchor itself replaces the merged cell's content. */
   @Test
   void allowsAPasteOntoTheAnchor() throws Exception {
      text(0, 0, "ANCHOR");
      text(1, 0, "SRC");
      merge(0, 0, 1, 2);

      copy("copy", 1, 0, 1, 1, 0, 0);

      assertEquals("SRC", value(0, 0));
      assertEquals(new Dimension(2, 1), layout().getSpan(0, 0));
   }

   /** R1: a cut clears its own merge before pasting, so the merged cell moves one column. */
   @Test
   void allowsCuttingAMergedCellOntoItsOwnHiddenCell() throws Exception {
      text(0, 0, "M");
      merge(0, 0, 1, 2);
      text(1, 0, "KEEP");

      copy("cut", 0, 0, 1, 1, 0, 1);

      assertEquals("M", value(0, 1));
      assertEquals(new Dimension(2, 1), layout().getSpan(0, 1));
      assertEquals("KEEP", value(1, 0));
   }

   /** R2: an aligned merge pasted over a merge of the same shape. */
   @Test
   void allowsAMergePastedOverAnAlignedMerge() throws Exception {
      text(0, 0, "A");
      merge(0, 0, 1, 2);
      text(2, 0, "B");
      merge(2, 0, 1, 2);

      copy("copy", 2, 0, 1, 1, 0, 0);

      assertEquals("B", value(0, 0));
      assertEquals(new Dimension(2, 1), layout().getSpan(0, 0));
   }

   /** D: a pasted merge that partly overlaps another merge dissolves it (native behavior). */
   @Test
   void allowsAMergePastedOverlappingAnotherMerge() throws Exception {
      text(0, 0, "A2W");
      merge(0, 0, 1, 2);
      text(2, 1, "B");
      merge(2, 1, 1, 2);

      copy("copy", 0, 0, 1, 1, 2, 0);

      assertEquals("A2W", value(2, 0));
   }

   /** E1: the pasted merge dissolves the merge that would have hidden "Z". */
   @Test
   void allowsAPasteWhoseOwnMergeDissolvesTheMergeAtTheTarget() throws Exception {
      text(0, 0, "T0");
      text(0, 1, "M");
      merge(0, 1, 1, 2);
      text(2, 0, "P");
      merge(2, 0, 1, 2);
      text(2, 2, "Z");

      copy("copy", 2, 0, 1, 3, 0, 0);

      assertEquals("P", value(0, 0));
      assertEquals("Z", value(0, 2));
      assertNull(layout().getSpan(0, 1));
   }

   /** R3: only the hidden half of a merged source falls past the grid edge. */
   @Test
   void allowsAMergedSourceWhoseHiddenHalfFallsOffTheGrid() throws Exception {
      text(0, 0, "E");
      merge(0, 0, 1, 2);

      copy("copy", 0, 0, 1, 1, 2, 3);

      assertEquals("E", value(2, 3));
   }

   /** L1: only a blank cell falls past the grid edge. */
   @Test
   void allowsACutWhoseBlankCellFallsOffTheGrid() throws Exception {
      text(1, 0, "X");

      copy("cut", 1, 0, 1, 2, 3, 3);

      assertEquals("X", value(3, 3));
   }

   /** A blank cell of the range lands under a merge; nothing visible is lost. */
   @Test
   void allowsABlankCellLandingUnderAMerge() throws Exception {
      text(0, 2, "ANCHOR");
      merge(0, 2, 1, 2);
      text(1, 0, "A");

      copy("copy", 1, 0, 1, 2, 0, 2);

      assertEquals("A", value(0, 2));
      assertEquals(new Dimension(2, 1), layout().getSpan(0, 2));
   }

   /** A column-bound cell is content too, not only text. */
   @Test
   void refusesAColumnBoundCellLandingUnderAMerge() {
      text(0, 0, "ANCHOR");
      merge(0, 0, 1, 2);
      layout().setCellBinding(1, 0, new TableCellBinding(CellBinding.BIND_COLUMN, "Region"));

      IllegalArgumentException thrown = assertThrows(
         IllegalArgumentException.class, () -> copy("cut", 1, 0, 1, 1, 0, 1));

      assertTrue(thrown.getMessage().contains(
         "Pasting cell [1,0] would land on [0,1], which is merged into [0,0]"),
                 thrown.getMessage());
      assertEquals("Region", value(1, 0));
      verifyNoInteractions(layoutService);
   }

   // ── helpers ───────────────────────────────────────────────────────────────

   private TableLayout layout() {
      return info.getTableLayout();
   }

   private void text(int row, int col, String value) {
      layout().setCellBinding(row, col, new TableCellBinding(CellBinding.BIND_TEXT, value));
   }

   private void merge(int row, int col, int rows, int cols) {
      handler.doOperation(info, "mergeCells", new Rectangle(col, row, cols, rows), 0);
   }

   private String value(int row, int col) {
      CellBinding binding = layout().getCellBinding(row, col);
      return binding == null ? null : binding.getValue();
   }

   /** Mirrors BindingAgentController: the target takes the source request's size. */
   private void copy(String op, int srow, int scol, int rows, int cols, int trow, int tcol)
      throws Exception
   {
      service.copyCells("tok", principal(), "Calc1", op,
                        new Rectangle(scol, srow, cols, rows),
                        new Rectangle(tcol, trow, cols, rows));
   }

   private static Principal principal() {
      return () -> "admin";
   }
}
