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
import inetsoft.uql.viewsheet.internal.SelectionListVSAssemblyInfo;
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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #79603: Excel export (Match Layout) of a selection list dropped values the dashboard shows
 * and wrote "More...". Rows were costed per row rounded to 20px and compared with a budget
 * from the box height. Rows are now mapped cumulatively onto the grid rows the box owns, bounded
 * by the first row of any assembly below the list. The tests run the real exporter grid and the
 * real list writer, and read cells and merged regions back.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ExcelSelectionListFitTest {
   /** Census Region: y=153, h=88, cell 31, 2 columns, 4 values. */
   @Test
   void censusOffGrid() throws Exception {
      assertEquals(values(4), new Case(153, 88, 31, 2, 4).run().labels);
   }

   /** On-grid and off-grid boxes whose grid extent is the 80px that used to give "More...". */
   @Test
   void onGridAndOffGridBoxes() throws Exception {
      assertEquals(values(4), new Case(160, 88, 31, 2, 4).run().labels);
      assertEquals(values(4), new Case(140, 88, 31, 2, 4).run().labels);
      assertEquals(values(4), new Case(140, 100, 31, 2, 4).run().labels);
   }

   /** Three rows of cell height 30/31 (rounded up to 40px each before) must all be written. */
   @Test
   void threeRowsOfRoundedUpCells() throws Exception {
      assertEquals(values(3), new Case(140, 120, 30, 1, 3).run().labels);
      assertEquals(values(3), new Case(140, 120, 31, 1, 3).run().labels);
      assertEquals(values(3), new Case(140, 111, 30, 1, 3).run().labels);
   }

   @Test
   void tallTitle() throws Exception {
      Case c = new Case(140, 130, 31, 1, 3);
      c.titleHeight = 30;
      assertEquals(values(3), c.run().labels);
   }

   @Test
   void hiddenTitle() throws Exception {
      Case c = new Case(140, 96, 31, 1, 3);
      c.titleVisible = false;
      assertEquals(values(3), c.run().labels);
   }

   @Test
   void threeColumns() throws Exception {
      assertEquals(values(6), new Case(140, 88, 31, 3, 6).run().labels);
   }

   /** The list in a selection container: the container stacks it on the grid. */
   @Test
   void inContainer() throws Exception {
      Case c = new Case(140, 80, 31, 1, 2);
      c.inContainer = true;
      assertEquals(values(2), c.run().labels);
   }

   /**
    * Another assembly starts well inside the box (y=200, grid row 10). The list must stop above
    * it, throw nothing and leave the neighbour's rows alone.
    */
   @Test
   void deepNeighborOverlapIsRespected() throws Exception {
      Case c = new Case(153, 88, 31, 2, 4);
      c.neighborY = 200;
      Result r = c.run();
      assertEquals(List.of("V0", "More..."), r.labels);
      assertTrue(r.lastListRow < 10, "list reached row " + r.lastListRow);
   }

   /** Shallow overlap (the #7677 regression): bottom 1..19px covered by the next assembly. */
   @Test
   void shallowNeighborOverlapDoesNotThrow() throws Exception {
      Case c = new Case(153, 88, 31, 2, 4);
      c.neighborY = 229;
      Result r = c.run();
      assertTrue(r.lastListRow < 11, "list reached row " + r.lastListRow);
      assertEquals("V0", r.labels.get(0));
   }

   /** A neighbour that does not overlap the list horizontally must not bound it. */
   @Test
   void horizontallyDisjointNeighborDoesNotBound() throws Exception {
      Case c = new Case(153, 88, 31, 2, 4);
      c.neighborY = 200;
      c.neighborX = 400;
      assertEquals(values(4), c.run().labels);
   }

   /** Cells under 20px cannot be given a grid row each: "More..." is legitimate. */
   @Test
   void cellsUnderOneGridRowKeepMore() throws Exception {
      List<String> labels = new Case(140, 198, 18, 1, 10).run().labels;
      assertEquals("More...", labels.get(labels.size() - 1));
      assertTrue(labels.size() < 10);
   }

   /** More values than the box can hold keeps "More...". */
   @Test
   void trulyTruncatedListKeepsMore() throws Exception {
      List<String> labels = new Case(250, 176, 29, 2, 50).run().labels;
      assertEquals("More...", labels.get(labels.size() - 1));
      assertTrue(labels.size() < 50);
   }

   /** When a single slot fits, the value is written, not replaced by "More...". */
   @Test
   void singleSlotKeepsItsValue() throws Exception {
      assertEquals(List.of("V0"), new Case(140, 60, 31, 1, 2).run().labels);
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
      int lastListRow = -1;
   }

   private static final class Case {
      Case(int y, int h, int cellHeight, int columns, int nvalues) {
         this.y = y;
         this.h = h;
         this.cellHeight = cellHeight;
         this.columns = columns;
         this.nvalues = nvalues;
      }

      Result run() throws Exception {
         Viewsheet vs = new Viewsheet();
         SelectionListVSAssembly a = new SelectionListVSAssembly(vs, "L");
         SelectionListVSAssemblyInfo info = (SelectionListVSAssemblyInfo) a.getVSAssemblyInfo();
         vs.addAssembly(a);
         info.setPixelOffset(new Point(0, y));
         info.setPixelSize(new Dimension(344, h));
         info.setColumnCount(columns);
         info.setCellHeight(cellHeight);
         info.setTitleVisible(titleVisible);
         info.setTitleHeight(titleHeight);
         SelectionList list = new SelectionList();
         List<SelectionValue> values = new ArrayList<>();

         for(int i = 0; i < nvalues; i++) {
            SelectionValue v = new SelectionValue("V" + i, "V" + i);
            v.setFormat(new VSCompositeFormat());
            list.addSelectionValue(v);
            values.add(v);
         }

         info.setSelectionList(list);

         if(inContainer) {
            CurrentSelectionVSAssembly container = new CurrentSelectionVSAssembly(vs, "C");
            container.getVSAssemblyInfo().setPixelOffset(new Point(0, 100));
            container.getVSAssemblyInfo().setPixelSize(new Dimension(344, 40 + h));
            vs.addAssembly(container);
            container.setAssemblies(new String[] { "L" });
         }

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

         // the neighbour's own cells, written before the list as an earlier assembly would
         CellRangeAddress neighbor = null;

         if(nextRow >= 0) {
            neighbor = new CellRangeAddress(nextRow, nextRow + 1, 0, 3);
            sheet.addMergedRegion(neighbor);
         }

         ExcelSelectionListHelper helper = new ExcelSelectionListHelper(book);
         helper.setExporter(ex);
         Method m = ExcelSelectionListHelper.class.getDeclaredMethod(
            "writeList", SelectionListVSAssembly.class, Sheet.class, List.class);
         m.setAccessible(true);
         Result res = new Result();

         try {
            m.invoke(helper, a, sheet, values);
         }
         catch(InvocationTargetException e) {
            throw new AssertionError("writeList failed: " + e.getCause(), e.getCause());
         }

         for(Row row : sheet) {
            for(Cell cl : row) {
               if(cl.getCellType() == CellType.STRING && !cl.getStringCellValue().isEmpty()) {
                  res.labels.add(cl.getStringCellValue());
               }
            }
         }

         for(CellRangeAddress r : sheet.getMergedRegions()) {
            if(neighbor == null || !r.equals(neighbor)) {
               res.lastListRow = Math.max(res.lastListRow, r.getLastRow());
            }
         }

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
      final int columns;
      final int nvalues;
      int titleHeight = 18;
      boolean titleVisible = true;
      boolean inContainer;
      int neighborY = -1;
      int neighborX = 0;
   }
}
