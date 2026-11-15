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
package inetsoft.report.io.viewsheet.excel;

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.test.SwapperTestConfiguration;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.SelectionTreeVSAssemblyInfo;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #80207: Excel export (Match Layout) of a selection tree silently dropped its last nodes.
 * Nodes were placed by the sum of per-row heights rounded to 20px and cut off at the box's raw
 * bottom row, so the rounding error accumulated (cell height mod 20 >= 10). Nodes are now mapped
 * cumulatively from raw pixels like the list, bounded by the box and the neighbour below, and
 * "More..." is written when they really cannot fit. The tests run the real exporter grid and
 * the real tree writer, and read cells and merged regions back.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ExcelSelectionTreeFitTest {
   /** The reporter cases. */
   @Test
   void reporterCases() throws Exception {
      assertEquals(values(4), new Case(153, 142, 31, 4).run().labels);
      assertEquals(values(6), new Case(160, 204, 31, 6).run().labels);
   }

   /** Cell 20 and 40-49 never lost nodes; they must still not. */
   @Test
   void controls() throws Exception {
      assertEquals(values(4), new Case(153, 142, 20, 4).run().labels);
      assertEquals(values(10), new Case(160, 18 + 10 * 40, 40, 10).run().labels);
      assertEquals(values(10), new Case(160, 18 + 10 * 45, 45, 10).run().labels);
      assertEquals(values(10), new Case(160, 18 + 10 * 49, 49, 10).run().labels);
   }

   /** Cell heights, node counts, y offsets and title heights, box sized as the dashboard. */
   @Test
   void sweep() throws Exception {
      for(int y : new int[] { 153, 160 }) {
         for(int title : new int[] { 18, 30 }) {
            for(int cell : new int[] { 25, 29, 30, 31, 35, 39, 50, 55 }) {
               for(int n : new int[] { 3, 6, 10 }) {
                  Case c = new Case(y, title + n * cell, cell, n);
                  c.titleHeight = title;
                  assertEquals(values(n), c.run().labels,
                               "y=" + y + " title=" + title + " cell=" + cell + " n=" + n);
               }
            }
         }
      }
   }

   /** Merged node regions must not overlap each other or the label rows of later nodes. */
   @Test
   void nodeRegionsDoNotOverlap() throws Exception {
      Result r = new Case(153, 142, 31, 4).run();
      assertEquals(4, r.labelRows.size());
      assertNoOverlap(r);
      // cell 31 gives some node two rows, so a real merged region is recorded and checked
      assertFalse(r.nodeRegions.isEmpty());
   }

   /** Expand Selections: the box is sized by the exporter, all nodes must still be written. */
   @Test
   void expandSelectionsKeepsEveryNode() throws Exception {
      Result r = expandCase(160, 25, 6).run();
      assertEquals(values(6), r.labels);
      assertNoOverlap(r);
   }

   @Test
   void expandSelectionsSweep() throws Exception {
      for(int y : new int[] { 153, 160 }) {
         for(int cell : new int[] { 21, 25, 29, 41, 45, 49, 31, 40, 55 }) {
            for(int n : new int[] { 3, 6, 10 }) {
               Result r = expandCase(y, cell, n).run();
               assertEquals(values(n), r.labels, "y=" + y + " cell=" + cell + " n=" + n);
               assertNoOverlap(r);
            }
         }
      }
   }

   /** A real two-level tree: parents and children in order, nothing overlapping. */
   @Test
   void twoLevelTree() throws Exception {
      Case c = new Case(160, 18 + 9 * 31, 31, 3);
      c.childrenPerParent = 2;
      Result r = c.run();
      assertEquals(List.of("P0", "V0", "V1", "P1", "V2", "V3", "P2", "V4", "V5"), r.labels);
      assertNoOverlap(r);
      // children are indented, parents are not
      assertFalse(r.rawLabels.get(0).startsWith(" "));
      assertTrue(r.rawLabels.get(1).startsWith(" "), "[" + r.rawLabels.get(1) + "]");
   }

   /** A truncated two-level tree gets an un-indented "More..." even after an indented node. */
   @Test
   void twoLevelTruncationMoreIsLevelZero() throws Exception {
      Case c = new Case(160, 18 + 4 * 31, 31, 3);
      c.childrenPerParent = 2;
      Result r = c.run();
      assertEquals("More...", r.rawLabels.get(r.rawLabels.size() - 1));
      assertTrue(r.labels.size() < 9);
      assertNoOverlap(r);
   }

   /** Cells shorter than one grid row still produce one row per node, truncated if needed. */
   @Test
   void cellHeightUnderGridRow() throws Exception {
      Result r = new Case(160, 18 + 6 * 15, 15, 6).run();
      assertEquals("V0", r.labels.get(0));
      assertFalse(r.labels.isEmpty());
      assertNoOverlap(r);
      Result big = new Case(160, 400, 15, 6).run();
      assertEquals(values(6), big.labels);
   }

   @Test
   void zeroNodes() throws Exception {
      Result r = new Case(160, 200, 31, 0).run();
      assertTrue(r.labels.isEmpty());
   }

   /** Exactly one slot for several nodes: the one node is kept, "More..." never replaces it. */
   @Test
   void oneSlotSeveralNodes() throws Exception {
      Result r = new Case(160, 40, 31, 4).run();
      assertEquals(List.of("V0"), r.labels);
      assertNoOverlap(r);
   }

   @Test
   void hiddenTitle() throws Exception {
      Case c = new Case(160, 6 * 31, 31, 6);
      c.titleVisible = false;
      Result r = c.run();
      assertEquals(values(6), r.labels);
      assertNoOverlap(r);
   }

   /** A wrapped node is taller than the others and its neighbours are still all written. */
   @Test
   void wrappedTextNode() throws Exception {
      Case c = new Case(160, 400, 25, 4);
      c.wrapLabel = "V1 wrap " + "long text that wraps ".repeat(12);
      Result r = c.run();
      assertEquals(4, r.labels.size(), r.labels.toString());
      assertEquals("V0", r.labels.get(0));
      assertTrue(r.labels.get(1).startsWith("V1 wrap"));
      assertEquals("V2", r.labels.get(2));
      assertEquals("V3", r.labels.get(3));
      // the wrapped node needs more than one grid row
      assertTrue(r.labelRows.get(2) - r.labelRows.get(1) >= 2, r.labelRows.toString());
      assertNoOverlap(r);
   }

   private static Case expandCase(int y, int cell, int n) {
      Case c = new Case(y, 0, cell, n);
      c.expand = true;
      return c;
   }

   private static void assertNoOverlap(Result r) {
      for(int i = 1; i < r.labelRows.size(); i++) {
         assertTrue(r.labelRows.get(i) > r.labelRows.get(i - 1), r.labelRows.toString());
      }

      // no recorded region covers a label row other than the one it starts on
      for(CellRangeAddress region : r.allRegions) {
         for(int row : r.labelRows) {
            assertTrue(row == region.getFirstRow() || row < region.getFirstRow() ||
                       row > region.getLastRow(), "region " + region + " covers row " + row);
         }
      }

      for(int i = 0; i < r.allRegions.size(); i++) {
         for(int j = i + 1; j < r.allRegions.size(); j++) {
            assertFalse(r.allRegions.get(i).intersects(r.allRegions.get(j)),
                        r.allRegions.get(i) + " intersects " + r.allRegions.get(j));
         }
      }
   }

   /** A neighbour well inside the box bounds the tree, which then gets "More...". */
   @Test
   void deepNeighborIsRespected() throws Exception {
      Case c = new Case(153, 142, 31, 4);
      c.neighborY = 240;
      Result r = c.run();
      assertEquals("More...", r.labels.get(r.labels.size() - 1));
      assertTrue(r.labels.size() < 4);
      assertTrue(r.lastNodeRow < 12, "tree reached row " + r.lastNodeRow);
   }

   /** Shallow overlap of the bottom of the box by the neighbour. */
   @Test
   void shallowNeighborIsRespected() throws Exception {
      Case c = new Case(153, 142, 31, 4);
      c.neighborY = 289;
      Result r = c.run();
      assertTrue(r.lastNodeRow < 14, "tree reached row " + r.lastNodeRow);
      assertEquals("V0", r.labels.get(0));
   }

   /** A neighbour that does not overlap horizontally does not bound the tree. */
   @Test
   void disjointNeighborDoesNotBound() throws Exception {
      Case c = new Case(153, 142, 31, 4);
      c.neighborY = 200;
      c.neighborX = 400;
      assertEquals(values(4), c.run().labels);
   }

   /** Nodes that cannot fit get "More..." instead of vanishing. */
   @Test
   void trulyTruncatedTreeKeepsMore() throws Exception {
      List<String> labels = new Case(160, 142, 31, 10).run().labels;
      assertEquals("More...", labels.get(labels.size() - 1));
      assertTrue(labels.size() < 10);
      assertEquals("V0", labels.get(0));
   }

   private static List<String> values(int n) {
      List<String> list = new ArrayList<>();

      for(int i = 0; i < n; i++) {
         list.add("V" + i);
      }

      return list;
   }

   private static final class Result {
      List<String> labels = new ArrayList<>();
      List<String> rawLabels = new ArrayList<>();
      List<CellRangeAddress> nodeRegions = new ArrayList<>();
      List<CellRangeAddress> allRegions = new ArrayList<>();
      List<Integer> labelRows = new ArrayList<>();
      int lastNodeRow = -1;
   }

   private static final class Case {
      Case(int y, int h, int cellHeight, int nvalues) {
         this.y = y;
         this.h = h;
         this.cellHeight = cellHeight;
         this.nvalues = nvalues;
      }

      Result run() throws Exception {
         Viewsheet vs = new Viewsheet();
         SelectionTreeVSAssembly a = new SelectionTreeVSAssembly(vs, "T");
         SelectionTreeVSAssemblyInfo info = (SelectionTreeVSAssemblyInfo) a.getVSAssemblyInfo();
         vs.addAssembly(a);
         info.setPixelOffset(new Point(0, y));
         info.setPixelSize(new Dimension(344, h));
         info.setCellHeight(cellHeight);
         info.setTitleVisible(titleVisible);
         info.setTitleHeight(titleHeight);
         info.setExpandAll(true);
         SelectionList list = new SelectionList();
         int leafIndex = 0;

         for(int i = 0; i < nvalues; i++) {
            if(childrenPerParent > 0) {
               // a real two-level tree: parents P0.. each with children V0..
               CompositeSelectionValue parent = new CompositeSelectionValue("P" + i, "P" + i);
               parent.setFormat(new VSCompositeFormat());
               parent.setState(SelectionValue.STATE_SELECTED);
               parent.setLevel(0);
               SelectionList kids = new SelectionList();

               for(int k = 0; k < childrenPerParent; k++) {
                  SelectionValue v = new SelectionValue("V" + leafIndex, "V" + leafIndex);
                  leafIndex++;
                  v.setFormat(new VSCompositeFormat());
                  v.setState(SelectionValue.STATE_SELECTED);
                  v.setLevel(1);
                  kids.addSelectionValue(v);
               }

               parent.setSelectionList(kids);
               list.addSelectionValue(parent);
               continue;
            }

            SelectionValue v = new SelectionValue("V" + i, "V" + i);
            VSCompositeFormat vf = new VSCompositeFormat();

            if(wrapLabel != null && i == wrapIndex) {
               v = new SelectionValue(wrapLabel, wrapLabel);
               vf.getUserDefinedFormat().setWrapping(true);
               vf.getUserDefinedFormat().setFont(new java.awt.Font("Dialog", java.awt.Font.PLAIN, 11));
            }

            v.setFormat(vf);
            v.setState(SelectionValue.STATE_SELECTED);
            list.addSelectionValue(v);
         }

         CompositeSelectionValue root = new CompositeSelectionValue();
         root.setSelectionList(list);
         info.setCompositeSelectionValue(root);

         int nextRow = -1;

         if(neighborY >= 0) {
            TextVSAssembly next = new TextVSAssembly(vs, "N");
            next.getVSAssemblyInfo().setPixelOffset(new Point(neighborX, neighborY));
            next.getVSAssemblyInfo().setPixelSize(new Dimension(344, 40));
            vs.addAssembly(next);
            nextRow = neighborY / 20;
         }

         ExcelContext ctx = Mockito.mock(ExcelContext.class);
         Workbook book = new XSSFWorkbook();
         Mockito.when(ctx.getWorkbook()).thenReturn(book);
         PoiExcelVSExporter ex = new PoiExcelVSExporter(ctx, new ByteArrayOutputStream());
         ex.setMatchLayout(!expand);
         ex.setExpandSelections(expand);

         if(expand) {
            // the box AbstractVSExporter.expandSelectionAssembly gives the tree
            double eh = ex.getSelectionHeight(info, info.getRowHeights(), false);
            info.setPixelSize(new Dimension(344, (int) Math.max(h, eh)));
         }

         Sheet sheet = book.createSheet("s");
         ex.sheet = sheet;
         set(ex, "vs", vs);
         set(ex, "viewsheet", vs);

         // the grid prepareSheet builds
         TreeSet<Integer> rowsSet = new TreeSet<>();
         TreeSet<Integer> colsSet = new TreeSet<>();
         List<VSAssembly> cba = new ArrayList<>();
         Method split = PoiExcelVSExporter.class.getDeclaredMethod(
            "split", Viewsheet.class, TreeSet.class, TreeSet.class, List.class);
         split.setAccessible(true);
         split.invoke(ex, vs, rowsSet, colsSet, cba);
         colsSet.add(0);

         for(int i = 0; i <= 40; i++) {
            rowsSet.add(i * 20);
         }

         Method sc = PoiExcelVSExporter.class.getDeclaredMethod(
            "splitCellBasedVSAssembly", VSAssembly.class, TreeSet.class);
         sc.setAccessible(true);

         for(VSAssembly c : cba) {
            sc.invoke(ex, c, colsSet);
         }

         colsSet.add(800);
         Method ic = PoiExcelVSExporter.class.getDeclaredMethod(
            "initColsForExcel", Viewsheet.class, TreeSet.class);
         ic.setAccessible(true);
         ic.invoke(ex, vs, colsSet);
         set(ex, "cols", colsSet.toArray(new Integer[0]));
         int[] rows = rowsSet.stream().mapToInt(Integer::intValue).toArray();
         set(ex, "rows", rows);

         for(int i = 0; i < rows.length - 1; i++) {
            sheet.createRow(i).setHeight((short) ((rows[i + 1] - rows[i]) * 15));
         }

         CellRangeAddress neighbor = null;

         if(nextRow >= 0) {
            // a neighbour that is horizontally apart also has to be apart in the grid
            neighbor = neighborX == 0 ? new CellRangeAddress(nextRow, nextRow + 1, 0, 3) :
               new CellRangeAddress(nextRow, nextRow + 1, 6, 7);
            sheet.addMergedRegion(neighbor);
         }

         ExcelSelectionTreeHelper helper = new ExcelSelectionTreeHelper(book);
         helper.setExporter(ex);
         Result res = new Result();
         helper.write(sheet, a);

         for(Row row : sheet) {
            for(Cell cl : row) {
               if(cl.getCellType() == CellType.STRING &&
                  (cl.getStringCellValue().trim().startsWith("V") ||
                   cl.getStringCellValue().trim().startsWith("P") ||
                   cl.getStringCellValue().equals("More...")))
               {
                  res.labels.add(cl.getStringCellValue().trim());
                  res.rawLabels.add(cl.getStringCellValue());
                  res.labelRows.add(cl.getRowIndex());
                  res.lastNodeRow = Math.max(res.lastNodeRow, cl.getRowIndex());
               }
            }
         }

         for(CellRangeAddress r : sheet.getMergedRegions()) {
            // the title merge starts on the box's first row
            if((neighbor == null || !r.equals(neighbor)) && r.getFirstRow() > y / 20) {
               res.nodeRegions.add(r);
               res.lastNodeRow = Math.max(res.lastNodeRow, r.getLastRow());
            }
         }

         sheet.validateMergedRegions(); // throws when two merged regions intersect
         res.allRegions.addAll(sheet.getMergedRegions());
         res.nodeRegions.sort((r1, r2) -> Integer.compare(r1.getFirstRow(), r2.getFirstRow()));
         return res;
      }

      private static void set(Object o, String f, Object v) throws Exception {
         Class<?> c = o.getClass();

         while(c != null) {
            try {
               Field fd = c.getDeclaredField(f);
               fd.setAccessible(true);
               fd.set(o, v);
               return;
            }
            catch(NoSuchFieldException e) {
               c = c.getSuperclass();
            }
         }

         throw new NoSuchFieldException(f);
      }

      final int y;
      final int h;
      final int cellHeight;
      final int nvalues;
      int titleHeight = 18;
      boolean titleVisible = true;
      boolean expand = false;
      int childrenPerParent = 0;
      String wrapLabel;
      int wrapIndex = 1;
      int neighborY = -1;
      int neighborX = 0;
   }
}
